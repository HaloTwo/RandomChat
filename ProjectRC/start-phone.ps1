$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Test-PrivateIPv4([string]$Address) {
    $parts = $Address.Split('.')
    if ($parts.Count -ne 4) { return $false }
    foreach ($part in $parts) {
        if ($part -notmatch '^\d{1,3}$' -or [int]$part -gt 255) { return $false }
    }
    return ([int]$parts[0] -eq 10 -or ([int]$parts[0] -eq 172 -and [int]$parts[1] -ge 16 -and [int]$parts[1] -le 31) -or ([int]$parts[0] -eq 192 -and [int]$parts[1] -eq 168))
}

try {
    $addresses = @(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.AddressState -eq 'Preferred' -and (Test-PrivateIPv4 $_.IPAddress) } | Sort-Object InterfaceAlias, IPAddress)
    if ($addresses.Count -eq 0) { throw '사설 IPv4를 찾지 못했습니다. PC와 공기계를 같은 Wi-Fi에 연결하세요.' }
    Write-Host '공기계와 같은 Wi-Fi의 PC 주소를 고르세요.'
    for ($i = 0; $i -lt $addresses.Count; $i++) { Write-Host "[$($i+1)] $($addresses[$i].InterfaceAlias) - $($addresses[$i].IPAddress)" }
    $number = if ($addresses.Count -eq 1) { 1 } else { Read-Host '번호 입력' }
    if ([string]$number -notmatch '^\d+$' -or [int]$number -lt 1 -or [int]$number -gt $addresses.Count) { throw '목록에 있는 번호를 입력하세요.' }
    $env:RANDOMCHAT_HOST = $addresses[[int]$number - 1].IPAddress
    $network = Get-NetConnectionProfile -InterfaceIndex $addresses[[int]$number - 1].InterfaceIndex -ErrorAction SilentlyContinue
    if ($network.NetworkCategory -ne 'Private') {
        Write-Warning '현재 Windows 네트워크 분류가 공용입니다. 집 공유기가 맞는지 확인한 뒤 Windows 설정 > 네트워크 및 인터넷 > 이더넷/Wi-Fi > 네트워크 프로필을 개인으로 바꾸세요. 공용 장소에서는 바꾸지 마세요.'
    }
    if (Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue) {
        try {
            $health = Invoke-RestMethod -Uri "http://$($env:RANDOMCHAT_HOST):3000/api/health" -TimeoutSec 3
            $apk = Invoke-WebRequest -Uri "http://$($env:RANDOMCHAT_HOST):3000/app.apk" -UseBasicParsing -TimeoutSec 5
            if ($health.ok -eq $true -and $apk.StatusCode -eq 200) {
                Write-Host "RandomChat 서버가 이미 실행 중입니다: http://$($env:RANDOMCHAT_HOST):3000"
                Write-Host "공기계 APK 다운로드: http://$($env:RANDOMCHAT_HOST):3000/app.apk"
                return
            }
        } catch { }
        throw '3000번 포트가 다른 프로그램에서 사용 중이거나 기존 RandomChat 서버가 오래된 버전입니다. 그 프로그램을 종료하고 다시 실행하세요.'
    }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js 22 이상을 먼저 설치하세요. README의 설치 순서를 확인하세요.' }
    if (-not (Test-Path -LiteralPath 'node_modules\sharp\package.json')) {
        npm ci
        if ($LASTEXITCODE -ne 0) { throw 'npm ci 실패. 인터넷 연결과 Node.js 버전을 확인하세요.' }
    }
    Write-Host "`nPC/공기계 서버 주소: http://$($env:RANDOMCHAT_HOST):3000"
    Write-Host "공기계 APK 다운로드: http://$($env:RANDOMCHAT_HOST):3000/app.apk"
    Write-Host '공기계 앱의 서버 주소 칸에 위 주소를 그대로 입력하세요.'
    Write-Host 'Windows 방화벽 팝업이 나오면 개인 네트워크만 허용하세요. 공기계에서 안 열리면 README의 연결 문제를 확인하세요.'
    Write-Host '이 창을 닫거나 Ctrl+C를 누르면 서버가 중지됩니다.'
    node server.js
    if ($LASTEXITCODE -ne 0) { throw '서버 시작에 실패했습니다. 위 오류를 확인하세요.' }
} catch {
    Write-Error $_.Exception.Message
    exit 1
}
