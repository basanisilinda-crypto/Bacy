[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'Common.ps1')
Add-Type -AssemblyName System.Windows.Forms
function Tell([string]$Text) {
    [void][System.Windows.Forms.MessageBox]::Show($Text, 'XM Ultra setup', 'OK', 'Information')
}
function Ask([string]$Text) {
    return [System.Windows.Forms.MessageBox]::Show($Text, 'XM Ultra setup', 'YesNo', 'Question') -eq 'Yes'
}
try {
    if (-not [Environment]::Is64BitOperatingSystem) { throw 'A supported 64-bit Windows installation is required.' }
    Set-Location -LiteralPath $ProjectRoot
    Write-Host 'Preparing your private dashboard. Trading stays paused.'
    $pythonReady = $false
    foreach ($candidate in @('py', 'python')) {
        if (Get-Command $candidate -ErrorAction SilentlyContinue) {
            if ($candidate -eq 'py') { & py -3 -c 'import sys; raise SystemExit(0 if sys.version_info >= (3,11) and sys.maxsize > 2**32 else 1)' }
            else { & python -c 'import sys; raise SystemExit(0 if sys.version_info >= (3,11) and sys.maxsize > 2**32 else 1)' }
            if ($LASTEXITCODE -eq 0) { $pythonReady = $true; break }
        }
    }
    if (-not $pythonReady) {
        if ((Get-Command winget -ErrorAction SilentlyContinue) -and (Ask 'Python 3.13 is needed. Install the free official Python package for this Windows user using Windows Package Manager? This accepts its package/source agreements and downloads Python.')) {
            & winget install --id Python.Python.3.13 --exact --source winget --scope user --accept-package-agreements --accept-source-agreements --disable-interactivity
            if ($LASTEXITCODE -ne 0) { throw 'Python installation did not finish. Install supported 64-bit Python from python.org, then double-click Start Here again.' }
            Tell 'Python has been installed. Close this window and double-click Start Here again so Windows can reload its command paths.'
            exit 0
        }
        Start-Process 'https://www.python.org/downloads/windows/'
        Tell 'Install 64-bit Python 3.11 or newer from the official page. Select Add Python to PATH. Then close this window and double-click Start Here again.'
        exit 0
    }
    $configFile = Join-Path $ProjectRoot 'config\local.json'
    $venvPython = Join-Path $ProjectRoot '.venv\Scripts\python.exe'
    $configured = (Test-Path -LiteralPath $configFile) -and (Test-Path -LiteralPath $venvPython)
    if (-not $configured) {
        Tell 'Local setup opens without a dashboard password. It runs only on this Windows computer. Phone/network access requires separate protected setup.'
        & (Join-Path $PSScriptRoot 'Setup-Windows.ps1')
    } else {
        & $venvPython -c 'import fastapi, uvicorn, numpy'
        if ($LASTEXITCODE -ne 0) {
            & $venvPython -m pip install -e $ProjectRoot
            if ($LASTEXITCODE -ne 0) { throw 'Dependency repair failed. Check the connection and try again.' }
        }
    }
    $terminalRoot = Join-Path $env:APPDATA 'MetaQuotes\Terminal'
    $terminals = @(Get-ChildItem -LiteralPath $terminalRoot -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'MQL5\Experts') })
    $terminalFolder = $null
    if ($terminals.Count -eq 1) { $terminalFolder = $terminals[0].FullName }
    elseif ($terminals.Count -gt 1) {
        Tell 'Several MT5 data folders were found. In the intended XM MT5, use File > Open Data Folder, then select that same folder in the next window.'
        $picker = New-Object System.Windows.Forms.FolderBrowserDialog
        $picker.Description = 'Choose the intended XM MT5 data folder containing MQL5'
        $picker.SelectedPath = $terminalRoot
        if ($picker.ShowDialog() -eq 'OK') { $terminalFolder = $picker.SelectedPath }
    }
    if ($terminalFolder -and (Test-Path -LiteralPath (Join-Path $terminalFolder 'MQL5\Experts'))) {
        $destination = Join-Path $terminalFolder 'MQL5\Experts\XMUltraAI'
        New-Item -ItemType Directory -Force -Path $destination | Out-Null
        $sourceFile = Join-Path $ProjectRoot 'mt5\XMUltraAITrader.mq5'
        $targetFile = Join-Path $destination 'XMUltraAITrader.mq5'
        $install = $true
        if (Test-Path -LiteralPath $targetFile) {
            $same = (Get-FileHash -LiteralPath $sourceFile).Hash -eq (Get-FileHash -LiteralPath $targetFile).Hash
            $install = -not $same
            if ($install -and -not (Ask 'The installed EA source differs. Before updating, pause the robot and remove it from its MT5 chart. Continue with a backed-up source replacement? This does not attach or start the robot.')) { $install = $false }
            elseif ($install) { Copy-Item -LiteralPath $targetFile -Destination ($targetFile + '.backup-' + (Get-Date -Format 'yyyyMMddHHmmss')) }
        }
        if ($install) { Copy-Item -LiteralPath $sourceFile -Destination $targetFile -Force }
        $editor = $null
        $origin = Join-Path $terminalFolder 'origin.txt'
        if (Test-Path -LiteralPath $origin) {
            $installDirectory = (Get-Content -LiteralPath $origin -Raw).Trim()
            $candidate = Join-Path $installDirectory 'metaeditor64.exe'
            if (Test-Path -LiteralPath $candidate) { $editor = $candidate }
        }
        if ($editor -and ($install -or $same)) {
            $log = Join-Path $destination 'compile.log'
            if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log }
            Start-Process -FilePath $editor -ArgumentList @(('/compile:"' + $targetFile + '"'), ('/log:"' + $log + '"')) -Wait
            $compiled = $false
            if (Test-Path -LiteralPath $log) {
                $compiled = [bool]((Get-Content -LiteralPath $log -Raw) -match '(?i)\b0 errors?\b') -and (Test-Path -LiteralPath ($targetFile -replace '\.mq5$', '.ex5'))
            }
            if ($compiled) { Tell 'MetaEditor reports zero errors and a compiled EA file exists. This is compilation only, not a broker trading test. Open XM MT5, log into DEMO, refresh Navigator > Expert Advisors and attach XMUltraAITrader to one chart. Keep Algo Trading off until checks are complete.' }
            else { Tell 'Automatic compilation could not be confirmed. Open XMUltraAITrader.mq5 in MetaEditor and press F7. Share the compiler errors if any. Do not start trading. The dashboard can still open.' }
        } else { Tell 'Robot source is available in your MT5 data folder. Automatic compilation was skipped. Open XMUltraAITrader.mq5 in MetaEditor and press F7 before attaching it. Trading remains off.' }
    } else {
        Tell 'An MT5 data folder was not selected. Install/open XM MT5 and log into DEMO first. You can still open the dashboard, but it will remain disconnected. Run Start Here again after MT5 is installed.'
    }
    # No credentials in process arguments, no auto-login, no start/resume controls.
    $runner = Start-Process -FilePath $venvPython -ArgumentList @('"' + (Join-Path $PSScriptRoot 'serve.py') + '"') -WorkingDirectory $ProjectRoot -PassThru
    & $venvPython (Join-Path $PSScriptRoot 'open_dashboard.py') --process-id $runner.Id
    if ($LASTEXITCODE -ne 0) { throw 'The dashboard service did not become ready. Read its window for the error. No trade was started.' }
    Write-Host 'Dashboard opened. Keep the service window open. Verify the MT5 demo account and connection before requesting any trade.'
} catch {
    Write-Host ('Setup stopped: ' + $_.Exception.Message) -ForegroundColor Red
    Tell 'Setup stopped. Read the error in the setup window and share only that error text, never your password or bridge secret.'
    exit 1
}
