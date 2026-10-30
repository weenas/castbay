//! H.264 encoding. The platform encoder sits behind its own module; picture conversion is
//! shared.

use std::fmt;

pub mod ffmpeg;
pub mod scale;

#[cfg(feature = "ffmpeg-lib")]
pub mod inproc;

#[cfg(windows)]
pub mod windows;

/// The video format of a cast: what the encoder makes and the receiver decodes.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum VideoFormat {
    #[default]
    H264,
    H265,
}

impl VideoFormat {
    /// The name the protocol and the receiver use (`offer.codec`).
    pub fn protocol_name(self) -> &'static str {
        match self {
            VideoFormat::H264 => "h264",
            VideoFormat::H265 => "h265",
        }
    }

    pub fn from_protocol_name(name: &str) -> Option<VideoFormat> {
        match name {
            "h264" => Some(VideoFormat::H264),
            "h265" => Some(VideoFormat::H265),
            _ => None,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Config {
    /// Both must be even.
    pub width: u32,
    pub height: u32,
    pub fps: u32,
    pub bitrate_bps: u32,
    /// Frames between key frames.
    pub gop: u32,
    /// H.264 unless the receiver and the encoder both do H.265.
    pub format: VideoFormat,
}

/// One encoded access unit in Annex B format; a key frame carries SPS and PPS in front.
pub struct Encoded {
    pub data: Vec<u8>,
    pub key: bool,
    /// The presentation time given with the input picture, in microseconds.
    pub pts_us: i64,
}

#[derive(Debug)]
pub struct Error(pub String);

impl fmt::Display for Error {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "encoder: {}", self.0)
    }
}

impl std::error::Error for Error {}

/// Converts BGRA (rows `stride` bytes apart) to NV12, BT.709 limited range, into `out`
/// (`width * height * 3 / 2` bytes). Splits the work over a few threads.
pub fn bgra_to_nv12(src: &[u8], stride: usize, width: usize, height: usize, out: &mut [u8]) {
    assert!(width % 2 == 0 && height % 2 == 0 && out.len() >= width * height * 3 / 2);
    let (luma, chroma) = out.split_at_mut(width * height);
    let threads = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(2).min(8);
    let pairs = height / 2;
    let per = pairs.div_ceil(threads);
    std::thread::scope(|scope| {
        let mut luma_rest = luma;
        let mut chroma_rest = chroma;
        let mut first = 0;
        while first < pairs {
            let count = per.min(pairs - first);
            let (l, lr) = luma_rest.split_at_mut(count * 2 * width);
            let (c, cr) = chroma_rest.split_at_mut(count * width);
            luma_rest = lr;
            chroma_rest = cr;
            let row0 = first * 2;
            scope.spawn(move || {
                for pair in 0..count {
                    let top = &src[(row0 + pair * 2) * stride..][..width * 4];
                    let bottom = &src[(row0 + pair * 2 + 1) * stride..][..width * 4];
                    let (ly0, ly1) = l[pair * 2 * width..][..2 * width].split_at_mut(width);
                    let cuv = &mut c[pair * width..][..width];
                    for x in (0..width).step_by(2) {
                        let mut r = 0i32;
                        let mut g = 0i32;
                        let mut b = 0i32;
                        for (row, ly) in [(top, &mut *ly0), (bottom, &mut *ly1)] {
                            for dx in 0..2 {
                                let p = &row[(x + dx) * 4..][..4];
                                let (pb, pg, pr) = (p[0] as i32, p[1] as i32, p[2] as i32);
                                ly[x + dx] = (((47 * pr + 157 * pg + 16 * pb + 128) >> 8) + 16) as u8;
                                r += pr;
                                g += pg;
                                b += pb;
                            }
                        }
                        let (r, g, b) = ((r + 2) >> 2, (g + 2) >> 2, (b + 2) >> 2);
                        cuv[x] = ((((-26 * r - 87 * g + 112 * b + 128) >> 8) + 128).clamp(0, 255)) as u8;
                        cuv[x + 1] = ((((112 * r - 102 * g - 10 * b + 128) >> 8) + 128).clamp(0, 255)) as u8;
                    }
                }
            });
            first += count;
        }
    });
}

/// The NAL unit types of an H.265 Annex B buffer (VPS 32, SPS 33, PPS 34; IDR 19 and 20, CRA 21 are
/// the pictures one can start from).
pub fn nal_types_h265(data: &[u8]) -> Vec<u8> {
    let mut types = Vec::new();
    let mut i = 0;
    while i + 3 < data.len() {
        if data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1 {
            types.push((data[i + 3] >> 1) & 0x3F);
            i += 3;
        } else {
            i += 1;
        }
    }
    types
}

/// Splits an Annex B buffer into its NAL units' types, for finding key frames and parameter sets.
pub fn nal_types(data: &[u8]) -> Vec<u8> {
    let mut types = Vec::new();
    let mut i = 0;
    while i + 3 < data.len() {
        if data[i] == 0 && data[i + 1] == 0 && data[i + 2] == 1 {
            types.push(data[i + 3] & 0x1F);
            i += 3;
        } else {
            i += 1;
        }
    }
    types
}
