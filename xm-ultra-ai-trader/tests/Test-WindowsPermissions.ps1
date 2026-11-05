# Run on Windows as an ordinary (non-administrator) user.
$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'scripts\Common.ps1')
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('bc-acl-' + [Guid]::NewGuid().ToString('N'))
try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null
    $sections = [System.Security.AccessControl.AccessControlSections]
    $beforeOwner = [System.IO.Directory]::GetAccessControl($testRoot, $sections::Owner).GetOwner([System.Security.Principal.SecurityIdentifier]).Value
    Protect-LocalDirectory $testRoot
    $acl = [System.IO.Directory]::GetAccessControl($testRoot, $sections::Access)
    if (-not $acl.AreAccessRulesProtected) { throw 'DACL inheritance was not disabled.' }
    $current = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $rules = @($acl.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier]))
    if ($rules.Count -ne 2) { throw 'Unexpected access entries.' }
    foreach ($rule in $rules) {
        if ($rule.IdentityReference.Value -notin @($current,'S-1-5-18')) { throw 'Unexpected identity.' }
        if ($rule.AccessControlType -ne 'Allow' -or $rule.FileSystemRights -ne 'FullControl') { throw 'Unexpected access rights.' }
    }
    $sample = Join-Path $testRoot 'sample.txt'
    [System.IO.File]::WriteAllText($sample,'retained')
    Protect-LocalDirectory $testRoot
    if ([System.IO.File]::ReadAllText($sample) -ne 'retained') { throw 'Reapplication damaged data.' }
    $afterOwner = [System.IO.Directory]::GetAccessControl($testRoot,$sections::Owner).GetOwner([System.Security.Principal.SecurityIdentifier]).Value
    if ($beforeOwner -ne $afterOwner) { throw 'Directory ownership changed.' }
    Write-Host 'PASS: access-only permissions, ordinary-user write access, idempotency and owner preservation.'
} finally {
    if (Test-Path -LiteralPath $testRoot) { Remove-Item -LiteralPath $testRoot -Recurse -Force }
}
