use std::{
    io::{Read, Write},
    net::{TcpListener, TcpStream},
    path::PathBuf,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
        mpsc::{Sender, channel},
    },
    time::Duration,
};

use castbay_sender::{Codec, Options, Status, Stopper, Switcher, Target};
#[path = "autostart.rs"]
mod autostart;
use gpui::{
    App, Bounds, ClickEvent, Context, FocusHandle, Focusable, FontWeight, SharedString, Window,
    WindowBounds, WindowOptions, div, prelude::*, px, rgb, size,
};
use gpui_platform::application;

#[path = "../../app/src/crash.rs"]
mod crash;
#[path = "../../app/src/diagnostics.rs"]
mod diagnostics;
#[path = "../../app/src/settings.rs"]
mod settings;
#[path = "../../app/src/update.rs"]
mod update;
#[path = "../../app/src/usage.rs"]
mod usage;

use settings::Settings;

static ACTIVATE_REQUESTED: AtomicBool = AtomicBool::new(false);
static TRAY_ACTION: Mutex<Option<String>> = Mutex::new(None);

#[derive(Clone)]
struct Receiver {
    id: String,
    name: String,
    ip: String,
    port: u16,
    pair: String,
    password: bool,
    model: Option<String>,
    app_version: Option<String>,
}

#[derive(Default)]
struct Sources {
    monitors: Vec<String>,
    windows: Vec<String>,
    system_picker: bool,
}

#[derive(Clone, Copy, PartialEq)]
enum Page {
    Home,
    Settings,
    Paired,
    Diagnostics,
    About,
}

#[derive(Clone, Copy, PartialEq)]
enum InputMode {
    None,
    Password,
    PairCode,
}

#[derive(Default)]
struct Runtime {
    stopper: Option<Stopper>,
    switcher: Option<Switcher>,
    code: Option<Sender<Option<String>>>,
}

struct CastBay {
    dir: PathBuf,
    window_handle: Option<gpui::AnyWindowHandle>,
    settings: Settings,
    receivers: Vec<Receiver>,
    sources: Sources,
    selected_receiver: usize,
    selected_source: usize,
    page: Page,
    status: Arc<Mutex<String>>,
    status_text: String,
    error: String,
    runtime: Arc<Mutex<Runtime>>,
    input_mode: InputMode,
    input: String,
    focus: FocusHandle,
    paired: Vec<(String, String, u64)>,
    logs: Vec<String>,
    update_message: String,
    autostart: bool,
    pending_crash: Option<crash::Pending>,
    report_confirm: bool,
    reset_confirm: bool,
}

impl CastBay {
    fn new(cx: &mut Context<Self>) -> Self {
        let dir = data_dir();
        let _ = std::fs::create_dir_all(&dir);
        crash::install_hook(dir.clone());
        let settings = Settings::load(&dir);
        let pending = crash::take(&dir);
        let status = Arc::new(Mutex::new(String::from("Ready")));
        let runtime = Arc::new(Mutex::new(Runtime::default()));
        let mut state = Self {
            dir: dir.clone(),
            window_handle: None,
            settings,
            receivers: vec![],
            sources: Sources::default(),
            selected_receiver: 0,
            selected_source: 0,
            page: Page::Home,
            status: status.clone(),
            status_text: if pending.is_some() {
                "Previous run ended unexpectedly".into()
            } else {
                "Ready".into()
            },
            error: String::new(),
            runtime: runtime.clone(),
            input_mode: InputMode::None,
            input: String::new(),
            focus: cx.focus_handle(),
            paired: vec![],
            logs: vec![],
            update_message: String::new(),
            autostart: autostart::get(),
            pending_crash: pending,
            report_confirm: false,
            reset_confirm: false,
        };

        state.load_sources();
        log_line(&dir, "application started");
        if state.settings.usage_stats {
            let _ = usage::turn_on(&dir);
        }
        if state.settings.check_updates {
            cx.spawn(async move |this, cx| {
                let result = cx
                    .background_executor()
                    .spawn(async move { latest_version() })
                    .await;
                let _ = this.update(cx, |app, cx| {
                    match result {
                        Ok(Some(v)) if v != env!("CARGO_PKG_VERSION") => {
                            app.update_message = format!("Version {v} is available")
                        }
                        Ok(_) => app.update_message = "You are using the latest version".into(),
                        Err(e) => app.update_message = format!("Update check failed: {e}"),
                    }
                    cx.notify();
                });
            })
            .detach();
        }
        cx.spawn(async move |this, cx| {
            let found = cx
                .background_executor()
                .spawn(async move { castbay_link::browse(Duration::from_secs(3)) })
                .await;
            let _ = this.update(cx, |app, cx| {
                match found {
                    Ok(list) => {
                        app.receivers = list
                            .into_iter()
                            .filter_map(|r| {
                                let ip = r
                                    .addresses
                                    .iter()
                                    .find(|a| a.is_ipv4())
                                    .or(r.addresses.first())?;
                                Some(Receiver {
                                    id: r.id,
                                    name: r.name,
                                    ip: ip.to_string(),
                                    port: r.port,
                                    pair: format!("{:?}", r.pair).to_lowercase(),
                                    password: r.password,
                                    model: r.model,
                                    app_version: r.app_version,
                                })
                            })
                            .collect();
                        app.receivers.sort_by(|a, b| a.name.cmp(&b.name));
                        app.selected_receiver = app
                            .receivers
                            .iter()
                            .position(|r| r.id == app.settings.last_receiver)
                            .unwrap_or(0);
                        app.error.clear();
                    }
                    Err(e) => app.error = e.to_string(),
                }
                cx.notify();
            });
        })
        .detach();

        let poll_status = status;
        cx.spawn(async move |this, cx| {
            loop {
                cx.background_executor()
                    .timer(Duration::from_millis(400))
                    .await;
                let value = poll_status.lock().map(|s| s.clone()).unwrap_or_default();
                let mut activate = ACTIVATE_REQUESTED.swap(false, Ordering::SeqCst);
                let action = TRAY_ACTION.lock().ok().and_then(|mut action| action.take());
                if let Some(action) = action {
                    match action.as_str() {
                        "settings" => {
                            let _ = this.update(cx, |app, cx| {
                                app.page = Page::Settings;
                                cx.notify();
                            });
                            activate = true;
                        }
                        "about" => {
                            let _ = this.update(cx, |app, cx| {
                                app.page = Page::About;
                                cx.notify();
                            });
                            activate = true;
                        }
                        "stop" => {
                            if let Ok(app) = this.read_with(cx, |app, _| app.runtime.clone()) {
                                if let Ok(rt) = app.lock() {
                                    if let Some(stop) = rt.stopper.as_ref() {
                                        stop.stop();
                                    }
                                }
                            }
                        }
                        "quit" => {
                            if let Ok(app) = this.read_with(cx, |app, _| app.runtime.clone()) {
                                if let Ok(rt) = app.lock() {
                                    if let Some(stop) = rt.stopper.as_ref() {
                                        stop.stop();
                                    }
                                }
                            }
                            let _ = cx.update(|app| app.quit());
                            break;
                        }
                        _ => activate = true,
                    }
                }
                if activate {
                    let handle = this
                        .read_with(cx, |app, _| app.window_handle)
                        .ok()
                        .flatten();
                    let _ = cx.update(|app| app.activate(true));
                    if let Some(handle) = handle {
                        let _ = handle.update(cx, |_, window, _| window.activate_window());
                    }
                }
                if this
                    .update(cx, |app, cx| {
                        if app.status_text != value {
                            app.status_text = value;
                            cx.notify();
                        }
                    })
                    .is_err()
                {
                    break;
                }
            }
        })
        .detach();
        if state.settings.error_reports {
            if let Some(pending) = state.pending_crash.clone() {
                let report_dir = dir.clone();
                let report_settings = state.settings.clone();
                cx.spawn(async move |this, cx| {
                    let result = cx
                        .background_executor()
                        .spawn(async move {
                            let text = report_text(
                                &report_dir,
                                &report_settings,
                                "crash",
                                Some(pending.text),
                            );
                            diagnostics::upload(
                                &text,
                                env!("CARGO_PKG_VERSION"),
                                "crash",
                                &report_dir.join("report-cache"),
                            )
                            .map_err(|e| e.text())
                        })
                        .await;
                    let _ = this.update(cx, |app, cx| {
                        match result {
                            Ok(id) => {
                                app.pending_crash = None;
                                app.status_text = format!("Crash report uploaded: {id}");
                            }
                            Err(e) => app.error = format!("Crash report failed: {e}"),
                        }
                        cx.notify();
                    });
                })
                .detach();
            }
        }
        let settings_for_stats = state.settings.clone();
        let stats_dir = dir.clone();
        std::thread::spawn(move || {
            send_usage_once(&stats_dir, &settings_for_stats);
            loop {
                std::thread::sleep(Duration::from_secs(6 * 3600));
                send_usage_once(&stats_dir, &settings_for_stats);
            }
        });
        state
    }

