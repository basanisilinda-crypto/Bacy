[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'Common.ps1')
Set-Location -LiteralPath $ProjectRoot
$python = Get-ProjectPython
& $python (Join-Path $PSScriptRoot 'serve.py')
if ($LASTEXITCODE -ne 0) { throw 'Dashboard stopped with an error. Check the message above; do not repeatedly resume trading.' }
