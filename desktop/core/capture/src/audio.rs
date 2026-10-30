//! System audio: what the default output device is playing (WASAPI loopback), as 48 kHz stereo
//! 32-bit float samples, interleaved. The platform converts whatever the device uses.

use std::time::Duration;

use windows::core::{implement, Interface};
use windows::Win32::Media::Audio::{
    eConsole, eRender, ActivateAudioInterfaceAsync, IActivateAudioInterfaceAsyncOperation, IActivateAudioInterfaceCompletionHandler,
    IActivateAudioInterfaceCompletionHandler_Impl, AUDIOCLIENT_ACTIVATION_PARAMS, AUDIOCLIENT_ACTIVATION_PARAMS_0,
    AUDIOCLIENT_ACTIVATION_TYPE_PROCESS_LOOPBACK, AUDIOCLIENT_PROCESS_LOOPBACK_PARAMS, PROCESS_LOOPBACK_MODE_INCLUDE_TARGET_PROCESS_TREE,
    VIRTUAL_AUDIO_DEVICE_PROCESS_LOOPBACK, IAudioCaptureClient, IAudioClient, IMMDeviceEnumerator, MMDeviceEnumerator, AUDCLNT_BUFFERFLAGS_SILENT,
    AUDCLNT_SHAREMODE_SHARED, AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM, AUDCLNT_STREAMFLAGS_LOOPBACK, AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
    WAVEFORMATEX,
};
use windows::Win32::System::Com::StructuredStorage::{PROPVARIANT, PROPVARIANT_0, PROPVARIANT_0_0, PROPVARIANT_0_0_0};
use windows::Win32::System::Com::{BLOB, CoCreateInstance, CoInitializeEx, CoUninitialize, CLSCTX_ALL, COINIT_MULTITHREADED};
use windows::Win32::System::Variant::VT_BLOB;

use crate::Error;

pub const SAMPLE_RATE: u32 = 48_000;
pub const CHANNELS: u32 = 2;

fn platform(e: windows::core::Error) -> Error {
    Error::Platform(e.to_string())
}

/// Delivers the audio the default output plays to `on_samples` (interleaved stereo floats, in
/// bursts of a few milliseconds) until `stop` is set. Silence is delivered as zeros while
/// something is playing; while nothing plays the device delivers nothing at all. Returns an
/// error when the device goes away (the caller starts it again).
///
/// With `process` (a process id) only what that process and the ones it started play is
/// delivered (a window's own sound, a browser's included); where Windows cannot do that (before
/// Windows 10 version 2004) the whole output is delivered, as without it.
pub fn run_loopback(process: Option<u32>, stop: &dyn Fn() -> bool, mut on_samples: impl FnMut(&[f32])) -> Result<(), Error> {
    unsafe {
        let initialised = CoInitializeEx(None, COINIT_MULTITHREADED).is_ok();
        let result = capture(process, stop, &mut on_samples);
        if initialised {
            CoUninitialize();
        }
        result
    }
}

/// Tells the thread waiting in [process_client] that the activation is over.
#[implement(IActivateAudioInterfaceCompletionHandler)]
struct Done(std::sync::mpsc::Sender<()>);

impl IActivateAudioInterfaceCompletionHandler_Impl for Done_Impl {
    fn ActivateCompleted(&self, _operation: windows::core::Ref<'_, IActivateAudioInterfaceAsyncOperation>) -> windows::core::Result<()> {
        let _ = self.0.send(());
        Ok(())
    }
}