    fn refresh(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        self.status_text = "Searching for receivers…".into();
        self.error.clear();
        cx.notify();
        cx.spawn(async move |this, cx| {
            let result = cx
                .background_executor()
                .spawn(async move { castbay_link::browse(Duration::from_secs(3)) })
                .await;
            let _ = this.update(cx, |app, cx| {
                match result {
                    Ok(list) => {
                        app.receivers = list
                            .into_iter()
                            .filter_map(|r| {
                                let ip = r
                                    .addresses
                                    .iter()
                                    .find(|a| a.is_ipv4())
                                    .or(r.addresses.first())?;
                                Some(Receiver {
                                    id: r.id,
                                    name: r.name,
                                    ip: ip.to_string(),
                                    port: r.port,
                                    pair: format!("{:?}", r.pair).to_lowercase(),
                                    password: r.password,
                                    model: r.model,
                                    app_version: r.app_version,
                                })
                            })
                            .collect();
                        app.receivers.sort_by(|a, b| a.name.cmp(&b.name));
                        app.status_text = format!("{} receiver(s) found", app.receivers.len());
                        app.selected_receiver = app
                            .receivers
                            .iter()
                            .position(|r| r.id == app.settings.last_receiver)
                            .unwrap_or(0);
                    }
                    Err(e) => {
                        app.error = e.to_string();
                        app.status_text = "Search failed".into();
                    }
                }
                cx.notify();
            });
        })
        .detach();
    }

    fn start(&mut self, _: &ClickEvent, window: &mut Window, cx: &mut Context<Self>) {
        let Some(receiver) = self.receivers.get(self.selected_receiver).cloned() else {
            self.error =
                "No receiver selected. Refresh and make sure CastBay is open on the TV.".into();
            cx.notify();
            return;
        };
        if receiver.password && self.input.is_empty() {
            self.input_mode = InputMode::Password;
            window.focus(&self.focus, cx);
            self.error = "Enter the receiver password, then press Enter.".into();
            cx.notify();
            return;
        }
        if self.runtime.lock().unwrap().stopper.is_some() {
            return;
        }
        let target = if self.sources.system_picker {
            Target::Monitor(0)
        } else if self.settings.source_kind == "window" {
            Target::Window(
                self.sources
                    .windows
                    .get(self.selected_source)
                    .cloned()
                    .unwrap_or_default(),
            )
        } else {
            Target::Monitor(self.selected_source)
        };
        let (fps, bitrate_bps) = self.settings.quality_params();
        let codec = if self.settings.encoder == "auto" {
            None
        } else {
            Codec::parse(&self.settings.encoder)
        };
        let ip: std::net::IpAddr = match receiver.ip.parse() {
            Ok(ip) => ip,
            Err(e) => {
                self.error = e.to_string();
                cx.notify();
                return;
            }
        };
        let (code_tx, code_rx) = channel::<Option<String>>();
        let status = self.status.clone();
        let dir = self.dir.clone();
        let options = Options {
            addr: std::net::SocketAddr::new(ip, receiver.port),
            receiver_id: Some(receiver.id.clone()),
            name: std::env::var("COMPUTERNAME")
                .or_else(|_| std::env::var("HOSTNAME"))
                .unwrap_or_else(|_| "Computer".into()),
            password: if receiver.password {
                Some(std::mem::take(&mut self.input))
            } else {
                None
            },
            target,
            fps,
            bitrate_bps,
            codec,
            video_format: castbay_sender::FormatChoice::parse(&self.settings.video_format)
                .unwrap_or_default(),
            audio: self.settings.send_audio,
            max_size: (1920, 1080),
            data_dir: dir.clone(),
            max_duration: None,
        };
        let code = Box::new(move |_expected: &str| {
            code_rx
                .recv_timeout(Duration::from_secs(180))
                .ok()
                .flatten()
        });
        let on_status = Arc::new(move |s: Status| {
            let text = match &s {
                Status::Connecting => "Connecting…".into(),
                Status::NeedsCode => "Enter the pairing code shown on the TV".into(),
                Status::Casting {
                    receiver,
                    width,
                    height,
                    format,
                } => format!("Casting to {receiver} · {width}×{height} · {format}"),
                Status::Reconnecting(reason) => format!("Reconnecting: {reason}"),
                Status::Reconnected => "Reconnected".into(),
                Status::Receiver {
                    decode_ms,
                    dropped,
                    fps,
                } => format!("Receiver: {fps:.1} fps · {decode_ms} ms decode · {dropped} dropped"),
                Status::Bitrate(bps) => format!("Bitrate: {} Mbit/s", bps / 1_000_000),
                Status::Still(true) => "Screen is idle; capture rate reduced".into(),
                Status::Still(false) => "Screen activity resumed".into(),
                Status::SourceLost => "Shared source closed; choose another source".into(),
                Status::Switched => "Source switched".into(),
                Status::SwitchFailed(reason) => format!("Could not switch source: {reason}"),
                Status::ScreenLocked(true) => "Computer locked".into(),
                Status::ScreenLocked(false) => "Computer unlocked".into(),
                Status::Ended(reason) => format!("Casting ended: {reason}"),
            };
            if let Ok(mut value) = status.lock() {
                *value = text;
            }
        });
        let started = std::time::Instant::now();
        let day = usage::day_of(now_secs());
        if self.settings.usage_stats {
            let key = if self.settings.source_kind == "window" {
                "cast.window"
            } else {
                "cast.screen"
            };
            usage::count(&dir, &day, key);
            usage::count(
                &dir,
                &day,
                if self.settings.fps == 60 {
                    "fps.60"
                } else {
                    "fps.30"
                },
            );
            usage::count(&dir, &day, usage::bitrate_key(self.settings.bitrate_mbps));
            usage::count(
                &dir,
                &day,
                if self.settings.send_audio {
                    "sound.on"
                } else {
                    "sound.off"
                },
            );
        }
        log_line(
            &dir,
            &format!(
                "casting started to receiver {} using {} at {} fps and {} Mbit/s",
                receiver.id, self.settings.encoder, self.settings.fps, self.settings.bitrate_mbps
            ),
        );
        let handle = castbay_sender::start(options, on_status, code);
        let stopper = handle.stopper();
        let switcher = handle.switcher();
        let rt = self.runtime.clone();
        *rt.lock().unwrap() = Runtime {
            stopper: Some(stopper),
            switcher: Some(switcher),
            code: Some(code_tx),
        };
        crash::mark_casting(&dir, true);
        let _ = self
            .settings
            .apply(&serde_json::json!({"last_receiver":receiver.id}));
        let _ = self.settings.save(&dir);
        let status = self.status.clone();
        let dir2 = dir.clone();
        let settings_on_end = self.settings.clone();
        std::thread::spawn(move || {
            let outcome = handle.join();
            crash::mark_casting(&dir2, false);
            if settings_on_end.usage_stats {
                usage::count(
                    &dir2,
                    &usage::day_of(now_secs()),
                    usage::length_key(started.elapsed().as_secs_f64()),
                );
            }
            let failed = outcome
                .as_ref()
                .err()
                .map(|e| (e.code.clone(), e.detail.clone()));
            if let Some((code, detail)) = failed.as_ref() {
                log_line(&dir2, &format!("casting failed: {code} - {detail}"));
                if settings_on_end.usage_stats {
                    usage::count(&dir2, &usage::day_of(now_secs()), usage::failure_key(code));
                }
                if settings_on_end.error_reports
                    && matches!(
                        code.as_str(),
                        "encoder" | "capture" | "no_picture" | "protocol" | "other"
                    )
                {
                    let text = report_text(
                        &dir2,
                        &settings_on_end,
                        "failure",
                        Some(format!("{code}: {detail}")),
                    );
                    let _ = diagnostics::upload(
                        &text,
                        env!("CARGO_PKG_VERSION"),
                        "failure",
                        &dir2.join("report-cache"),
                    );
                }
            } else {
                log_line(&dir2, "casting stopped");
            }
            if let Ok(mut v) = status.lock() {
                *v = match outcome {
                    Ok(_) => "Ready".into(),
                    Err(e) => format!("Cast failed: {} ({})", e.code, e.detail),
                };
            }
            if let Ok(mut runtime) = rt.lock() {
                *runtime = Runtime::default();
            }
        });
        self.error.clear();
        self.input_mode = InputMode::None;
        if self.settings.minimize_on_cast {
            window.minimize_window();
        }
        cx.notify();
    }

