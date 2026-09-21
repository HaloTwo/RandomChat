param(
    [ValidateSet("new","continue","attach")]
    [string]$Mode = "new",

    [int]$PollSeconds = 3,

    # If a task is marked "running" but OpenCode becomes idle,
    # wait this long before automatically continuing it.
    [int]$IdleRecoverySeconds = 25,

    # Long thinking is allowed. Only recover an ACTIVE session when
    # both logs and worker CPU show no progress for this many minutes.
    [int]$HardStallMinutes = 12,

    [int]$RetryWindowSeconds = 90,
    [int]$RetryThreshold = 2,
    [int]$MaxRecoveriesPer10Min = 5
)

$ErrorActionPreference = "Stop"

# Put this file in the RandomChat repository root, next to ProjectRC/.
$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Join-Path $RepoRoot "ProjectRC"
$TaskStateFile = Join-Path $ProjectDir ".agent-watchdog.json"

$StateDir = Join-Path $env:LOCALAPPDATA "RandomChat"
$WatchdogLog = Join-Path $StateDir "opencode-watchdog.log"
New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

function Write-WatchdogLog {
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

function Invoke-OpenCodeJson {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Data = ""
    )

    try {
        if ($Data) {
            $raw = (& opencode api $Method $Path --data $Data 2>$null | Out-String)
        }
        else {
            $raw = (& opencode api $Method $Path 2>$null | Out-String)
        }

        if (-not $raw.Trim()) {
            return $null
        }

        return ($raw | ConvertFrom-Json)
    }
    catch {
        return $null
    }
}

function Get-OpenCodeLogTarget {
    try {
        $raw = (& opencode debug paths log 2>$null | Select-Object -First 1)

        if ($raw) {
            $path = $raw.Trim()

            if ($path) {
                return $path
            }
        }
    }
    catch {}

    return (Join-Path $env:USERPROFILE ".local\share\opencode\log")
}

function Get-LogFiles {
    param([string]$Target)

    if (Test-Path $Target -PathType Leaf) {
        return @(Get-Item $Target)
    }

    if (Test-Path $Target -PathType Container) {
        return @(
            Get-ChildItem $Target -File -Filter "*.log" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime
        )
    }

    return @()
}

function Get-TaskState {
    if (-not (Test-Path $TaskStateFile -PathType Leaf)) {
        return $null
    }

    try {
        $raw = Get-Content $TaskStateFile -Raw -Encoding UTF8
        if (-not $raw.Trim()) {
            return $null
        }

        return ($raw | ConvertFrom-Json)
    }
    catch {
        Write-WatchdogLog "Could not parse $TaskStateFile. Idle auto-continue is temporarily disabled."
        return $null
    }
}

function Set-TaskStateRunningIfMissing {
    if (Test-Path $TaskStateFile -PathType Leaf) {
        return
    }

    $obj = [ordered]@{
        status = "running"
        task = "Current OpenCode task"
        updated_at = (Get-Date).ToString("o")
        next = "Continue from HANDOFF and the current session."
    }

    $obj | ConvertTo-Json | Set-Content $TaskStateFile -Encoding UTF8
    Write-WatchdogLog "Created task state as RUNNING for attach mode."
}

function Test-TaskRunning {
    $state = Get-TaskState

    if ($null -eq $state) {
        return $false
    }

    return ([string]$state.status).ToLowerInvariant() -eq "running"
}

function Get-ActiveSessionIds {
    # OpenCode V2 preferred endpoint.
    $data = Invoke-OpenCodeJson -Method "GET" -Path "/api/session/active"

    if ($null -ne $data) {
        $ids = @()

        foreach ($p in $data.PSObject.Properties) {
            $type = ""

            try {
                $type = [string]$p.Value.type
            }
            catch {}

            if (-not $type -or $type -eq "running" -or $type -eq "busy" -or $type -eq "retry") {
                $ids += $p.Name
            }
        }

        return @($ids)
    }

    # Compatibility fallback.
    $data = Invoke-OpenCodeJson -Method "GET" -Path "/session/status"

    if ($null -eq $data) {
        return @()
    }

    $ids = @()

    foreach ($p in $data.PSObject.Properties) {
        $type = ""

        try {
            $type = [string]$p.Value.type
        }
        catch {}

        if ($type -eq "running" -or $type -eq "busy" -or $type -eq "retry") {
            $ids += $p.Name
        }
    }

    return @($ids)
}

