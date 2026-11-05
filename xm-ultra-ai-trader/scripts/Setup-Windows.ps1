[CmdletBinding()]
param(
    [string]$BridgeDir = (Join-Path $env:APPDATA 'MetaQuotes\Terminal\Common\Files\XMUltraAI'),
    [switch]$EnablePassword,
    [switch]$ChangePassword
)
. (Join-Path $PSScriptRoot 'Common.ps1')
if (-not [Environment]::Is64BitOperatingSystem) { throw 'Use a supported 64-bit Windows installation for MT5 and Python.' }
Set-Location -LiteralPath $ProjectRoot
foreach ($directory in @('data', 'config')) { Protect-LocalDirectory (Join-Path $ProjectRoot $directory) }
Protect-LocalDirectory $BridgeDir
if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot '.venv\Scripts\python.exe'))) {
    $launcher = Get-Command py -ErrorAction SilentlyContinue
    if ($launcher) { & py -3 -c 'import sys; assert sys.version_info >= (3,11), "Python 3.11 or newer is required"'; if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 or newer from python.org.' }; & py -3 -m venv .venv }
    else { & python -c 'import sys; assert sys.version_info >= (3,11), "Python 3.11 or newer is required"'; if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 or newer from python.org.' }; & python -m venv .venv }
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the local Python environment.' }
}
$python = Get-ProjectPython
& $python -m pip install -e .
if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Check your internet connection and supported Python version.' }
$setupArguments = @((Join-Path $PSScriptRoot 'setup_local.py'), '--bridge-dir', $BridgeDir)
if ($EnablePassword) { $setupArguments += '--enable-password' }
if ($ChangePassword) { $setupArguments += '--change-password' }
& $python @setupArguments
if ($LASTEXITCODE -ne 0) { throw 'Local configuration failed.' }
Write-Host 'Setup finished. No broker login, account, deposit, or trading action was performed.'
