#requires -RunAsAdministrator
$ErrorActionPreference = 'Stop'

# 집 공유기에서만 쓴다. 공용 네트워크에는 규칙을 만들지 않는다.
$private = @(Get-NetConnectionProfile | Where-Object { $_.NetworkCategory -eq 'Private' -and $_.IPv4Connectivity -ne 'Disconnected' })
if ($private.Count -eq 0) {
    throw '개인 네트워크 연결을 찾지 못했습니다. 집 공유기인지 확인한 뒤 Windows 설정에서 해당 연결을 개인 네트워크로 바꾸고 다시 실행하세요.'
}

$node = (Get-Command node -ErrorAction Stop).Source
$ruleName = 'RandomChat Home TCP 3000'
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "이미 방화벽 규칙이 있습니다: $ruleName"
    Get-NetFirewallRule -DisplayName $ruleName | Get-NetFirewallPortFilter | Format-Table Protocol,LocalPort -AutoSize
    exit 0
}

New-NetFirewallRule -DisplayName $ruleName -Description 'RandomChat 공기계 테스트용. 개인 네트워크의 로컬 서브넷에서 Node.js TCP 3000만 허용.' `
    -Direction Inbound -Action Allow -Profile Private -Program $node -Protocol TCP -LocalPort 3000 -RemoteAddress LocalSubnet | Out-Null

Write-Host '완료: 집 개인 네트워크의 같은 공유기 기기만 RandomChat TCP 3000에 접속할 수 있습니다.'
Write-Host '나중에 지우려면 관리자 PowerShell에서 다음을 실행하세요:'
Write-Host "Remove-NetFirewallRule -DisplayName '$ruleName'"
