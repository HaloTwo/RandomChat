param(
    [int]$PollSeconds = 3,
    [int]$IdleSeconds = 25,
    [int]$MaxAutoContinuesPer10Min = 6
)

$ErrorActionPreference = "Stop"

# This script MUST live in the RandomChat repository root.
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$TaskStateFile = Join-Path $RepoRoot ".agent-watchdog.json"
$StateDir = Join-Path $env:LOCALAPPDATA "RandomChat"
$WatchdogLog = Join-Path $StateDir "opencode-watchdog.log"

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

$ContinuePrompt = @"
계속 진행해.
현재 작업은 아직 완료되지 않았다.
AGENTS.md와 HANDOFF/README/실제 코드 상태를 기준으로,
이미 끝난 분석은 반복하지 말고 중단된 다음 실제 작업부터 이어가.
프로젝트 내부에서 확인 가능한 내용은 사용자에게 다시 묻지 마.
작업 완료 조건을 만족할 때까지 구현 -> 검증 -> 수정 -> 재검증을 계속해.
"@

function Log {
    param([string]$Message)
    $line = "[{0}] {1}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"), $Message
    Write-Host $line
    Add-Content -Path $WatchdogLog -Value $line -Encoding UTF8
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name was not found in PATH."
    }
}

function Get-TaskStatus {
    if (-not (Test-Path $TaskStateFile -PathType Leaf)) {
        return ""
    }

    try {
        $obj = Get-Content $TaskStateFile -Raw -Encoding UTF8 | ConvertFrom-Json
        return ([string]$obj.status).ToLowerInvariant()
    }
    catch {
        return ""
    }
}

function Get-OpenCodeLogDir {
    try {
        $raw = (& opencode debug paths log 2>$null | Select-Object -First 1)
        if ($raw) {
            $p = $raw.Trim()
            if ($p) { return $p }
        }
    } catch {}

    return (Join-Path $env:USERPROFILE ".local\share\opencode\log")
}

function Get-LogSnapshot {
    param([string]$LogDir)

    $result = [ordered]@{
        NewestWrite = [datetime]::MinValue
        TotalLength = 0L
    }

    if (-not (Test-Path $LogDir)) {
        return [pscustomobject]$result
    }

    $files = @()

    if (Test-Path $LogDir -PathType Leaf) {
        $files = @(Get-Item $LogDir)
    }
    else {
        $files = @(Get-ChildItem $LogDir -File -Filter "*.log" -ErrorAction SilentlyContinue)
    }

    foreach ($f in $files) {
        if ($f.LastWriteTime -gt $result.NewestWrite) {
            $result.NewestWrite = $f.LastWriteTime
        }
        $result.TotalLength += [int64]$f.Length
    }

    return [pscustomobject]$result
}

function Get-OllamaCpuSeconds {
    $sum = 0.0

    try {
        $procs = Get-Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.ProcessName -like "ollama*" -or
                $_.ProcessName -like "llama*"
            }

        foreach ($p in $procs) {
            try { $sum += [double]$p.CPU } catch {}
        }
    } catch {}

    return $sum
}

function Test-OpenCodeTuiRunning {
    try {
        $items = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Name -match "^opencode(\.exe)?$" -and
                $_.CommandLine -notmatch "\s(run|serve|service|api|web|debug)\b"
            }

        return (@($items).Count -gt 0)
    }
    catch {
        return $false
    }
}

function Ensure-TaskStateExists {
    if (Test-Path $TaskStateFile -PathType Leaf) {
        return
    }

    $obj = [ordered]@{
        status = "running"
        task = "Current OpenCode task"
        updated_at = (Get-Date).ToString("o")
        next = "Continue the current unfinished task."
    }

    $obj | ConvertTo-Json | Set-Content $TaskStateFile -Encoding UTF8
    Log "Created .agent-watchdog.json with status=running."
}

function Send-Continue {
    Log "AUTO-CONTINUE: agent appears idle while task is still running."

    Push-Location $RepoRoot
    try {
        # Important: run from RandomChat repo root, not ProjectRC.
        & opencode run --continue --agent build $ContinuePrompt
        $code = $LASTEXITCODE
        Log "AUTO-CONTINUE command finished with exit code $code."
    }
    catch {
        Log ("AUTO-CONTINUE failed: " + $_.Exception.Message)
    }
    finally {
        Pop-Location
    }
}

Require-Command "opencode"

# The repo root itself must contain AGENTS.md.
if (-not (Test-Path (Join-Path $RepoRoot "AGENTS.md") -PathType Leaf)) {
    throw "AGENTS.md was not found next to this script. Put this script in the RandomChat repo root."
}

Ensure-TaskStateExists

$logDir = Get-OpenCodeLogDir
Log "Repository root: $RepoRoot"
Log "Task state: $TaskStateFile"
Log "OpenCode log target: $logDir"
Log "Idle threshold: $IdleSeconds second(s)"
Log "Monitoring started."
Log "OpenCode itself is NOT killed. Long thinking is allowed while Ollama CPU/log activity continues."
Log "When the TUI is still open but the agent silently stops, watchdog will run 'opencode run --continue' from the RandomChat root."

$lastSnapshot = Get-LogSnapshot $logDir
$lastCpu = Get-OllamaCpuSeconds
$lastProgressAt = Get-Date
$continueTimes = New-Object System.Collections.Generic.List[datetime]

while ($true) {
    Start-Sleep -Seconds $PollSeconds

    if ((Get-TaskStatus) -ne "running") {
        $lastProgressAt = Get-Date
        continue
    }

    if (-not (Test-OpenCodeTuiRunning)) {
        # TUI closed: this watchdog only handles the user's actual issue
        # (TUI remains open but agent stops). Don't launch surprise windows here.
        $lastProgressAt = Get-Date
        continue
    }

    $snap = Get-LogSnapshot $logDir
    $cpu = Get-OllamaCpuSeconds

    $logMoved = ($snap.TotalLength -ne $lastSnapshot.TotalLength) -or
                ($snap.NewestWrite -gt $lastSnapshot.NewestWrite)

    $cpuDelta = $cpu - $lastCpu

    # Any log activity or actual Ollama compute counts as progress.
    if ($logMoved -or $cpuDelta -gt 0.15) {
        $lastProgressAt = Get-Date
    }

    $lastSnapshot = $snap
    $lastCpu = $cpu

    $now = Get-Date
    $idleFor = ($now - $lastProgressAt).TotalSeconds

    if ($idleFor -lt $IdleSeconds) {
        continue
    }

    # Final safety check: if Ollama just started computing again, do not interrupt.
    Start-Sleep -Milliseconds 800
    $cpu2 = Get-OllamaCpuSeconds

    if (($cpu2 - $lastCpu) -gt 0.10) {
        $lastCpu = $cpu2
        $lastProgressAt = Get-Date
        continue
    }

    for ($i = $continueTimes.Count - 1; $i -ge 0; $i--) {
        if (($now - $continueTimes[$i]).TotalMinutes -gt 10) {
            $continueTimes.RemoveAt($i)
        }
    }

    if ($continueTimes.Count -ge $MaxAutoContinuesPer10Min) {
        Log "Auto-continue limit reached. Waiting 60 seconds to avoid a loop."
        Start-Sleep -Seconds 60
        $lastProgressAt = Get-Date
        continue
    }

    $continueTimes.Add($now)
    Send-Continue

    # Give the continued run time to actually start.
    Start-Sleep -Seconds 5
    $lastProgressAt = Get-Date
    $lastSnapshot = Get-LogSnapshot $logDir
    $lastCpu = Get-OllamaCpuSeconds
}
