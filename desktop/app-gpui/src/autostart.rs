use std::path::PathBuf;

#[cfg(windows)]
const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";
#[cfg(windows)]
const RUN_VALUE: &str = "CastBayDesktop";

#[cfg(windows)]
fn reg(args: &[&str]) -> std::io::Result<std::process::Output> {
    use std::os::windows::process::CommandExt;
    std::process::Command::new("reg")
        .args(args)
        .creation_flags(0x0800_0000)
        .output()
}
#[cfg(windows)]
pub fn get() -> bool {
    reg(&["query", RUN_KEY, "/v", RUN_VALUE])
        .map(|o| o.status.success())
        .unwrap_or(false)
}
#[cfg(windows)]
pub fn set(enabled: bool) -> Result<(), String> {
    if enabled {
        let exe = std::env::current_exe().map_err(|e| e.to_string())?;
        let value = format!(r#""{}" --minimized"#, exe.display());
        if !reg(&[
            "add", RUN_KEY, "/v", RUN_VALUE, "/t", "REG_SZ", "/d", &value, "/f",
        ])
        .map_err(|e| e.to_string())?
        .status
        .success()
        {
            return Err("Could not write the Windows startup entry".into());
        }
    } else {
        let _ = reg(&["delete", RUN_KEY, "/v", RUN_VALUE, "/f"]);
    }
    Ok(())
}

#[cfg(not(windows))]
fn entry() -> Result<PathBuf, String> {
    let base = std::env::var_os("XDG_CONFIG_HOME")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".config")))
        .ok_or("No home folder")?;
    Ok(base.join("autostart").join("castbay-desktop.desktop"))
}
#[cfg(not(windows))]
pub fn get() -> bool {
    entry().map(|p| p.exists()).unwrap_or(false)
}
#[cfg(not(windows))]
pub fn set(enabled: bool) -> Result<(), String> {
    let path = entry()?;
    if enabled {
        let exe = std::env::current_exe().map_err(|e| e.to_string())?;
        let text = format!(
            r#"[Desktop Entry]
Type=Application
Name=CastBay Desktop
Exec="{}" --minimized
X-GNOME-Autostart-enabled=true
"#,
            exe.display()
        );
        std::fs::create_dir_all(path.parent().unwrap()).map_err(|e| e.to_string())?;
        std::fs::write(&path, text).map_err(|e| e.to_string())?;
    } else {
        match std::fs::remove_file(&path) {
            Ok(()) => {}
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {}
            Err(e) => return Err(e.to_string()),
        }
    }
    Ok(())
}