function Get-LatestSessionId {
    $data = Invoke-OpenCodeJson -Method "GET" -Path "/session"

    if ($null -eq $data) {
        $data = Invoke-OpenCodeJson -Method "GET" -Path "/api/session"
    }

    if ($null -eq $data) {
        return $null
    }

    $items = @()

    if ($data -is [System.Array]) {
        $items = @($data)
    }
    elseif ($data.PSObject.Properties.Name -contains "items") {
        $items = @($data.items)
    }
    elseif ($data.PSObject.Properties.Name -contains "sessions") {
        $items = @($data.sessions)
    }
    else {
        $items = @($data)
    }

    foreach ($item in $items) {
        foreach ($name in @("id", "sessionID", "sessionId")) {
            if ($item.PSObject.Properties.Name -contains $name) {
                $value = [string]$item.$name

                if ($value) {
                    return $value
                }
            }
        }
    }

    return $null
}

function Send-ContinuePrompt {
    param([string]$SessionId)

    if (-not $SessionId) {
        return $false
    }

    $text = @"
계속 진행해.
AGENTS.md와 ProjectRC/HANDOFF.md의 현재 규칙과 체크포인트를 기준으로,
이미 완료한 분석은 반복하지 말고 중단된 실제 다음 작업부터 이어가.
프로젝트 내부에서 확인 가능한 내용은 사용자에게 다시 묻지 말고 직접 확인해.
작업이 완전히 끝날 때까지 구현 -> 검증 -> 수정 -> 재검증을 계속해.
"@

    $payloadObject = [ordered]@{
        parts = @(
            [ordered]@{
                type = "text"
                text = $text
            }
        )
        agent = "build"
    }

    $json = $payloadObject | ConvertTo-Json -Depth 8 -Compress

    # V1-compatible async prompt endpoint used by OpenCode 2.x.
    try {
        & opencode api POST "/session/$SessionId/prompt_async" --data $json *> $null

        if ($LASTEXITCODE -eq 0) {
            Write-WatchdogLog "Sent automatic continue prompt to session $SessionId."
            return $true
        }
    }
    catch {}

    # Synchronous fallback.
    try {
        & opencode api POST "/session/$SessionId/message" --data $json *> $null

        if ($LASTEXITCODE -eq 0) {
            Write-WatchdogLog "Sent automatic continue prompt through message endpoint to session $SessionId."
            return $true
        }
    }
    catch {}

    return $false
}

function Start-OpenCodeWindow {
    param(
        [bool]$Resume,
        [bool]$SendUpdate
    )

    if ($Resume -and $SendUpdate) {
        $command = 'opencode --continue --prompt "update"'
    }
    elseif ($Resume) {
        $command = 'opencode --continue'
    }
    else {
        $command = 'opencode'
    }

    # cmd.exe is intentionally used only to launch the interactive TUI.
    $full = 'cd /d "{0}" && {1}' -f $ProjectDir, $command

    Write-WatchdogLog ("Starting OpenCode: " + $command)

    return Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList "/c", $full `
        -WorkingDirectory $ProjectDir `
        -PassThru
}

function Stop-ProcessTree {
    param([int]$PidToStop)

    if ($PidToStop -le 0) {
        return
    }

    Write-WatchdogLog "Stopping managed OpenCode process tree (PID $PidToStop)."

    try {
        & taskkill.exe /PID $PidToStop /T /F *> $null
    }
    catch {}
}

function Test-ServiceHealthy {
    try {
        & opencode api GET /api/info *> $null

        if ($LASTEXITCODE -eq 0) {
            return $true
        }
    }
    catch {}

    try {
        & opencode api GET /session *> $null
        return ($LASTEXITCODE -eq 0)
    }
    catch {}

    return $false
}

function Get-WorkerCpuSeconds {
    $total = 0.0

    try {
        $procs = Get-Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.ProcessName -like "ollama*" -or
                $_.ProcessName -like "llama*" -or
                $_.ProcessName -like "opencode*" -or
                $_.ProcessName -like "node*"
            }

        foreach ($p in $procs) {
            try {
                $total += [double]$p.CPU
            }
            catch {}
        }
    }
    catch {}

    return $total
}

function Can-Recover {
    param(
        [System.Collections.Generic.List[datetime]]$RecoveryTimes,
        [int]$Limit
    )

    $now = Get-Date

    for ($i = $RecoveryTimes.Count - 1; $i -ge 0; $i--) {
        if (($now - $RecoveryTimes[$i]).TotalMinutes -gt 10) {
            $RecoveryTimes.RemoveAt($i)
        }
    }

    if ($RecoveryTimes.Count -ge $Limit) {
        return $false
    }

    $RecoveryTimes.Add($now)
    return $true
}

