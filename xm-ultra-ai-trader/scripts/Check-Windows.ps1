[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'Common.ps1')
Set-Location -LiteralPath $ProjectRoot
$python = Get-ProjectPython
& $python (Join-Path $PSScriptRoot 'diagnostics.py')
if ($LASTEXITCODE -ne 0) { throw 'Diagnostics could not run.' }