    fn stop(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        if let Some(s) = self.runtime.lock().unwrap().stopper.as_ref() {
            s.stop();
        }
        cx.notify();
    }

    fn choose_receiver(
        &mut self,
        index: usize,
        _: &ClickEvent,
        _: &mut Window,
        cx: &mut Context<Self>,
    ) {
        if index < self.receivers.len() {
            self.selected_receiver = index;
        }
        cx.notify();
    }

    fn choose_source(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        let total = if self.settings.source_kind == "window" {
            self.sources.windows.len()
        } else {
            self.sources.monitors.len()
        };
        if total > 0 {
            self.selected_source = (self.selected_source + 1) % total;
            let value = if self.settings.source_kind == "window" {
                self.sources
                    .windows
                    .get(self.selected_source)
                    .cloned()
                    .unwrap_or_default()
            } else {
                self.sources
                    .monitors
                    .get(self.selected_source)
                    .cloned()
                    .unwrap_or_default()
            };
            let key = if self.settings.source_kind == "window" {
                "source_window"
            } else {
                "source_monitor"
            };
            let patch = serde_json::json!({key:value});
            if let Err(e) = self
                .settings
                .apply(&patch)
                .and_then(|_| self.settings.save(&self.dir))
            {
                self.error = e;
            }
            if let Some(switcher) = self.runtime.lock().unwrap().switcher.as_ref() {
                let target = if self.settings.source_kind == "window" {
                    Target::Window(value)
                } else {
                    Target::Monitor(self.selected_source)
                };
                switcher.switch(target);
            }
        }
        cx.notify();
    }

    fn change_page(&mut self, page: Page, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        self.page = page;
        if page == Page::Settings {
            self.load_sources();
        }
        if page == Page::Paired {
            self.load_paired();
        }
        cx.notify();
    }

    fn load_sources(&mut self) {
        self.sources = Sources {
            monitors: castbay_capture::monitors().unwrap_or_default(),
            windows: castbay_capture::window_titles().unwrap_or_default(),
            system_picker: cfg!(target_os = "linux"),
        };
        let saved = if self.settings.source_kind == "window" {
            &self.settings.source_window
        } else {
            &self.settings.source_monitor
        };
        let list = if self.settings.source_kind == "window" {
            &self.sources.windows
        } else {
            &self.sources.monitors
        };
        self.selected_source = list.iter().position(|source| source == saved).unwrap_or(0);
    }

    fn load_paired(&mut self) {
        self.paired = castbay_link::Pins::load(&self.dir)
            .list()
            .into_iter()
            .map(|r| (r.id, r.name, r.seen))
            .collect();
    }

    fn cycle_setting(
        &mut self,
        key: &'static str,
        _: &ClickEvent,
        _: &mut Window,
        cx: &mut Context<Self>,
    ) {
        let patch = match key {
            "fps" => serde_json::json!({"fps": if self.settings.fps==30 {60} else {30}}),
            "encoder" => {
                let mut values = vec!["auto"];
                values.extend(
                    castbay_sender::available_encoders()
                        .iter()
                        .map(|c| match c {
                            Codec::Amf => "amf",
                            Codec::Nvenc => "nvenc",
                            Codec::Qsv => "qsv",
                            Codec::X264 => "x264",
                        }),
                );
                let i = values
                    .iter()
                    .position(|v| *v == self.settings.encoder)
                    .unwrap_or(0);
                serde_json::json!({"encoder":values[(i+1)%values.len()]})
            }
            "video_format" => {
                let a = ["h264", "auto", "h265"];
                let i = a
                    .iter()
                    .position(|v| *v == self.settings.video_format)
                    .unwrap_or(0);
                serde_json::json!({"video_format":a[(i+1)%a.len()]})
            }
            "bitrate" => {
                serde_json::json!({"bitrate_mbps": if self.settings.bitrate_mbps>=20 {2} else {self.settings.bitrate_mbps+1}})
            }
            "source_kind" => {
                self.selected_source = 0;
                serde_json::json!({"source_kind": if self.settings.source_kind=="monitor" {"window"} else {"monitor"}})
            }
            "language" => {
                serde_json::json!({"language": match self.settings.language.as_str() {"auto"=>"zh","zh"=>"en",_=>"auto"}})
            }
            "send_audio" => serde_json::json!({"send_audio":!self.settings.send_audio}),
            "minimize_on_cast" => {
                serde_json::json!({"minimize_on_cast":!self.settings.minimize_on_cast})
            }
            "usage_stats" => serde_json::json!({"usage_stats":!self.settings.usage_stats}),
            "error_reports" => serde_json::json!({"error_reports":!self.settings.error_reports}),
            "check_updates" => serde_json::json!({"check_updates":!self.settings.check_updates}),
            "autostart" => serde_json::json!({}),
            _ => serde_json::json!({}),
        };
        if key == "usage_stats" {
            let was = self.settings.usage_stats;
            if let Err(e) = self
                .settings
                .apply(&patch)
                .and_then(|_| self.settings.save(&self.dir))
            {
                self.error = e;
                cx.notify();
                return;
            }
            if self.settings.usage_stats && !was {
                if !usage::turn_on(&self.dir) {
                    self.error = "Could not initialize anonymous statistics".into();
                }
            } else if was && !self.settings.usage_stats {
                if let Some(id) = usage::turn_off(&self.dir) {
                    let cache = self.dir.join("report-cache");
                    std::thread::spawn(move || {
                        let body = serde_json::json!({"id":id}).to_string();
                        let _ = diagnostics::post(
                            "/api/stats/delete",
                            "application/json",
                            &[],
                            &body,
                            &cache,
                        );
                    });
                }
            }
        } else if let Err(e) = self
            .settings
            .apply(&patch)
            .and_then(|_| self.settings.save(&self.dir))
        {
            self.error = e;
        }
        if key == "source_kind" {
            self.load_sources();
        }
        log_line(&self.dir, &format!("setting changed: {key}"));
        cx.notify();
    }