function Restart-ManagedSession {
    param(
        $ManagedProcess,
        [System.Collections.Generic.List[datetime]]$RecoveryTimes,
        [string]$Reason
    )

    Write-WatchdogLog "Recovery triggered: $Reason"

    if (-not (Can-Recover -RecoveryTimes $RecoveryTimes -Limit $MaxRecoveriesPer10Min)) {
        Write-WatchdogLog "Too many recoveries in 10 minutes. Automatic recovery stopped."
        return $null
    }

    if ($ManagedProcess -and -not $ManagedProcess.HasExited) {
        Stop-ProcessTree -PidToStop $ManagedProcess.Id

        try {
            $ManagedProcess.WaitForExit(5000) | Out-Null
        }
        catch {}
    }

    if (-not (Test-ServiceHealthy)) {
        Write-WatchdogLog "OpenCode service looks unhealthy. Restarting background service."

        try {
            & opencode service restart *> $null
        }
        catch {}

        Start-Sleep -Seconds 2
    }

    Start-Sleep -Seconds 2
    return (Start-OpenCodeWindow -Resume:$true -SendUpdate:$true)
}

Require-Command "opencode"

if (-not (Test-Path $ProjectDir -PathType Container)) {
    throw "ProjectRC folder was not found: $ProjectDir"
}

$logTarget = Get-OpenCodeLogTarget

Write-WatchdogLog "Repository: $RepoRoot"
Write-WatchdogLog "Project: $ProjectDir"
Write-WatchdogLog "Mode: $Mode"
Write-WatchdogLog "OpenCode log target: $logTarget"
Write-WatchdogLog "Idle recovery: $IdleRecoverySeconds second(s), only while .agent-watchdog.json says status=running"
Write-WatchdogLog "Hard active-session stall timeout: $HardStallMinutes minute(s)"
Write-WatchdogLog "Watchdog log: $WatchdogLog"

$fatalPatterns = @(
    "OpenAI Chat stream ended without finish_reason",
    "unexpected EOF"
)

$logLengths = @{}
$errorTimes = New-Object System.Collections.Generic.List[datetime]
$recoveryTimes = New-Object System.Collections.Generic.List[datetime]

$lastActivity = Get-Date
$idleSince = $null
$previousCpu = Get-WorkerCpuSeconds

foreach ($file in (Get-LogFiles $logTarget)) {
    try {
        $text = Get-Content $file.FullName -Raw -Encoding UTF8 -ErrorAction Stop
        $logLengths[$file.FullName] = $text.Length
    }
    catch {
        $logLengths[$file.FullName] = 0
    }
}

$managedProcess = $null

switch ($Mode) {
    "new" {
        $managedProcess = Start-OpenCodeWindow -Resume:$false -SendUpdate:$false
    }

    "continue" {
        $managedProcess = Start-OpenCodeWindow -Resume:$true -SendUpdate:$true
    }

    "attach" {
        Set-TaskStateRunningIfMissing
        Write-WatchdogLog "Attached to the currently most-recent OpenCode session. No new TUI was started."
    }
}

Write-WatchdogLog "Monitoring started."
Write-WatchdogLog "Long thinking is allowed. The watchdog does NOT restart a healthy active session just because it is slow."
Write-WatchdogLog "If a RUNNING task becomes idle before completion, it will automatically send a continue prompt."
Write-WatchdogLog "Close this watchdog window to stop monitoring."

