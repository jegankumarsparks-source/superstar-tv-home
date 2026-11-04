# THALAIVAR TV restore (Windows): puts Home, the screensaver and the accessibility helpers back as the
# installer's backup recorded them, then removes the two apps THALAIVAR TV added. Nothing else is touched.
#
#   powershell -ExecutionPolicy Bypass -File tools\thalaivar\restore.ps1 -TvHost 192.168.1.50
#   ... -Backup backup\thalaivar-20261104-150000    a specific backup (default: the newest)
#   ... -KeepApps                                    restore Home and settings, keep the apps installed
#   ... -DryRun                                      print what would run, change nothing
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Backup,
    [switch]$KeepApps,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$Root = (Resolve-Path "$PSScriptRoot\..\..").Path
$Py = "$Root\tools\venv\Scripts\python.exe"
if (-not $TvHost) { throw 'Pass -TvHost <tv ip> or set $env:TV_HOST.' }
$env:TV_HOST = $TvHost
if (-not $Backup) {
    $Backup = (Get-ChildItem "$Root\backup" -Directory -Filter 'thalaivar-*' | Sort-Object Name | Select-Object -Last 1).FullName
}
if (-not $Backup -or -not (Test-Path "$Backup\home.txt")) { throw 'No THALAIVAR backup found; run install.ps1 first or pass -Backup.' }
Write-Host "Restoring from $Backup"

function Run([string]$cmd) {
    if ($DryRun) { Write-Host "  would run: $cmd"; return '' }
    $out = & $Py -I "$Root\tools\tv.py" $cmd 2>&1 | Out-String
    Write-Host "  $cmd`n    $($out.Trim())"
    $out
}

$homeWas = (Get-Content "$Backup\home.txt" -Raw).Trim()
$settings = @{}
foreach ($line in Get-Content "$Backup\settings.txt") {
    $k, $v = $line -split '=', 2
    $settings[$k] = $v.Trim()
}

# 1. Home back to what it was (the stock launcher is never disabled by THALAIVAR TV, so this always works)
if ($homeWas -match '/') { Run "cmd package set-home-activity --user 0 $homeWas" | Out-Null }
# 2. Screensaver and accessibility helpers back to their recorded values
foreach ($k in 'screensaver_components', 'enabled_accessibility_services') {
    $v = $settings[$k]
    if ($v -and $v -ne 'null') { Run "settings put secure $k '$v'" | Out-Null } else { Run "settings delete secure $k" | Out-Null }
}
# 3. The two apps THALAIVAR TV added (only these two, never anything that came with the TV)
if (-not $KeepApps) {
    $had = Get-Content "$Backup\packages.txt" -Raw
    foreach ($p in 'tv.thalaivar.hub', 'com.gathin.tvhome') {
        if ($had -match [regex]::Escape("package:$p`r?`n")) { Write-Host "  $p was there before THALAIVAR TV: kept"; continue }
        Run "pm uninstall --user 0 $p" | Out-Null
    }
}
$now = Run 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
if (-not $DryRun) { Write-Host "Home now resolves to: $((($now -split "`n") | Where-Object { $_ -match '/' } | Select-Object -Last 1))" }
Write-Host 'Restore finished.'
