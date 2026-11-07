$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$loopDirectory = Join-Path $env:LOCALAPPDATA 'LoopSecure'
$loopCache = Join-Path $loopDirectory 'adb-path.txt'
[IO.Directory]::CreateDirectory($loopDirectory) | Out-Null
function Test-LoopAdb([string]$candidate) {
    if ([string]::IsNullOrWhiteSpace($candidate) -or !(Test-Path -LiteralPath $candidate -PathType Leaf)) { return $false }
    try {
        $loopVersionOutput = @(& $candidate version 2>$null) -join ' '
        if ($LASTEXITCODE -ne 0 -or $loopVersionOutput -notmatch 'Android Debug Bridge version (?<protocol>[0-9]+\.[0-9]+\.[0-9]+)') { return $false }
        return ([version]$Matches.protocol -ge [version]'1.0.41')
    } catch { return $false }
}
$candidates = @()
if (Test-Path -LiteralPath $loopCache) { $candidates += [IO.File]::ReadAllText($loopCache).Trim() }
$candidates += @((Join-Path $PSScriptRoot 'adb.exe'), (Join-Path $loopDirectory 'platform-tools\adb.exe'))
$command = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($command) { $candidates += $command.Source }
foreach ($base in @('Downloads','Documents','Desktop')) {
    $folder = Join-Path $env:USERPROFILE $base
    $candidates += (Join-Path $folder 'platform-tools\adb.exe')
    if (Test-Path -LiteralPath $folder) {
        Get-ChildItem -LiteralPath $folder -Directory -Filter '*platform-tools*' -ErrorAction SilentlyContinue | ForEach-Object {
            $candidates += (Join-Path $_.FullName 'adb.exe')
            $candidates += (Join-Path $_.FullName 'platform-tools\adb.exe')
        }
    }
}
$candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
$adb = $candidates | Where-Object { Test-LoopAdb $_ } | Select-Object -First 1
if (!$adb) {
    Write-Host 'Preparing official Google USB tools once for this laptop...'
    [xml]$repository = (Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/repository2-1.xml' -TimeoutSec 60).Content
    $package = @($repository.SelectNodes("//*[local-name()='remotePackage' and @path='platform-tools']")) | Where-Object { $_.SelectSingleNode("*[local-name()='channelRef' and @ref='channel-0']") } | Select-Object -First 1
    if (!$package) { throw 'Official stable Google platform-tools metadata was not found.' }
    $archive = $package.SelectSingleNode("*[local-name()='archives']/*[local-name()='archive'][*[local-name()='host-os']='windows']/*[local-name()='complete']")
    if (!$archive) { throw 'Official Windows USB tools were not found.' }
    $file = $archive.SelectSingleNode("*[local-name()='url']").InnerText
    $expected = $archive.SelectSingleNode("*[local-name()='checksum']").InnerText.ToLowerInvariant()
    $size = [long]$archive.SelectSingleNode("*[local-name()='size']").InnerText
    if ($file -notmatch '^platform-tools_r[0-9.]+-win\.zip$' -or $size -lt 1000000 -or $size -gt 40000000) { throw 'Unexpected Google USB download metadata.' }
    $algorithm = if ($expected -match '^[0-9a-f]{64}$') { 'SHA256' } elseif ($expected -match '^[0-9a-f]{40}$') { 'SHA1' } else { throw 'Unsupported published Google checksum.' }
    $download = Join-Path $loopDirectory ('google-tools-' + [Guid]::NewGuid().ToString('N') + '.zip')
    try {
        Invoke-WebRequest -UseBasicParsing -Uri ('https://dl.google.com/android/repository/' + $file) -OutFile $download -TimeoutSec 180
        if ((Get-Item -LiteralPath $download).Length -ne $size -or (Get-FileHash -LiteralPath $download -Algorithm $algorithm).Hash.ToLowerInvariant() -ne $expected) { throw 'The Google USB tools download did not match its published checksum.' }
        Expand-Archive -LiteralPath $download -DestinationPath $loopDirectory -Force
    } finally { Remove-Item -LiteralPath $download -Force -ErrorAction SilentlyContinue }
    $adb = Join-Path $loopDirectory 'platform-tools\adb.exe'
    if (!(Test-LoopAdb $adb)) { throw 'The official Google USB tools could not start on this Windows laptop.' }
}
[IO.File]::WriteAllText($loopCache,$adb,[Text.UTF8Encoding]::new($false))
Write-Host 'USB tools ready. Keep one phone connected and approve USB debugging on the phone.'
