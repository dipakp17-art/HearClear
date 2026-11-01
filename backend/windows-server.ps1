[CmdletBinding()]
param(
    [ValidateSet('setup','start','report','approve','reject')]
    [string]$Action = 'start',
    [string]$Order,
    [int]$Amount,
    [string]$Reference,
    [switch]$SettlementChecked
)
$ErrorActionPreference = 'Stop'
$serverRoot = $PSScriptRoot
$stateRoot = Join-Path $env:LOCALAPPDATA 'HearClearServer'
$venvRoot = Join-Path $stateRoot 'venv'
$serverPython = Join-Path $venvRoot 'Scripts\python.exe'
$settingsFile = Join-Path $stateRoot 'settings.json'
$dbFile = Join-Path $stateRoot 'hearclear.sqlite3'
if ($Action -eq 'setup') {
    New-Item -ItemType Directory -Force -Path $stateRoot | Out-Null
    if (-not (Test-Path -LiteralPath $serverPython)) {
        if (Get-Command py -ErrorAction SilentlyContinue) {
            & py -3 -m venv $venvRoot
        } elseif (Get-Command python -ErrorAction SilentlyContinue) {
            & python -m venv $venvRoot
        } else {
            throw 'Install Python 3.11 or newer, reopen PowerShell, then run setup again.'
        }
        if ($LASTEXITCODE -ne 0) { throw 'Python virtual environment creation failed.' }
    }
    & $serverPython -c 'import sys; assert sys.version_info >= (3,11)'
    if ($LASTEXITCODE -ne 0) { throw 'Install Python 3.11 or newer before continuing.' }
    & $serverPython -m pip install -r (Join-Path $serverRoot 'requirements.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Dependency installation failed. Check internet and run setup again.' }
    if (-not (Test-Path -LiteralPath $settingsFile)) {
        # Public receiving details supplied by the owner; no UPI PIN/bank login is stored.
        @{ upi_id = '9867918513@axl'; payee = 'deepk parmar' } |
            ConvertTo-Json | Set-Content -LiteralPath $settingsFile -Encoding UTF8
    }
    Write-Host "Setup complete. Private server data is in $stateRoot"
    Write-Host 'Approval hours: 10:00 AM to 5:00 PM India time, every day.'
    Write-Host 'Next: run this script with -Action start. Keep its window open.'
    Write-Host 'HTTPS phone access still needs a stable domain/reverse proxy or a configured named tunnel.'
    exit 0
}
if (-not (Test-Path -LiteralPath $serverPython) -or -not (Test-Path -LiteralPath $settingsFile)) {
    throw 'Run windows-server.ps1 -Action setup first.'
}
$config = Get-Content -LiteralPath $settingsFile -Raw | ConvertFrom-Json
$env:HEARCLEAR_DB = $dbFile
$env:HEARCLEAR_UPI_ID = $config.upi_id
$env:HEARCLEAR_PAYEE = $config.payee
$serverFile = Join-Path $serverRoot 'server.py'
$serverArgs = @($serverFile, $Action)
if ($Action -eq 'start') {
    $serverArgs = @($serverFile, 'serve')
    Write-Host 'Starting HearClear on 127.0.0.1:8787. Keep this window open; Ctrl+C stops it.'
    Write-Host 'The laptop must stay awake and connected. Paid/trial users need periodic server access.'
    Write-Host 'This local address is not yet a public HTTPS app endpoint.'
} elseif ($Action -eq 'approve') {
    if (-not $Order -or $Amount -notin @(50,100,150,200,250) -or -not $Reference -or -not $SettlementChecked) {
        throw 'Check bank settlement, then provide -Order, -Amount, -Reference and -SettlementChecked.'
    }
    $serverArgs += @('--order', $Order, '--amount', "$Amount", '--reference', $Reference, '--settlement-checked')
} elseif ($Action -eq 'reject') {
    if (-not $Order) { throw 'Provide -Order for the pending order to reject.' }
    $serverArgs += @('--order', $Order)
}
& $serverPython @serverArgs
if ($LASTEXITCODE -ne 0) { throw 'HearClear server command failed. See the message above.' }
