# THALAIVAR TV installer (Windows): backs up the TV, installs the Clean Your TV launcher with the Tamil
# translation and the Thalaivar Hub add-on, and pushes the THALAIVAR theme. It does NOT make the launcher
# your Home: whatever is Home now stays Home until you run make-home.ps1 yourself.
#
#   powershell -ExecutionPolicy Bypass -File tools\thalaivar\install.ps1 -TvHost 192.168.1.50
#   ... -Motion low|medium|high      animation level for the launcher theme (default medium)
#   ... -OriginalArtOnly              push themes\thalaivar (original art) instead of your local poster theme
#   ... -SkipBuild                    install the APKs already built
#
# Undo everything with tools\thalaivar\restore.ps1.
param(
    [string]$TvHost = $env:TV_HOST,
    [ValidateSet('low', 'medium', 'high')][string]$Motion = 'medium',
    [switch]$OriginalArtOnly,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$Root = (Resolve-Path "$PSScriptRoot\..\..").Path
$Py = "$Root\tools\venv\Scripts\python.exe"
$Launcher = 'com.gathin.tvhome'
$Hub = 'tv.thalaivar.hub'
if (-not $TvHost) { throw 'Pass -TvHost <tv ip> or set $env:TV_HOST.' }
$env:TV_HOST = $TvHost

function Step($n, $s) { Write-Host "`n[$n/8] $s" -ForegroundColor Yellow }
function Tv([string]$cmd) {
    $out = & $Py -I "$Root\tools\tv.py" $cmd 2>&1
    if ($LASTEXITCODE -ne 0) { throw "TV command failed: $cmd`n$out" }
    ($out | Out-String).TrimEnd()
}
function Install-Apk($apk, $name) {
    & $Py -I "$Root\tools\tv.py" --push $apk "/data/local/tmp/$name.apk" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "push of $name failed" }
    $r = Tv "pm install -r /data/local/tmp/$name.apk; rm -f /data/local/tmp/$name.apk"
    if ($r -notmatch 'Success') { throw "pm install of $name failed:`n$r" }
    Write-Host "  installed $name"
}

Step 1 'Python tools'
if (-not (Test-Path $Py)) {
    python -m venv "$Root\tools\venv"
    & $Py -m pip install -q -r "$Root\tools\requirements.txt"
}

Step 2 'Device detection and ADB test'
$probe = & $Py -I "$Root\tools\tv.py" --probe 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) { throw "Could not reach $TvHost over ADB. Turn on Network debugging on the TV.`n$probe" }
$sdk = [int](([regex]::Match($probe, 'ro.build.version.sdk = (\d+)')).Groups[1].Value)
$model = ([regex]::Match($probe, 'ro.product.model = (.+)')).Groups[1].Value.Trim()
Write-Host "  $model, Android API $sdk"
if ($sdk -lt 26) { throw "THALAIVAR TV needs Android 8.0 (API 26) or newer; this TV is API $sdk." }

Step 3 'Backup (packages, Home, screensaver, accessibility helpers)'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$bk = "$Root\backup\thalaivar-$stamp"
New-Item -ItemType Directory -Force $bk | Out-Null
$homeQuery = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
$homeNow = ((Tv $homeQuery) -split "`n")[-1].Trim()
Set-Content "$bk\home.txt" $homeNow -Encoding utf8
Tv 'pm list packages' | Set-Content "$bk\packages.txt" -Encoding utf8
Tv 'pm list packages -d' | Set-Content "$bk\disabled.txt" -Encoding utf8
foreach ($k in 'enabled_accessibility_services', 'screensaver_components', 'screensaver_enabled', 'accessibility_enabled') {
    "$k=" + (Tv "settings get secure $k") | Add-Content "$bk\settings.txt" -Encoding utf8
}
Tv 'getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.display.id' | Set-Content "$bk\device.txt" -Encoding utf8
Write-Host "  saved in $bk  (Home is $homeNow)"