    fn key_down(&mut self, event: &gpui::KeyDownEvent, _: &mut Window, cx: &mut Context<Self>) {
        if self.input_mode == InputMode::None {
            return;
        }
        let key = &event.keystroke.key;
        if key == "backspace" || key == "Backspace" {
            self.input.pop();
        } else if key == "enter" || key == "Enter" {
            if self.input_mode == InputMode::PairCode {
                if let Some(tx) = self.runtime.lock().unwrap().code.as_ref() {
                    let _ = tx.send(Some(std::mem::take(&mut self.input)));
                }
                self.input_mode = InputMode::None;
            } else {
                self.input_mode = InputMode::None;
                self.status_text = "Password entered; choose Start casting.".into();
            }
        } else if let Some(c) = event
            .keystroke
            .key_char
            .as_ref()
            .and_then(|s| s.chars().next())
        {
            if !c.is_control() && self.input.len() < 64 {
                self.input.push(c);
            }
        }
        cx.notify();
    }

    fn toggle_autostart(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        match autostart::set(!self.autostart) {
            Ok(()) => {
                self.autostart = !self.autostart;
                log_line(&self.dir, &format!("autostart {}", self.autostart));
            }
            Err(e) => self.error = e,
        }
        cx.notify();
    }

    fn request_report(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        self.report_confirm = !self.report_confirm;
        self.logs = diagnostics::read_log(&self.dir);
        cx.notify();
    }

    fn send_manual_report(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        let dir = self.dir.clone();
        let settings = self.settings.clone();
        self.report_confirm = false;
        self.status_text = "Preparing diagnostic report…".into();
        cx.notify();
        cx.spawn(async move |this, cx| {
            let result = cx
                .background_executor()
                .spawn(async move {
                    let text = report_text(&dir, &settings, "manual", None);
                    diagnostics::upload(
                        &text,
                        env!("CARGO_PKG_VERSION"),
                        "manual",
                        &dir.join("report-cache"),
                    )
                    .map_err(|e| e.text())
                })
                .await;
            let _ = this.update(cx, |app, cx| {
                match result {
                    Ok(id) => app.status_text = format!("Report uploaded: {id}"),
                    Err(e) => app.error = format!("Report upload failed: {e}"),
                }
                log_line(&app.dir, &app.status_text);
                cx.notify();
            });
        })
        .detach();
    }

    fn skip_update(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        if let Some(version) = self
            .update_message
            .split_whitespace()
            .find(|word| update::valid_version(word))
            .map(str::to_string)
        {
            let _ = self
                .settings
                .apply(&serde_json::json!({"ignored_update":version}));
            if let Err(e) = self.settings.save(&self.dir) {
                self.error = e;
            }
            self.update_message = localized(
                "已忽略此版本更新提示",
                "Skipped this update",
                self.settings.chinese(system_is_chinese()),
            )
            .into();
        }
        cx.notify();
    }

    fn dismiss_crash(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        self.pending_crash = None;
        log_line(&self.dir, "previous crash report dismissed");
        cx.notify();
    }

    fn send_crash(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        let Some(pending) = self.pending_crash.clone() else {
            return;
        };
        let dir = self.dir.clone();
        let settings = self.settings.clone();
        cx.spawn(async move |this, cx| {
            let result = cx
                .background_executor()
                .spawn(async move {
                    let text = report_text(&dir, &settings, "crash", Some(pending.text));
                    diagnostics::upload(
                        &text,
                        env!("CARGO_PKG_VERSION"),
                        "crash",
                        &dir.join("report-cache"),
                    )
                    .map_err(|e| e.text())
                })
                .await;
            let _ = this.update(cx, |app, cx| {
                match result {
                    Ok(id) => {
                        app.pending_crash = None;
                        app.status_text = format!("Crash report uploaded: {id}");
                    }
                    Err(e) => app.error = format!("Crash report failed: {e}"),
                }
                log_line(&app.dir, &app.status_text);
                cx.notify();
            });
        })
        .detach();
    }

    fn check_updates(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        self.update_message = "Checking for updates…".into();
        cx.notify();
        cx.spawn(async move |this, cx| {
            let result = cx
                .background_executor()
                .spawn(async move { latest_version() })
                .await;
            let _ = this.update(cx, |app, cx| {
                app.update_message = match result {
                    Ok(Some(v)) if v != env!("CARGO_PKG_VERSION") => {
                        format!("Version {v} is available")
                    }
                    Ok(_) => "You are using the latest version".into(),
                    Err(e) => format!("Update check failed: {e}"),
                };
                cx.notify();
            });
        })
        .detach();
    }

    fn install_update(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        if self
            .runtime
            .lock()
            .map(|r| r.stopper.is_some())
            .unwrap_or(false)
        {
            self.error = "Stop casting before updating.".into();
            cx.notify();
            return;
        }
        let Some(version) = self
            .update_message
            .split_whitespace()
            .find(|w| update::valid_version(w))
            .map(str::to_string)
        else {
            self.error = "Check for an available version first.".into();
            cx.notify();
            return;
        };
        let settings = self.settings.clone();
        let dir = self.dir.clone();
        self.update_message = format!("Downloading and verifying {version}…");
        cx.notify();
        cx.spawn(async move |this, cx| {
            let result = cx
                .background_executor()
                .spawn(async move {
                    let exe = std::env::current_exe().map_err(|e| e.to_string())?;
                    let installed = if cfg!(windows) {
                        std::env::var_os("LOCALAPPDATA").is_some_and(|root| {
                            exe.starts_with(
                                PathBuf::from(root).join("Programs").join("CastBay Desktop"),
                            )
                        })
                    } else {
                        exe == PathBuf::from("/usr/bin/castbay-desktop")
                    };
                    if !installed {
                        return Err("This copy must be updated from the release page.".to_string());
                    }
                    let zh = settings.chinese(system_is_chinese());
                    let package = update::fetch(&version, cfg!(windows), zh, &dir.join("update"))?;
                    #[cfg(windows)]
                    {
                        update::install(&package, &exe)?;
                        std::process::exit(0);
                    }
                    #[cfg(not(windows))]
                    {
                        match update::install(&package)? {
                            update::Installed::Yes => {
                                update::start_again(&exe)?;
                                std::process::exit(0);
                            }
                            update::Installed::Cancelled => Err("Update cancelled.".into()),
                            update::Installed::NoPrompt => {
                                open_path(&package);
                                Ok(())
                            }
                        }
                    }
                })
                .await;
            let _ = this.update(cx, |app, cx| {
                match result {
                    Ok(()) => {
                        app.update_message = "Installer opened; follow the system prompt.".into()
                    }
                    Err(e) => app.error = e,
                }
                cx.notify();
            });
        })
        .detach();
    }

    fn dismiss_welcome(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        let _ = self
            .settings
            .apply(&serde_json::json!({"welcome_done":true}));
        if let Err(e) = self.settings.save(&self.dir) {
            self.error = e;
        }
        cx.notify();
    }

    fn clear_error(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        self.error.clear();
        cx.notify();
    }

    fn reset(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        if !self.reset_confirm {
            self.reset_confirm = true;
            cx.notify();
            return;
        }
        self.reset_confirm = false;
        self.settings.restore_defaults();
        if let Err(e) = self.settings.save(&self.dir) {
            self.error = e;
        }
        cx.notify();
    }

    fn forget_share(&mut self, _: &ClickEvent, _: &mut Window, cx: &mut Context<Self>) {
        match std::fs::remove_file(self.dir.join("screencast-token")) {
            Ok(()) => self.status_text = "Screen share permission will be requested again".into(),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                self.status_text = "No saved screen permission".into()
            }
            Err(e) => self.error = e.to_string(),
        }
        cx.notify();
    }

    fn remove_paired(
        &mut self,
        id: String,
        _: &ClickEvent,
        _: &mut Window,
        cx: &mut Context<Self>,
    ) {
        if let Err(e) = castbay_link::Pins::load(&self.dir).forget(&id) {
            self.error = e.to_string();
        }
        self.load_paired();
        cx.notify();
    }
}

impl Focusable for CastBay {
    fn focus_handle(&self, _: &App) -> FocusHandle {
        self.focus.clone()
    }
}

