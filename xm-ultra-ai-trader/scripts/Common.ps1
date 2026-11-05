Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot

function Protect-LocalDirectory {
    param([Parameter(Mandatory = $true)][string]$Path)
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
    $directory = Get-Item -LiteralPath $Path -Force
    if ($directory.Attributes -band [System.IO.FileAttributes]::ReparsePoint) {
        throw 'Private application folders cannot be symbolic links or junctions.'
    }
    $currentSid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    # Construct an access-only descriptor. Get-Acl/Set-Acl can carry audit or
    # owner metadata and request SeSecurityPrivilege on ordinary Windows users.
    # Only the DACL is marked modified; SACL, owner and group are untouched.
    $acl = [System.Security.AccessControl.DirectorySecurity]::new()
    $dacl = 'D:P(A;OICI;FA;;;' + $currentSid + ')(A;OICI;FA;;;S-1-5-18)'
    $acl.SetSecurityDescriptorSddlForm($dacl, [System.Security.AccessControl.AccessControlSections]::Access)
    try {
        [System.IO.Directory]::SetAccessControl($directory.FullName, $acl)
    } catch {
        throw ('Cannot secure the private application folder "' + $directory.FullName + '". Keep the extracted project in a folder owned by your Windows user. No passwords were saved by this step. Details: ' + $_.Exception.Message)
    }
}

function Get-ProjectPython {
    $python = Join-Path $ProjectRoot '.venv\Scripts\python.exe'
    if (-not (Test-Path -LiteralPath $python)) {
        throw 'Run Setup-Windows.ps1 first. The project Python environment is missing.'
    }
    return $python
}