Step 4 'Build'
if (-not $SkipBuild) {
    foreach ($p in "$Root\launcher", "$Root\addons\thalaivar-hub") {
        if (-not (Test-Path "$p\local.properties")) { "sdk.dir=$($env:ANDROID_HOME -replace '\\','/')" | Set-Content "$p\local.properties" -Encoding ascii }
        Push-Location $p
        & .\gradlew.bat --no-daemon -q assembleRelease --console=plain
        $code = $LASTEXITCODE
        Pop-Location
        if ($code -ne 0) { throw "build failed in $p" }
    }
}
$secrets = "$Root\addons\thalaivar-hub\secrets.properties"
if (-not ((Test-Path $secrets) -and (Select-String -Quiet -Path $secrets -Pattern '^tmdb\.token=.+'))) {
    Write-Warning 'addons\thalaivar-hub\secrets.properties has no TMDB token: the hub will show its art and news but no film data.'
}

Step 5 'Install the launcher and Thalaivar Hub (alongside your current Home)'
Install-Apk "$Root\launcher\app\build\outputs\apk\release\app-release.apk" 'cyt-launcher'
Install-Apk "$Root\addons\thalaivar-hub\app\build\outputs\apk\release\app-release.apk" 'thalaivar-hub'
# A second Home app can make Android ask which Home to use; keep the one the TV had.
$after = ((Tv $homeQuery) -split "`n")[-1].Trim()
if ($after -ne $homeNow -and $homeNow -match '/') {
    Tv "cmd package set-home-activity --user 0 $homeNow" | Out-Null
    $after = ((Tv $homeQuery) -split "`n")[-1].Trim()
}
Write-Host "  Home is still: $after"
# English, with the theme's own Cinzel-style headings; the launcher also ships a Tamil translation (values-ta)
# for anyone who wants it:  tools\tv.py "cmd locale set-app-locales com.gathin.tvhome --locales ta"
Tv "cmd locale set-app-locales $Launcher --locales en" | Out-Null

Step 6 'Push the THALAIVAR theme'
$theme = "$Root\themes-local\thalaivar-posters"
if ($OriginalArtOnly -or -not (Test-Path "$theme\config.json")) { $theme = "$Root\themes\thalaivar" }
$cfg = Get-Content "$theme\config.json" -Raw -Encoding utf8 | ConvertFrom-Json
switch ($Motion) {
    'low' { $cfg.reduced_motion = $true; $cfg.advanced_effects = $false; $cfg.fade_rows = $false; $cfg | Add-Member -Force glow_snap $true }
    'medium' { $cfg.reduced_motion = $false; $cfg.advanced_effects = $false }
    'high' { $cfg.reduced_motion = $false; $cfg.advanced_effects = $true }
}
$stage = "$Root\themes-local\.push"
if (Test-Path $stage) { Get-ChildItem $stage -Recurse -File | ForEach-Object { [IO.File]::Delete($_.FullName) } }
New-Item -ItemType Directory -Force "$stage\wallpapers" | Out-Null
Copy-Item "$theme\wallpaper.jpg" $stage
Copy-Item "$theme\wallpapers\*" "$stage\wallpapers\"
[IO.File]::WriteAllText("$stage\config.json", ($cfg | ConvertTo-Json -Depth 5), (New-Object System.Text.UTF8Encoding $false))
& powershell -NoProfile -ExecutionPolicy Bypass -File "$Root\tools\theme.ps1" -TvHost $TvHost $stage
if ($LASTEXITCODE -ne 0) { throw 'theme push failed' }

Step 7 'Verify'
$pk = Tv "pm list packages $Launcher; pm list packages $Hub"
foreach ($p in $Launcher, $Hub) { if ($pk -notmatch [regex]::Escape("package:$p")) { throw "$p is not installed" } }
$start = Tv "am start -W -n $Hub/.HubActivity"
if ($start -notmatch 'Status: ok') { throw "Thalaivar Hub did not start:`n$start" }
Write-Host '  both apps installed; Thalaivar Hub starts'

Step 8 'Done'
Write-Host @"
  - Thalaivar Hub: Apps on your TV > Thalaivar TV (or the card on the THALAIVAR launcher).
  - Try the launcher without changing Home:  tools\venv\Scripts\python.exe -I tools\tv.py "am start -n $Launcher/.MainActivity"
  - Screensavers: TV Settings > Screen saver > Thalaivar TV, or Baasha - The Evergreen Emperor.
  - Make it your Home only when you are happy:  tools\thalaivar\make-home.ps1 -TvHost $TvHost
  - Undo everything:  tools\thalaivar\restore.ps1 -TvHost $TvHost   (backup: $bk)
"@