impl Render for CastBay {
    fn render(&mut self, _window: &mut Window, cx: &mut Context<Self>) -> impl IntoElement {
        let dark = 0x101827;
        let panel = 0x1c2a3a;
        let muted = 0xa8b5c8;
        let accent = 0x8c74e8;
        let page = self.page;
        let zh = self.settings.chinese(system_is_chinese());
        let mut root = div()
            .id("main")
            .size_full()
            .bg(rgb(dark))
            .text_color(rgb(0xe6edf7))
            .flex()
            .flex_col()
            .p_6()
            .gap_4()
            .overflow_y_scroll()
            .track_focus(&self.focus)
            .on_key_down(cx.listener(Self::key_down));
        root = root.child(
            div()
                .flex()
                .items_center()
                .justify_between()
                .pb_3()
                .border_b_1()
                .border_color(rgb(0x34445a))
                .child(
                    div()
                        .text_2xl()
                        .font_weight(FontWeight::BOLD)
                        .child("CastBay Desktop"),
                )
                .child(
                    div()
                        .flex()
                        .gap_2()
                        .child(nav(
                            localized("首页", "Home", zh),
                            page == Page::Home,
                            cx.listener(|this, e, w, cx| this.change_page(Page::Home, e, w, cx)),
                        ))
                        .child(nav(
                            localized("设置", "Settings", zh),
                            page == Page::Settings,
                            cx.listener(|this, e, w, cx| {
                                this.change_page(Page::Settings, e, w, cx)
                            }),
                        ))
                        .child(nav(
                            localized("已配对", "Paired", zh),
                            page == Page::Paired,
                            cx.listener(|this, e, w, cx| this.change_page(Page::Paired, e, w, cx)),
                        ))
                        .child(nav(
                            localized("诊断", "Diagnostics", zh),
                            page == Page::Diagnostics,
                            cx.listener(|this, e, w, cx| {
                                this.change_page(Page::Diagnostics, e, w, cx)
                            }),
                        ))
                        .child(nav(
                            localized("关于", "About", zh),
                            page == Page::About,
                            cx.listener(|this, e, w, cx| this.change_page(Page::About, e, w, cx)),
                        )),
                ),
        );
        root = root.child(
            div()
                .text_sm()
                .text_color(rgb(muted))
                .child(self.status_text.clone()),
        );
        if !self.error.is_empty() {
            root = root.child(
                div()
                    .rounded_md()
                    .bg(rgb(0x4c2634))
                    .p_3()
                    .child(self.error.clone())
                    .child(button("Dismiss", cx.listener(Self::clear_error))),
            );
        }
        if let Some(pending) = self.pending_crash.as_ref() {
            root = root.child(
                section(
                    localized("上次异常退出", "Previous unexpected exit", zh),
                    0x4c2634,
                )
                .child(div().child(pending.text.clone()))
                .child(
                    div()
                        .flex()
                        .gap_2()
                        .child(button(
                            localized("发送崩溃报告", "Send crash report", zh),
                            cx.listener(Self::send_crash),
                        ))
                        .child(button(
                            localized("忽略", "Dismiss", zh),
                            cx.listener(Self::dismiss_crash),
                        )),
                ),
            );
        }
        if self.report_confirm {
            let home = std::env::var("HOME")
                .or_else(|_| std::env::var("USERPROFILE"))
                .unwrap_or_default();
            let preview = diagnostics::scrub(&diagnostics::newest(&self.logs, 40_000), &home);
            root=root.child(section(localized("上传前预览","Review before upload",zh),panel)
                .child(div().text_sm().child(format!("This uploads system details, settings and recent logs. Addresses, receiver names and your home folder are removed first.
{}",preview)))
                .child(div().flex().gap_2().child(button(localized("确认上传","Upload report",zh),cx.listener(Self::send_manual_report))).child(button(localized("取消","Cancel",zh),cx.listener(Self::request_report)))));
        }
        match self.page {
            Page::Home => {
                if !self.settings.welcome_done {
                    root=root.child(section(localized("欢迎使用 CastBay Desktop","Welcome to CastBay Desktop",zh),panel)
                        .child(div().child(localized("在接收端电视上开启桌面投屏，然后选择接收端和屏幕或窗口。","Enable desktop casting on your CastBay receiver, then choose a receiver and a screen or window.",zh)))
                        .child(button(localized("知道了","Got it",zh),cx.listener(Self::dismiss_welcome))));
                }
                root = root.child(section(localized("接收端", "Receivers", zh), panel).child(
                    button(localized("刷新", "Refresh", zh), cx.listener(Self::refresh)),
                ));
                if self.receivers.is_empty() {
                    root = root.child(div().text_color(rgb(muted)).child(localized(
                        "没有发现接收端。请在电视上打开 CastBay 后刷新。",
                        "No receivers found. Open CastBay on a receiver and refresh.",
                        zh,
                    )));
                }
                for (i, r) in self.receivers.iter().enumerate() {
                    let selected = i == self.selected_receiver;
                    let label = format!(
                        "{}  ·  {}:{}  ·  {}  ·  {}{}",
                        r.name,
                        r.ip,
                        r.port,
                        r.app_version.as_deref().unwrap_or("CastBay"),
                        r.model.as_deref().unwrap_or(""),
                        if r.password {
                            if zh {
                                "  ·  需要密码"
                            } else {
                                "  ·  Password required"
                            }
                        } else {
                            if r.pair == "required" {
                                if zh {
                                    "  ·  需要配对"
                                } else {
                                    "  ·  Pairing required"
                                }
                            } else {
                                ""
                            }
                        }
                    );
                    root =
                        root.child(
                            div()
                                .rounded_md()
                                .border_1()
                                .border_color(rgb(if selected { accent } else { 0x34445a }))
                                .bg(rgb(panel))
                                .p_3()
                                .child(label)
                                .id(format!("receiver-{i}"))
                                .on_click(cx.listener(move |this, e, w, cx| {
                                    this.choose_receiver(i, e, w, cx)
                                })),
                        );
                }
                let src = if self.settings.source_kind == "window" {
                    self.sources
                        .windows
                        .get(self.selected_source)
                        .cloned()
                        .unwrap_or_else(|| "Choose window".into())
                } else {
                    self.sources
                        .monitors
                        .get(self.selected_source)
                        .cloned()
                        .unwrap_or_else(|| {
                            if self.sources.system_picker {
                                "Choose screen using system picker".into()
                            } else {
                                "Choose display".into()
                            }
                        })
                };
                let mut source_panel = section(localized("投屏来源", "Source", zh), panel);
                if !self.sources.system_picker {
                    source_panel = source_panel.child(button(
                        format!(
                            "{}: {}",
                            if zh {
                                "来源类型（点击切换）"
                            } else {
                                "Type (click to change)"
                            },
                            if zh {
                                if self.settings.source_kind == "window" {
                                    "窗口"
                                } else {
                                    "屏幕"
                                }
                            } else if self.settings.source_kind == "window" {
                                "Window"
                            } else {
                                "Screen"
                            }
                        ),
                        cx.listener(|this, e, w, cx| this.cycle_setting("source_kind", e, w, cx)),
                    ));
                }
                source_panel = source_panel.child(button(
                    format!(
                        "{}: {src}",
                        if zh {
                            "捕获来源（点击切换）"
                        } else {
                            "Capture (click to cycle)"
                        }
                    ),
                    cx.listener(Self::choose_source),
                ));
                root = root.child(source_panel);
                root = root.child(
                    div()
                        .flex()
                        .gap_3()
                        .child(button(
                            localized("开始投屏", "Start casting", zh),
                            cx.listener(Self::start),
                        ))
                        .child(button(
                            localized("停止投屏", "Stop casting", zh),
                            cx.listener(Self::stop),
                        )),
                );
                if self.input_mode != InputMode::None {
                    let value = if self.input_mode == InputMode::Password {
                        "•".repeat(self.input.len())
                    } else {
                        self.input.clone()
                    };
                    let form = section(
                        if self.input_mode == InputMode::PairCode {
                            localized("配对码", "Pairing code", zh)
                        } else {
                            localized("接收端密码", "Receiver password", zh)
                        },
                        panel,
                    )
                    .child(div().rounded_md().bg(rgb(0x101827)).p_3().child(value))
                    .child(localized(
                        "在应用窗口输入内容并按 Enter 确认。",
                        "Type in the focused window; press Enter to submit.",
                        zh,
                    ));
                    root = root.child(form);
                }
                if let Ok(rt) = self.runtime.lock() {
                    if rt.code.is_some() && self.input_mode == InputMode::None {
                        root = root.child(button(
                            localized("输入配对码", "Enter pairing code", zh),
                            cx.listener(|this, _, window, cx| {
                                this.input_mode = InputMode::PairCode;
                                this.input.clear();
                                window.focus(&this.focus, cx);
                                cx.notify();
                            }),
                        ));
                    }
                }
                if !self.sources.monitors.is_empty() || !self.sources.windows.is_empty() {
                    root = root.child(div().text_sm().text_color(rgb(muted)).child(format!(
                        "Available: {} displays · {} windows",
                        self.sources.monitors.len(),
                        self.sources.windows.len()
                    )));
                }
            }
            Page::Settings => {
                root=root.child(section(localized("偏好设置","Preferences",zh),panel)
                    .child(setting_line(localized("帧率","Frame rate",zh),format!("{} fps",self.settings.fps),cx.listener(|this,e,w,cx|this.cycle_setting("fps",e,w,cx))))
                    .child(setting_line(localized("码率","Bitrate",zh),format!("{} Mbit/s",self.settings.bitrate_mbps),cx.listener(|this,e,w,cx|this.cycle_setting("bitrate",e,w,cx))))
                    .child(setting_line(localized("编码器","Encoder",zh),if self.settings.encoder=="auto" {localized("自动","Auto",zh).into()} else {self.settings.encoder.clone()},cx.listener(|this,e,w,cx|this.cycle_setting("encoder",e,w,cx))))
                    .child(setting_line(localized("视频格式","Video format",zh),localized(match self.settings.video_format.as_str() {"auto"=>"自动","h265"=>"H.265",_=>"H.264"},match self.settings.video_format.as_str() {"auto"=>"Auto","h265"=>"H.265",_=>"H.264"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("video_format",e,w,cx))))
                    .child(setting_line(localized("发送声音","Send audio",zh),localized(if self.settings.send_audio {"开"} else {"关"},if self.settings.send_audio {"On"} else {"Off"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("send_audio",e,w,cx))))
                    .child(setting_line(localized("投屏后最小化","Minimize when casting",zh),localized(if self.settings.minimize_on_cast {"开"} else {"关"},if self.settings.minimize_on_cast {"On"} else {"Off"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("minimize_on_cast",e,w,cx))))
                    .child(setting_line(localized("语言","Language",zh),localized(match self.settings.language.as_str() {"zh"=>"中文","en"=>"English",_=>"跟随系统"},match self.settings.language.as_str() {"zh"=>"Chinese","en"=>"English",_=>"Follow system"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("language",e,w,cx))))
                    .child(setting_line(localized("开机启动","Start with computer",zh),localized(if self.autostart {"开"} else {"关"},if self.autostart {"On"} else {"Off"},zh).to_string(),cx.listener(Self::toggle_autostart)))
                    .child(div().text_sm().text_color(rgb(muted)).child(localized("默认关闭；启用后每天发送匿名汇总。关闭时会请求删除已上传的统计数据。","Off by default; when enabled, anonymous daily summaries are sent. Turning it off requests deletion of uploaded statistics.",zh)))
                    .child(setting_line(localized("发送匿名使用统计","Anonymous usage statistics",zh),localized(if self.settings.usage_stats {"开"} else {"关"},if self.settings.usage_stats {"On"} else {"Off"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("usage_stats",e,w,cx))))
                    .child(div().text_sm().text_color(rgb(muted)).child(localized("默认关闭；开启后，程序异常结束或投屏遇到特定错误时会自动上传已脱敏的报告。","Off by default; when enabled, a scrubbed report is sent after a crash or certain casting failures.",zh)))
                    .child(setting_line(localized("自动发送错误报告","Automatic error reports",zh),localized(if self.settings.error_reports {"开"} else {"关"},if self.settings.error_reports {"On"} else {"Off"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("error_reports",e,w,cx))))
                    .child(setting_line(localized("启动时检查更新","Check for updates at startup",zh),localized(if self.settings.check_updates {"开"} else {"关"},if self.settings.check_updates {"On"} else {"Off"},zh).to_string(),cx.listener(|this,e,w,cx|this.cycle_setting("check_updates",e,w,cx))))
                    .child(button(localized("清除屏幕共享授权","Forget saved screen-sharing permission",zh),cx.listener(Self::forget_share)))
                    .child(button(if self.reset_confirm {localized("再次点击确认恢复默认设置","Click again to restore defaults",zh)} else {localized("恢复默认设置","Restore defaults",zh)},cx.listener(Self::reset))));
            }
            Page::Paired => {
                root = root.child(section(
                    localized("已配对的接收端", "Paired receivers", zh),
                    panel,
                ));
                if self.paired.is_empty() {
                    root = root.child(div().text_color(rgb(muted)).child(localized(
                        "尚无已配对的接收端。",
                        "No paired receivers yet.",
                        zh,
                    )));
                }
                for (id, name, seen) in self.paired.clone() {
                    root = root.child(
                        div()
                            .flex()
                            .justify_between()
                            .rounded_md()
                            .bg(rgb(panel))
                            .p_3()
                            .child(format!(
                                "{} · {} {seen}",
                                name,
                                localized("最近使用", "last used", zh)
                            ))
                            .child(button(
                                localized("忘记设备", "Forget", zh),
                                cx.listener(move |this, e, w, cx| {
                                    this.remove_paired(id.clone(), e, w, cx)
                                }),
                            )),
                    );
                }
            }
            Page::Diagnostics => {
                root = root.child(
                    section(localized("诊断信息", "Diagnostics", zh), panel)
                        .child(
                            div()
                                .text_color(rgb(muted))
                                .child(diagnostics::system_line()),
                        )
                        .child(
                            div()
                                .text_color(rgb(muted))
                                .child(format!("Log directory: {}", self.dir.display())),
                        )
                        .child(div().text_color(rgb(muted)).child(format!(
                            "Paired receivers: {}",
                            castbay_link::Pins::load(&self.dir).list().len()
                        )))
                        .child(button(
                            localized(
                                "预览并上传诊断报告",
                                "Review and upload diagnostic report",
                                zh,
                            ),
                            cx.listener(Self::request_report),
                        ))
                        .child(button(
                            localized("打开日志目录", "Open logs folder", zh),
                            cx.listener(|this, _, _, cx| {
                                open_path(&this.dir);
                                cx.notify();
                            }),
                        )),
                );
            }
            Page::About => {
                root = root.child(
                    section(
                        localized("关于 CastBay Desktop", "About CastBay Desktop", zh),
                        panel,
                    )
                    .child(
                        div()
                            .text_color(rgb(muted))
                            .child(format!("Version {}", env!("CARGO_PKG_VERSION"))),
                    )
                    .child(button(
                        localized("检查更新", "Check for updates", zh),
                        cx.listener(Self::check_updates),
                    ))
                    .child(button(
                        localized("安装可用更新", "Install available update", zh),
                        cx.listener(Self::install_update),
                    ))
                    .child(button(
                        localized("忽略此版本", "Skip this version", zh),
                        cx.listener(Self::skip_update),
                    ))
                    .child(button(
                        localized("查看更新说明", "Release notes", zh),
                        cx.listener(|this, _, _, _| {
                            if let Some(v) = this
                                .update_message
                                .split_whitespace()
                                .find(|word| update::valid_version(word))
                            {
                                open_url(&format!(
                                    "https://castbay.weenas.com/{}changelog#desktop-v{v}",
                                    if this.settings.chinese(system_is_chinese()) {
                                        "zh/"
                                    } else {
                                        ""
                                    }
                                ));
                            }
                        }),
                    ))
                    .child(button(
                        localized("打开桌面版发布页", "Open CastBay Desktop releases", zh),
                        cx.listener(|_, _, _, _| {
                            open_url("https://github.com/weenas/castbay/releases")
                        }),
                    ))
                    .child(button(
                        localized("反馈问题", "Report a problem", zh),
                        cx.listener(|this, _, _, _| {
                            open_url(&format!(
                                "https://castbay.weenas.com/{}feedback?from=desktop&v={}",
                                if this.settings.chinese(system_is_chinese()) {
                                    "zh/"
                                } else {
                                    ""
                                },
                                env!("CARGO_PKG_VERSION")
                            ))
                        }),
                    ))
                    .child(button(
                        localized("查看 GPL-3.0 许可证", "Open GPL-3.0 license", zh),
                        cx.listener(|_, _, _, _| {
                            open_url("https://github.com/weenas/castbay/blob/main/LICENSE")
                        }),
                    ))
                    .child(button(
                        localized("打开项目网站", "Open project website", zh),
                        cx.listener(|_, _, _, _| open_url("https://castbay.weenas.com")),
                    ))
                    .child(button(
                        localized("打开源代码仓库", "Open source repository", zh),
                        cx.listener(|_, _, _, _| open_url("https://github.com/weenas/castbay")),
                    ))
                    .child(
                        div()
                            .text_color(rgb(muted))
                            .child(self.update_message.clone()),
                    ),
                );
            }
        }
        root
    }
}

fn section(title: &'static str, color: u32) -> gpui::Div {
    div()
        .flex()
        .flex_col()
        .gap_3()
        .rounded_lg()
        .bg(rgb(color))
        .p_4()
        .child(div().text_lg().font_weight(FontWeight::BOLD).child(title))
}
fn nav(
    label: &'static str,
    selected: bool,
    on_click: impl Fn(&ClickEvent, &mut Window, &mut gpui::App) + 'static,
) -> gpui::Stateful<gpui::Div> {
    div()
        .id(label)
        .cursor_pointer()
        .rounded_md()
        .px_3()
        .py_2()
        .bg(rgb(if selected { 0x6850b5 } else { 0x28384d }))
        .child(label)
        .on_click(on_click)
}
fn button(
    label: impl Into<SharedString>,
    on_click: impl Fn(&ClickEvent, &mut Window, &mut gpui::App) + 'static,
) -> gpui::Stateful<gpui::Div> {
    let label: SharedString = label.into();
    div()
        .id(label.clone())
        .cursor_pointer()
        .rounded_md()
        .bg(rgb(0x2a3a50))
        .px_4()
        .py_2()
        .child(label)
        .on_click(on_click)
}
fn setting_line(
    label: &'static str,
    value: String,
    on_click: impl Fn(&ClickEvent, &mut Window, &mut gpui::App) + 'static,
) -> gpui::Div {
    div()
        .flex()
        .justify_between()
        .items_center()
        .py_2()
        .child(label)
        .child(button(value, on_click))
}
fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}
fn send_usage_once(dir: &std::path::Path, settings: &Settings) {
    if !settings.usage_stats {
        return;
    }
    let Some((id, days)) = usage::finished_days(dir, &usage::day_of(now_secs())) else {
        return;
    };
    let cache = dir.join("usage-cache");
    let encoders: Vec<&str> = castbay_sender::available_encoders()
        .iter()
        .map(|c| match c {
            Codec::Amf => "amf",
            Codec::Nvenc => "nvenc",
            Codec::Qsv => "qsv",
            Codec::X264 => "x264",
        })
        .collect();
    let height = castbay_capture::monitors()
        .ok()
        .and_then(|m| m.iter().filter_map(|s| usage::monitor_height(s)).max());
    let session = std::env::var("XDG_SESSION_TYPE").unwrap_or_else(|_| {
        if cfg!(windows) {
            "windows".into()
        } else {
            "other".into()
        }
    });
    for (day, counts) in days {
        let body=serde_json::json!({
            "platform":"desktop","id":id,"day":day,"app":env!("CARGO_PKG_VERSION"),
            "os":if cfg!(windows){"windows"}else{"linux"},
            "osVersion":if cfg!(windows){diagnostics::windows_version(&diagnostics::system_line()).unwrap_or_else(||"unknown".into())}else{diagnostics::linux_distribution(&std::fs::read_to_string("/etc/os-release").unwrap_or_default())},
            "arch":std::env::consts::ARCH,"session":match session.as_str(){"windows"=>"windows","wayland"=>"wayland","x11"=>"x11",_=>"other"},
            "screen":height.map(usage::screen_class).unwrap_or("unknown"),"lang":if settings.chinese(system_is_chinese()){"zh"}else{"en"},
            "encoders":encoders.iter().copied().collect::<Vec<_>>(),
            "counts":counts,"settings":{"encoder":settings.encoder,"language":settings.language,"updates":settings.check_updates,"errorReports":settings.error_reports}
        }).to_string();
        match diagnostics::post("/api/stats", "application/json", &[], &body, &cache) {
            Ok((code, _)) if (200..300).contains(&code) => {
                usage::forget(dir, &day);
                log_line(dir, &format!("usage summary sent for {day}"));
            }
            other => {
                log_line(dir, &format!("usage summary failed: {other:?}"));
                break;
            }
        }
    }
}
fn system_is_chinese() -> bool {
    let locale = std::env::var("LANG")
        .unwrap_or_default()
        .to_ascii_lowercase();
    locale.starts_with("zh") || locale.contains("_cn") || locale.contains("-cn")
}
fn version_cmp(a: &str, b: &str) -> std::cmp::Ordering {
    let aa: Vec<u64> = a.split('.').map(|x| x.parse().unwrap_or(0)).collect();
    let bb: Vec<u64> = b.split('.').map(|x| x.parse().unwrap_or(0)).collect();
    for i in 0..aa.len().max(bb.len()) {
        match aa
            .get(i)
            .copied()
            .unwrap_or(0)
            .cmp(&bb.get(i).copied().unwrap_or(0))
        {
            std::cmp::Ordering::Equal => {}
            other => return other,
        }
    }
    std::cmp::Ordering::Equal
}
fn latest_version() -> Result<Option<String>, String> {
    let urls = [
        "https://api.github.com/repos/weenas/castbay/releases?per_page=30",
        "https://castbay.weenas.com/desktop/latest.json",
        "https://cast.weenas.com/desktop/latest.json",
    ];
    let mut newest: Option<String> = None;
    let mut failure = String::from("No update service responded");
    for url in urls {
        let output = match std::process::Command::new("curl")
            .args([
                "--fail",
                "--silent",
                "--show-error",
                "--proto",
                "=https",
                "--connect-timeout",
                "10",
                "--max-time",
                "30",
                url,
            ])
            .output()
        {
            Ok(output) => output,
            Err(e) => {
                failure = e.to_string();
                continue;
            }
        };
        if !output.status.success() {
            failure = String::from_utf8_lossy(&output.stderr).trim().to_string();
            continue;
        }
        let data: serde_json::Value = match serde_json::from_slice(&output.stdout) {
            Ok(data) => data,
            Err(e) => {
                failure = e.to_string();
                continue;
            }
        };
        let mut candidates = Vec::new();
        if let Some(list) = data.as_array() {
            for item in list {
                if item["draft"].as_bool().unwrap_or(false) {
                    continue;
                }
                if let Some(version) = item["tag_name"]
                    .as_str()
                    .and_then(|tag| tag.strip_prefix("desktop-v"))
                {
                    if update::valid_version(version) {
                        candidates.push(version.to_string());
                    }
                }
            }
        } else if let Some(version) = data["version"].as_str() {
            if update::valid_version(version) {
                candidates.push(version.to_string());
            }
        }
        for version in candidates {
            if newest
                .as_deref()
                .is_none_or(|current| version_cmp(&version, current).is_gt())
            {
                newest = Some(version);
            }
        }
        if newest.is_some() {
            break;
        }
    }
    let Some(version) = newest else {
        return Err(failure);
    };
    if version_cmp(&version, env!("CARGO_PKG_VERSION")).is_gt() {
        Ok(Some(version))
    } else {
        Ok(None)
    }
}
fn log_line(dir: &std::path::Path, line: &str) {
    use std::io::Write;
    let _ = std::fs::create_dir_all(dir);
    let path = dir.join("castbay-desktop.log");
    if std::fs::metadata(&path)
        .map(|m| m.len() > 512 * 1024)
        .unwrap_or(false)
    {
        let _ = std::fs::rename(&path, dir.join("castbay-desktop.old.log"));
    }
    if let Ok(mut f) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
    {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let _ = writeln!(f, "{now} {line}");
    }
}
fn report_text(
    dir: &std::path::Path,
    settings: &Settings,
    kind: &str,
    extra: Option<String>,
) -> String {
    let mut head = format!(
        "CastBay Desktop problem report, {}\nVersion: {}\nSystem: {}\nArchitecture: {}\nSettings: {} fps, {} Mbit/s, encoder {}, audio {}, language {}\nPaired receivers: {}\n",
        usage::day_of(
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs()
        ),
        env!("CARGO_PKG_VERSION"),
        diagnostics::system_line(),
        std::env::consts::ARCH,
        settings.fps,
        settings.bitrate_mbps,
        settings.encoder,
        if settings.send_audio { "on" } else { "off" },
        settings.language,
        castbay_link::Pins::load(dir).list().len()
    );
    if let Some(detail) = extra {
        head.push_str(&format!("\n== {kind} ==\n{detail}\n"));
    }
    let home = std::env::var("HOME")
        .or_else(|_| std::env::var("USERPROFILE"))
        .unwrap_or_default();
    diagnostics::report_text(&head, &diagnostics::read_log(dir), &home)
}

fn localized(zh: &'static str, en: &'static str, is_zh: bool) -> &'static str {
    if is_zh { zh } else { en }
}
fn data_dir() -> PathBuf {
    #[cfg(windows)]
    {
        return std::env::var_os("APPDATA")
            .map(PathBuf::from)
            .unwrap_or_else(std::env::temp_dir)
            .join("com.weenas.castbay.desktop");
    }
    #[cfg(target_os = "linux")]
    {
        return std::env::var_os("XDG_DATA_HOME")
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                PathBuf::from(std::env::var_os("HOME").unwrap_or_default()).join(".local/share")
            })
            .join("com.weenas.castbay.desktop");
    }
    #[allow(unreachable_code)]
    std::env::temp_dir().join("com.weenas.castbay.desktop")
}
fn open_url(url: &str) {
    #[cfg(target_os = "linux")]
    {
        let _ = std::process::Command::new("xdg-open").arg(url).spawn();
    }
    #[cfg(windows)]
    {
        let _ = std::process::Command::new("rundll32")
            .args(["url.dll,FileProtocolHandler", url])
            .spawn();
    }
}
fn open_path(path: &std::path::Path) {
    #[cfg(target_os = "linux")]
    {
        let _ = std::process::Command::new("xdg-open").arg(path).spawn();
    }
    #[cfg(windows)]
    {
        let _ = std::process::Command::new("explorer").arg(path).spawn();
    }
}

fn build_tray() -> Option<tray_icon::TrayIcon> {
    use tray_icon::{
        Icon, MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent,
        menu::{Menu, MenuEvent, MenuItem, PredefinedMenuItem},
    };
    let menu = Menu::new();
    let open = MenuItem::with_id("open", "Open CastBay Desktop", true, None);
    let settings = MenuItem::with_id("settings", "Settings", true, None);
    let switch = MenuItem::with_id("switch", "Switch casting source…", true, None);
    let about = MenuItem::with_id("about", "About", true, None);
    let stop = MenuItem::with_id("stop", "Stop casting", true, None);
    let quit = MenuItem::with_id("quit", "Quit", true, None);
    menu.append_items(&[
        &open,
        &settings,
        &switch,
        &about,
        &PredefinedMenuItem::separator(),
        &stop,
        &PredefinedMenuItem::separator(),
        &quit,
    ])
    .ok()?;
    let mut rgba = vec![0u8; 32 * 32 * 4];
    for y in 0..32 {
        for x in 0..32 {
            let i = (y * 32 + x) * 4;
            let inside = (6..26).contains(&x) && (8..24).contains(&y);
            if inside {
                rgba[i..i + 4].copy_from_slice(&[140, 116, 232, 255]);
            }
            if inside
                && ((y == 12 && (11..21).contains(&x))
                    || (y == 15 && (9..23).contains(&x))
                    || (y == 18 && (11..21).contains(&x)))
            {
                rgba[i..i + 4].copy_from_slice(&[255, 255, 255, 255]);
            }
        }
    }
    let icon = Icon::from_rgba(rgba, 32, 32).ok()?;
    MenuEvent::set_event_handler(Some(|event: tray_icon::menu::MenuEvent| {
        if let Ok(mut action) = TRAY_ACTION.lock() {
            *action = Some(event.id.0);
        }
    }));
    TrayIconEvent::set_event_handler(Some(|event| {
        if let TrayIconEvent::Click {
            button: MouseButton::Left,
            button_state: MouseButtonState::Up,
            ..
        } = event
        {
            if let Ok(mut action) = TRAY_ACTION.lock() {
                *action = Some("open".into());
            }
        }
    }));
    TrayIconBuilder::new()
        .with_tooltip("CastBay Desktop")
        .with_menu(Box::new(menu))
        .with_icon(icon)
        .build()
        .ok()
}

fn ensure_single_instance() -> bool {
    const ADDRESS: &str = "127.0.0.1:51833";
    match TcpListener::bind(ADDRESS) {
        Ok(listener) => {
            let _ = listener.set_nonblocking(true);
            std::thread::spawn(move || {
                loop {
                    match listener.accept() {
                        Ok((mut stream, _)) => {
                            let mut buffer = [0u8; 64];
                            if stream
                                .read(&mut buffer)
                                .is_ok_and(|n| &buffer[..n] == b"castbay-activate\n")
                            {
                                ACTIVATE_REQUESTED.store(true, Ordering::SeqCst);
                            }
                        }
                        Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                            std::thread::sleep(Duration::from_millis(250))
                        }
                        Err(_) => break,
                    }
                }
            });
            true
        }
        Err(_) => {
            if let Ok(mut stream) = TcpStream::connect(ADDRESS) {
                let _ = stream.write_all(b"castbay-activate\n");
            }
            false
        }
    }
}

fn main() {
    if !ensure_single_instance() {
        return;
    }
    let _tray = build_tray();
    application().run(|cx: &mut App| {
        let bounds = Bounds::centered(None, size(px(900.), px(720.)), cx);
        let minimized = std::env::args().any(|arg| arg == "--minimized");
        cx.open_window(
            WindowOptions {
                window_bounds: Some(WindowBounds::Windowed(bounds)),
                ..Default::default()
            },
            move |window, cx| {
                window.set_window_title("CastBay Desktop");
                let app = cx.new(CastBay::new);
                let handle = window.window_handle();
                app.update(cx, |app, _| app.window_handle = Some(handle));
                let app_for_close = app.downgrade();
                window.on_window_should_close(cx, move |window, cx| {
                    let casting = app_for_close
                        .read_with(cx, |app, _| {
                            app.runtime
                                .lock()
                                .map(|runtime| runtime.stopper.is_some())
                                .unwrap_or(false)
                        })
                        .unwrap_or(false);
                    if casting {
                        window.minimize_window();
                        false
                    } else {
                        true
                    }
                });
                if minimized {
                    window.minimize_window();
                }
                app
            },
        )
        .expect("open CastBay");
        cx.activate(true);
    });
}
