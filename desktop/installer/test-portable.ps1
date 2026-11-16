# Extracts the portable package, starts the application and verifies that it loaded the app-local CRT.
# Usage: test-portable.ps1 -Zip <portable zip>
param([Parameter(Mandatory)] [string] $Zip)
$ErrorActionPreference = 'Stop'
$temp = Join-Path $env:TEMP ([guid]::NewGuid().ToString('N'))
$process = $null
try {
  Expand-Archive -LiteralPath $Zip -DestinationPath $temp
  $appDir = Get-ChildItem $temp -Directory | Select-Object -First 1
  if (-not $appDir) { throw 'Portable archive did not contain an application directory.' }
  $exe = Join-Path $appDir.FullName 'CastBay Desktop.exe'
  $runtimeDll = Join-Path $appDir.FullName 'vcruntime140.dll'
  if (-not (Test-Path $exe)) { throw 'Portable archive did not contain CastBay Desktop.exe.' }
  if (-not (Test-Path $runtimeDll)) { throw 'Portable archive did not contain vcruntime140.dll.' }

  $process = Start-Process -FilePath $exe -WorkingDirectory $appDir.FullName -PassThru
  Start-Sleep -Seconds 8
  $process.Refresh()
  if ($process.HasExited) { throw "Portable application exited with code $($process.ExitCode)." }
  $loadedRuntime = (Get-Process -Id $process.Id).Modules |
    Where-Object ModuleName -IEQ 'vcruntime140.dll' | Select-Object -First 1
  if (-not $loadedRuntime -or -not $loadedRuntime.FileName.StartsWith($appDir.FullName, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The portable application did not load vcruntime140.dll from its own folder.'
  }
  "Portable smoke test passed; runtime loaded from $($loadedRuntime.FileName)"
}
finally {
  if ($process -and -not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
  Remove-Item $temp -Recurse -Force -ErrorAction SilentlyContinue
}
