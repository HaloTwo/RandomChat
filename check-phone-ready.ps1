$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Show-Check([string]$Name, [bool]$Ok, [string]$Detail) {
    $mark = if ($Ok) { 'OK' } else { '확인 필요' }
    Write-Host "[$mark] $Name - $Detail"
}

function Test-PrivateIPv4([string]$Address) {
    $parts = $Address.Split('.')
    if ($parts.Count -ne 4) { return $false }
    foreach ($part in $parts) {
        if ($part -notmatch '^\d{1,3}$' -or [int]$part -gt 255) { return $false }
    }
    return ([int]$parts[0] -eq 10 -or ([int]$parts[0] -eq 172 -and [int]$parts[1] -ge 16 -and [int]$parts[1] -le 31) -or ([int]$parts[0] -eq 192 -and [int]$parts[1] -eq 168))
}

$addresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.AddressState -eq 'Preferred' -and (Test-PrivateIPv4 $_.IPAddress) })
if ($addresses.Count -eq 0) {
    Show-Check 'PC 사설 주소' $false '같은 공유기 연결을 확인하세요.'
    exit 1
}

$address = $addresses[0]
$base = "http://$($address.IPAddress):3000"
$profile = Get-NetConnectionProfile -InterfaceIndex $address.InterfaceIndex -ErrorAction SilentlyContinue
Show-Check 'PC 사설 주소' $true $base
Show-Check 'Windows 네트워크' ($profile.NetworkCategory -eq 'Private') "현재: $($profile.NetworkCategory). 집 공유기일 때만 개인 네트워크여야 합니다."

$apkPath = Join-Path $PSScriptRoot 'android\build\moment-local-debug.apk'
$apkExists = Test-Path -LiteralPath $apkPath
$apkDetail = if ($apkExists) { "$([math]::Round((Get-Item -LiteralPath $apkPath).Length / 1KB, 1)) KB" } else { 'APK를 찾지 못했습니다.' }
Show-Check 'APK 파일' $apkExists $apkDetail

$tokenPath = Join-Path $PSScriptRoot 'data\LOCAL_TOKENS.txt'
$hasTokenMemo = Test-Path -LiteralPath $tokenPath
$tokenDetail = if ($hasTokenMemo) { '파일이 있습니다. 원문은 출력하지 않습니다.' } else { '사진·영상 검토 전 node admin.js create-reviewer-to-file "테스트 담당자"를 실행하세요.' }
Show-Check '담당자 토큰 메모' $hasTokenMemo $tokenDetail

try {
    $health = Invoke-RestMethod -Uri "$base/api/health" -TimeoutSec 4
    Show-Check '서버 응답' ($health.ok -eq $true) "$base/api/health"
} catch {
    Show-Check '서버 응답' $false 'start-phone.cmd를 실행한 뒤 다시 확인하세요.'
}

try {
    $apk = Invoke-WebRequest -Uri "$base/app.apk" -UseBasicParsing -TimeoutSec 8
    Show-Check '공기계 APK 주소' ($apk.StatusCode -eq 200) "$base/app.apk"
} catch {
    Show-Check '공기계 APK 주소' $false '서버가 최신 버전인지 확인하세요.'
}

$rule = Get-NetFirewallRule -DisplayName 'RandomChat Home TCP 3000' -ErrorAction SilentlyContinue
$hasRule = $null -ne $rule
$ruleDetail = if ($hasRule) { 'RandomChat Home TCP 3000' } else { '개인 네트워크에서 enable-home-phone-access.ps1을 관리자 PowerShell로 실행하세요.' }
Show-Check '개인 네트워크 방화벽 규칙' $hasRule $ruleDetail

Write-Host ''
Write-Host '공기계 순서: 같은 Wi-Fi → 브라우저에서 APK 주소 열기 → 설치 → 앱에서 서버 주소 입력 → 서버 연결 확인 → 테스트 계정 만들기'
