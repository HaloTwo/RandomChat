[CmdletBinding()]
param(
    [string]$ServerAddress = 'http://192.168.0.2:3000',
    [switch]$Install
)

$ErrorActionPreference = 'Stop'
$server = $ServerAddress.Trim().TrimEnd('/')
if ($server -notmatch '^http://(?:10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[0-1])\.)[0-9.]+:3000$') {
    throw '사설 IPv4 서버 주소를 http://192.168.x.x:3000 형식으로 입력하세요.'
}

$health = Invoke-RestMethod -Uri "$server/api/health" -TimeoutSec 5
if ($health.ok -ne $true) { throw '서버 상태 확인에 실패했습니다.' }

$apkPath = Join-Path $env:TEMP 'moment-local-debug.apk'
Invoke-WebRequest -Uri "$server/app.apk" -OutFile $apkPath -UseBasicParsing -TimeoutSec 20
$apk = Get-Item -LiteralPath $apkPath
if ($apk.Length -lt 1024) { throw '다운로드한 APK가 비정상적으로 작습니다.' }
Write-Output "서버 확인: $server"
Write-Output "APK 다운로드: $($apk.FullName) ($($apk.Length) bytes)"

if (-not $Install) {
    Write-Output '설치하려면 USB 디버깅을 켠 공기계를 연결한 뒤 -Install 옵션으로 다시 실행하세요.'
    exit 0
}

$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'Android SDK platform-tools의 adb.exe를 찾지 못했습니다.' }
$devices = @(& $adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_ -split '\s+')[0] })
if ($devices.Count -eq 0) { throw 'USB 디버깅이 허용된 Android 기기를 찾지 못했습니다.' }
if ($devices.Count -gt 1) { throw '테스트할 공기계 하나만 연결하세요.' }

& $adb -s $devices[0] install -r $apkPath
if ($LASTEXITCODE -ne 0) { throw 'APK 설치에 실패했습니다.' }
& $adb -s $devices[0] shell am start -n 'com.moment.randomchat/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw '앱 실행에 실패했습니다.' }
Write-Output "공기계 설치·실행 완료: $($devices[0])"