/// An audio client that hears one process (and the ones it started) instead of a device.
unsafe fn process_client(process: u32) -> Result<IAudioClient, Error> {
    let mut params = AUDIOCLIENT_ACTIVATION_PARAMS {
        ActivationType: AUDIOCLIENT_ACTIVATION_TYPE_PROCESS_LOOPBACK,
        Anonymous: AUDIOCLIENT_ACTIVATION_PARAMS_0 {
            ProcessLoopbackParams: AUDIOCLIENT_PROCESS_LOOPBACK_PARAMS {
                TargetProcessId: process,
                ProcessLoopbackMode: PROCESS_LOOPBACK_MODE_INCLUDE_TARGET_PROCESS_TREE,
            },
        },
    };
    // Never dropped: dropping a PROPVARIANT frees its blob, and this one lives on the stack.
    let variant = std::mem::ManuallyDrop::new(PROPVARIANT {
        Anonymous: PROPVARIANT_0 {
            Anonymous: std::mem::ManuallyDrop::new(PROPVARIANT_0_0 {
                vt: VT_BLOB,
                wReserved1: 0,
                wReserved2: 0,
                wReserved3: 0,
                Anonymous: PROPVARIANT_0_0_0 {
                    blob: BLOB { cbSize: std::mem::size_of::<AUDIOCLIENT_ACTIVATION_PARAMS>() as u32, pBlobData: &mut params as *mut _ as *mut u8 },
                },
            }),
        },
    });
    let (tx, rx) = std::sync::mpsc::channel();
    let handler: IActivateAudioInterfaceCompletionHandler = Done(tx).into();
    let operation = ActivateAudioInterfaceAsync(VIRTUAL_AUDIO_DEVICE_PROCESS_LOOPBACK, &IAudioClient::IID, Some(&*variant), &handler).map_err(platform)?;
    rx.recv_timeout(Duration::from_secs(5)).map_err(|_| Error::Platform("the audio of the window did not answer".into()))?;
    let (mut result, mut client) = (windows::core::HRESULT(0), None);
    operation.GetActivateResult(&mut result, &mut client).map_err(platform)?;
    result.ok().map_err(platform)?;
    client.ok_or_else(|| Error::Platform("no audio client".into()))?.cast::<IAudioClient>().map_err(platform)
}

unsafe fn capture(process: Option<u32>, stop: &dyn Fn() -> bool, on_samples: &mut dyn FnMut(&[f32])) -> Result<(), Error> {
    let client: IAudioClient = match process.map(|p| process_client(p)) {
        Some(Ok(client)) => client,
        // Not possible here: the sound of the whole output, as before.
        _ => {
            let enumerator: IMMDeviceEnumerator = CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL).map_err(platform)?;
            let device = enumerator.GetDefaultAudioEndpoint(eRender, eConsole).map_err(platform)?;
            device.Activate(CLSCTX_ALL, None).map_err(platform)?
        }
    };
    let format = WAVEFORMATEX {
        wFormatTag: 3, // WAVE_FORMAT_IEEE_FLOAT
        nChannels: CHANNELS as u16,
        nSamplesPerSec: SAMPLE_RATE,
        nAvgBytesPerSec: SAMPLE_RATE * CHANNELS * 4,
        nBlockAlign: (CHANNELS * 4) as u16,
        wBitsPerSample: 32,
        cbSize: 0,
    };
    client
        .Initialize(
            AUDCLNT_SHAREMODE_SHARED,
            AUDCLNT_STREAMFLAGS_LOOPBACK | AUDCLNT_STREAMFLAGS_AUTOCONVERTPCM | AUDCLNT_STREAMFLAGS_SRC_DEFAULT_QUALITY,
            2_000_000, // 200 ms
            0,
            &format,
            None,
        )
        .map_err(platform)?;
    let capture: IAudioCaptureClient = client.GetService().map_err(platform)?;
    client.Start().map_err(platform)?;

    let mut silence: Vec<f32> = Vec::new();
    let result = (|| -> Result<(), Error> {
        while !stop() {
            std::thread::sleep(Duration::from_millis(5));
            while capture.GetNextPacketSize().map_err(platform)? > 0 {
                let (mut data, mut frames, mut flags) = (std::ptr::null_mut::<u8>(), 0u32, 0u32);
                capture.GetBuffer(&mut data, &mut frames, &mut flags, None, None).map_err(platform)?;
                let samples = frames as usize * CHANNELS as usize;
                if flags & (AUDCLNT_BUFFERFLAGS_SILENT.0 as u32) != 0 || data.is_null() {
                    silence.clear();
                    silence.resize(samples, 0.0);
                    on_samples(&silence);
                } else {
                    on_samples(std::slice::from_raw_parts(data as *const f32, samples));
                }
                capture.ReleaseBuffer(frames).map_err(platform)?;
            }
        }
        Ok(())
    })();
    let _ = client.Stop();
    result
}