while ($true) {
    Start-Sleep -Seconds $PollSeconds

    # Managed TUI was closed/crashed: reopen it and continue the last session.
    if ($managedProcess -and $managedProcess.HasExited) {
        Write-WatchdogLog "Managed OpenCode TUI exited with code $($managedProcess.ExitCode)."

        $managedProcess = Restart-ManagedSession `
            -ManagedProcess $managedProcess `
            -RecoveryTimes $recoveryTimes `
            -Reason "OpenCode TUI exited"

        if ($null -eq $managedProcess) {
            break
        }

        $lastActivity = Get-Date
        $idleSince = $null
        continue
    }

    # Read newly-appended OpenCode logs.
    $newLogText = ""

    foreach ($file in (Get-LogFiles $logTarget)) {
        try {
            $text = Get-Content $file.FullName -Raw -Encoding UTF8 -ErrorAction Stop

            if (-not $logLengths.ContainsKey($file.FullName)) {
                $logLengths[$file.FullName] = 0
            }

            $oldLength = [int]$logLengths[$file.FullName]

            if ($text.Length -lt $oldLength) {
                $oldLength = 0
            }

            if ($text.Length -gt $oldLength) {
                $newLogText += $text.Substring($oldLength)
                $logLengths[$file.FullName] = $text.Length
                $lastActivity = Get-Date
            }
        }
        catch {}
    }

    # Worker CPU acts as a second heartbeat so long model thinking does not
    # get mistaken for a hang just because the logs are quiet.
    $currentCpu = Get-WorkerCpuSeconds
    $cpuDelta = $currentCpu - $previousCpu
    $previousCpu = $currentCpu

    if ($cpuDelta -gt 0.20) {
        $lastActivity = Get-Date
    }

    # Detect repeated provider/stream failures.
    if ($newLogText) {
        foreach ($pattern in $fatalPatterns) {
            if ($newLogText -like "*$pattern*") {
                Write-WatchdogLog "Detected stream failure: $pattern"
                $errorTimes.Add((Get-Date))
                break
            }
        }
    }

    $now = Get-Date

    for ($i = $errorTimes.Count - 1; $i -ge 0; $i--) {
        if (($now - $errorTimes[$i]).TotalSeconds -gt $RetryWindowSeconds) {
            $errorTimes.RemoveAt($i)
        }
    }

    if ($errorTimes.Count -ge $RetryThreshold) {
        $errorTimes.Clear()

        if ($managedProcess) {
            $managedProcess = Restart-ManagedSession `
                -ManagedProcess $managedProcess `
                -RecoveryTimes $recoveryTimes `
                -Reason "Repeated provider stream failures"

            if ($null -eq $managedProcess) {
                break
            }
        }
        else {
            $sessionId = Get-LatestSessionId

            if (-not (Send-ContinuePrompt -SessionId $sessionId)) {
                Write-WatchdogLog "Could not continue through API. Starting a continue TUI as fallback."
                $managedProcess = Start-OpenCodeWindow -Resume:$true -SendUpdate:$true
            }
        }

        $lastActivity = Get-Date
        $idleSince = $null
        continue
    }

    $activeIds = @(Get-ActiveSessionIds)
    $isActive = $activeIds.Count -gt 0
    $taskRunning = Test-TaskRunning

    if ($isActive) {
        $idleSince = $null

        # This is deliberately conservative: it only fires when OpenCode still
        # says the session is active AND there has been no log or worker-CPU
        # progress for a long time.
        if ($taskRunning -and (($now - $lastActivity).TotalMinutes -ge $HardStallMinutes)) {
            if ($managedProcess) {
                $managedProcess = Restart-ManagedSession `
                    -ManagedProcess $managedProcess `
                    -RecoveryTimes $recoveryTimes `
                    -Reason "Active session made no observable progress for $HardStallMinutes minutes"

                if ($null -eq $managedProcess) {
                    break
                }
            }
            else {
                $sessionId = Get-LatestSessionId

                if (-not (Send-ContinuePrompt -SessionId $sessionId)) {
                    Write-WatchdogLog "Hard-stall API recovery failed. Starting a continue TUI."
                    $managedProcess = Start-OpenCodeWindow -Resume:$true -SendUpdate:$true
                }
            }

            $lastActivity = Get-Date
            $idleSince = $null
        }

        continue
    }

    # Session is idle. This is the key guard for the user's actual problem:
    # the agent sometimes stops and waits for another "계속" even though the
    # current task is not finished.
    if ($taskRunning) {
        if ($null -eq $idleSince) {
            $idleSince = Get-Date
            Write-WatchdogLog "Task is still RUNNING but OpenCode became idle. Starting grace timer."
            continue
        }

        if (($now - $idleSince).TotalSeconds -ge $IdleRecoverySeconds) {
            $sessionId = Get-LatestSessionId

            Write-WatchdogLog "Task is unfinished and session stayed idle. Sending automatic continue."

            if (-not (Send-ContinuePrompt -SessionId $sessionId)) {
                Write-WatchdogLog "API continue failed."

                if ($managedProcess) {
                    $managedProcess = Restart-ManagedSession `
                        -ManagedProcess $managedProcess `
                        -RecoveryTimes $recoveryTimes `
                        -Reason "Unfinished task became idle"

                    if ($null -eq $managedProcess) {
                        break
                    }
                }
                else {
                    Write-WatchdogLog "Starting a continue TUI as fallback."
                    $managedProcess = Start-OpenCodeWindow -Resume:$true -SendUpdate:$true
                }
            }

            $lastActivity = Get-Date
            $idleSince = $null
        }
    }
    else {
        # No running task marker => normal idle prompt/completed task.
        $idleSince = $null
    }
}
