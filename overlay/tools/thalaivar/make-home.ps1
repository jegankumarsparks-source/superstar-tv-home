# Makes the THALAIVAR launcher (com.gathin.tvhome) the TV's Home, after you have tried it. It never disables
# the stock launcher: Home is only pointed at THALAIVAR, and restore.ps1 points it back.
#
#   powershell -ExecutionPolicy Bypass -File tools\thalaivar\make-home.ps1 -TvHost 192.168.1.50
param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path "$PSScriptRoot\..\..").Path
$Py = "$Root\tools\venv\Scripts\python.exe"
if (-not $TvHost) { throw 'Pass -TvHost <tv ip> or set $env:TV_HOST.' }
$env:TV_HOST = $TvHost
if (-not (Get-ChildItem "$Root\backup" -Directory -Filter 'thalaivar-*' -ErrorAction SilentlyContinue)) {
    throw 'No backup yet: run install.ps1 first, so restore.ps1 can put Home back.'
}
$answer = Read-Host 'Make THALAIVAR TV the Home screen? restore.ps1 undoes it. Type YES'
if ($answer -ne 'YES') { Write-Host 'Nothing changed.'; exit 0 }
$q = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
& $Py -I "$Root\tools\tv.py" "cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity"
$now = (& $Py -I "$Root\tools\tv.py" $q | Select-Object -Last 1).Trim()
if ($now -like 'com.gathin.tvhome/*') {
    Write-Host "Home is now THALAIVAR TV ($now). Press Home on the remote to check."
} else {
    Write-Warning "Home resolves to '$now', not THALAIVAR TV. On some Google TVs the setup wizard or another app's Home-button helper takes Home; nothing was disabled, so the TV works as before."
}
