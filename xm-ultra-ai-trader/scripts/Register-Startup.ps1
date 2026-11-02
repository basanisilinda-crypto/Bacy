[CmdletBinding(SupportsShouldProcess = $true)]
param([switch]$Remove)
. (Join-Path $PSScriptRoot 'Common.ps1')
$taskName = 'XM Ultra AI local dashboard'
if ($Remove) {
    if ($PSCmdlet.ShouldProcess($taskName, 'Remove the current user logon task')) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue }
    return
}
$python = Get-ProjectPython
$scriptPath = Join-Path $PSScriptRoot 'serve.py'
$action = New-ScheduledTaskAction -Execute $python -Argument ('"' + $scriptPath + '"') -WorkingDirectory $ProjectRoot
$trigger = New-ScheduledTaskTrigger -AtLogOn -User ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name)
$principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable
if ($PSCmdlet.ShouldProcess($taskName, 'Run the dashboard at your Windows logon, with trading still paused')) {
    Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
    Write-Host 'Registered for this Windows user. MT5 must be started separately. Trading still requires a fresh authenticated start.'
}
