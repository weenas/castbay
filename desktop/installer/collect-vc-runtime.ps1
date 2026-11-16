# Stage the licensed x64 MSVC runtime DLLs required by CastBay Desktop for app-local deployment.
# Usage: collect-vc-runtime.ps1 -OutDir <directory>
param([Parameter(Mandatory)] [string] $OutDir)
$ErrorActionPreference = 'Stop'

$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
if (-not (Test-Path $vswhere)) { throw "vswhere.exe was not found: $vswhere" }
$vsPath = & $vswhere -latest -products '*' -property installationPath
if (-not $vsPath -or $LASTEXITCODE -ne 0) { throw 'Visual Studio installation was not found.' }

$redistRoot = Join-Path $vsPath 'VC\Redist\MSVC'
if (-not (Test-Path $redistRoot)) { throw "MSVC redistributable directory was not found: $redistRoot" }
$runtimeDirs = Get-ChildItem $redistRoot -Directory |
  Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
  ForEach-Object { Join-Path $_.FullName 'x64\Microsoft.VC143.CRT' }
$runtimeDir = $runtimeDirs | Where-Object { Test-Path (Join-Path $_ 'vcruntime140.dll') } | Select-Object -First 1
if (-not $runtimeDir) { throw 'The x64 Microsoft.VC143.CRT app-local runtime was not found.' }

New-Item -ItemType Directory -Force $OutDir | Out-Null
Copy-Item (Join-Path $runtimeDir 'vcruntime140.dll') (Join-Path $OutDir 'vcruntime140.dll') -Force
$file = Get-Item (Join-Path $OutDir 'vcruntime140.dll')
$signature = Get-AuthenticodeSignature $file.FullName
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notlike '*Microsoft Corporation*') {
  throw "The staged runtime DLL does not have a valid Microsoft signature: $($signature.Status)"
}
"Staged $($file.Name), $($file.Length) bytes, signer: $($signature.SignerCertificate.Subject)"
Get-FileHash $file.FullName -Algorithm SHA256 | Select-Object Algorithm, Hash
