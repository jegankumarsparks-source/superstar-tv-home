# Superstar TV Home (THALAIVAR TV edition) - one-command setup for Windows.
#
# Downloads Clean Your TV from its official repository, adds the THALAIVAR theme, the Thalaivar Hub app,
# the Tamil launcher strings and the install/restore tools on top, then builds and installs on your TV.
# Your current Home screen is NOT replaced; you decide that later with make-home.ps1.
#
#   powershell -ExecutionPolicy Bypass -File setup.ps1 -TvHost 192.168.1.50
#   ... -BuildOnly                          build the two apps, don't touch any TV
#   ... -Latitude 13.0827 -Longitude 80.2707   your city, for sunrise/sunset wallpapers (default Chennai)
#   ... -Motion low|medium|high              launcher animation level
#
# Needs: Git, Python 3, JDK 17+ and the Android SDK (platform 34). See docs/GUIDE.md.
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$BuildOnly,
    [string]$Latitude = '13.0827',
    [string]$Longitude = '80.2707',
    [ValidateSet('low', 'medium', 'high')][string]$Motion = 'medium'
)
$ErrorActionPreference = 'Stop'
$Here = $PSScriptRoot
$Cyt = Join-Path $Here 'cleanyourtv'
# The Clean Your TV commit this edition was built and tested against.
$Upstream = 'https://github.com/Gathin23/cleanyourtv.git'
$Pinned = '8672e68e8a24c1258e4662cd11daa4ed13795dad'

function Need($exe, $hint) {
    if (-not (Get-Command $exe -ErrorAction SilentlyContinue)) { throw "$exe not found. $hint" }
}
Write-Host "`n  SUPERSTAR TV HOME  -  THALAIVAR TV edition`n" -ForegroundColor Yellow
Need git 'Install it: winget install Git.Git'
Need python 'Install it: winget install Python.Python.3.12'
Need java 'Install a JDK 17 or newer: winget install Microsoft.OpenJDK.17'
if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    $guess = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $guess) { $env:ANDROID_HOME = $guess } else { throw 'Android SDK not found: install Android Studio, or set ANDROID_HOME to your SDK folder. See docs/GUIDE.md.' }
}

Write-Host '[1/5] Clean Your TV (official repository)'
# Only the one tested version, not the whole history: a far smaller download, retried because some home
# networks reset long transfers half way.
if (-not (Test-Path "$Cyt\.git")) {
    git init --quiet $Cyt
    git -C $Cyt remote add origin $Upstream
}
$ok = $false
foreach ($try in 1..3) {
    git -C $Cyt -c http.lowSpeedLimit=1000 -c http.lowSpeedTime=60 fetch --quiet --depth 1 origin $Pinned
    if ($LASTEXITCODE -eq 0) { $ok = $true; break }
    Write-Warning "download interrupted (try $try of 3), retrying..."
    Start-Sleep -Seconds (5 * $try)
}
if (-not $ok) { throw 'Could not download Clean Your TV from GitHub. Check the internet connection and run setup again; it continues where it stopped.' }
git -C $Cyt -c advice.detachedHead=false checkout --quiet --force $Pinned
if ($LASTEXITCODE -ne 0) { throw "could not check out Clean Your TV $Pinned" }

Write-Host '[2/5] Adding THALAIVAR TV'
Copy-Item -Recurse -Force "$Here\overlay\*" $Cyt
$cfg = "$Cyt\launcher\app\src\main\res\values\config.xml"
$x = [IO.File]::ReadAllText($cfg)
$x = [regex]::Replace($x, '<string name="latitude" translatable="false">[^<]*</string>', "<string name=""latitude"" translatable=""false"">$Latitude</string>")
$x = [regex]::Replace($x, '<string name="longitude" translatable="false">[^<]*</string>', "<string name=""longitude"" translatable=""false"">$Longitude</string>")
[IO.File]::WriteAllText($cfg, $x)
$loc = "$Cyt\launcher\app\src\main\res\xml\locales_config.xml"
$l = [IO.File]::ReadAllText($loc)
if ($l -notmatch '"ta"') { [IO.File]::WriteAllText($loc, $l.Replace('</locale-config>', "    <locale android:name=""ta"" />`n</locale-config>")) }
$ign = "$Cyt\.gitignore"
if (-not (Select-String -Quiet -Path $ign -Pattern '^themes-local/')) { Add-Content $ign "`nthemes-local/`naddons/thalaivar-hub/secrets.properties" }

Write-Host '[3/5] TMDB token (free: themoviedb.org/settings/api)'
$sec = "$Cyt\addons\thalaivar-hub\secrets.properties"
if (-not ((Test-Path $sec) -and (Select-String -Quiet -Path $sec -Pattern '^tmdb\.token=.+'))) {
    $tok = $env:TMDB_TOKEN
    if (-not $tok) {
        $s = Read-Host 'Paste your TMDB API Read Access Token (input hidden; Enter to skip)' -AsSecureString
        $tok = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
    }
    if ($tok) { Set-Content $sec "tmdb.token=$tok`nregion=IN" -Encoding ascii; Write-Host '  saved (git-ignored, stays on this computer)' }
    else { Write-Warning 'No token: the hub will show its own art and the news, but no films.' }
}
# Optional keys, each asked once and stored in the same git-ignored file
function Ask-Key($name, $prompt, $envName) {
    if ((Test-Path $sec) -and (Select-String -Quiet -Path $sec -Pattern "^$([regex]::Escape($name))=.+")) { return }
    $v = [Environment]::GetEnvironmentVariable($envName)
    if (-not $v) {
        $s = Read-Host "$prompt (input hidden; Enter to skip)" -AsSecureString
        $v = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
    }
    if ($v) { Add-Content $sec "$name=$v" -Encoding ascii; Write-Host "  $name saved" }
}
Ask-Key 'gemini.key' 'Gemini API key for the J.A.R.V.I.S voice assistant (free: aistudio.google.com)' 'GEMINI_KEY'
Ask-Key 'youtube.key' 'YouTube Data API key for JK Tube search inside the app (optional)' 'YOUTUBE_KEY'

Write-Host '[4/5] Building'
foreach ($p in "$Cyt\launcher", "$Cyt\addons\thalaivar-hub") {
    if (-not (Test-Path "$p\local.properties")) { "sdk.dir=$($env:ANDROID_HOME -replace '\\','/')" | Set-Content "$p\local.properties" -Encoding ascii }
    Push-Location $p
    & .\gradlew.bat --no-daemon -q assembleRelease --console=plain
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw "build failed in $p" }
}
Write-Host '  launcher and Thalaivar Hub built'
if ($BuildOnly) {
    Write-Host "`nBuilt. Install later with: powershell -ExecutionPolicy Bypass -File setup.ps1 -TvHost <tv ip>"
    exit 0
}

Write-Host '[5/5] Installing on the TV (backup first; your Home stays as it is)'
if (-not $TvHost) { $TvHost = Read-Host 'TV IP address (TV Settings > Network)' }
if (Test-Path "$Cyt\tools\thalaivar\poster_walls.py") {
    Write-Host '  optional: personal 3D poster wallpapers from TMDB (your TV only)'
    Push-Location $Cyt
    python -I tools\thalaivar\poster_walls.py
    Pop-Location
}
& powershell -NoProfile -ExecutionPolicy Bypass -File "$Cyt\tools\thalaivar\install.ps1" -TvHost $TvHost -Motion $Motion -SkipBuild
if ($LASTEXITCODE -ne 0) { throw 'install failed; see the messages above and docs/GUIDE.md#troubleshooting' }
