[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*\.zip$')]
    [string]$Name = ('backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
)
. (Join-Path $PSScriptRoot 'Common.ps1')
Set-Location -LiteralPath $ProjectRoot
$python = Get-ProjectPython
$backupDirectory = Join-Path $ProjectRoot 'backups'
Protect-LocalDirectory $backupDirectory
& $python (Join-Path $PSScriptRoot 'backup.py') create (Join-Path $backupDirectory $Name)
if ($LASTEXITCODE -ne 0) { throw 'Backup failed.' }
