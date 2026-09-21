param(
    [switch]$Continue,
    [int]$PollSeconds = 2,
    [int]$RetryWindowSeconds = 90,
    [int]$RetryThreshold = 2,
    [int]$MaxRestartsPer10Min = 5
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Join-Path $RepoRoot "ProjectRC"
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

function Get-OpenCodeLogTarget {
    try {
        $raw = (& opencode debug paths log 2>$null | Select-Object -First 1)
        if ($raw) {
            $path = $raw.Trim()
            if ($path) { return $path }
        }
    } catch {}

    return (Join-Path $env:USERPROFILE ".local\share\opencode\log")
}

function Get-LogFiles {
    param([string]$Target)

    if (Test-Path $Target -PathType Leaf) {
        return @(Get-Item $Target)
    }

    if (Test-Path $Target -PathType Container) {
        return @(Get-ChildItem $Target -File -Filter "*.log" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime)
    }

    return @()
}

function Start-OpenCodeWindow {
    param([bool]$Resume)

    $cmd = if ($Resume) {
        'opencode --continue --prompt "update"'
    } else {
        'opencode'
    }

    $full = 'cd /d "{0}" && {1}' -f $ProjectDir, $cmd

    Write-WatchdogLog ("Starting OpenCode: " + $cmd)
    return Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $full -WorkingDirectory $ProjectDir -PassThru
}

function Stop-ProcessTree {
    param([int]$PidToStop)

    Write-WatchdogLog "Stopping OpenCode process tree (PID $PidToStop)."
    & taskkill.exe /PID $PidToStop /T /F *> $null
}

function Test-ServiceHealthy {
    try {
        & opencode api get /api/info *> $null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

Require-Command "opencode"

if (-not (Test-Path $ProjectDir -PathType Container)) {
    throw "ProjectRC folder was not found: $ProjectDir"
}

$logTarget = Get-OpenCodeLogTarget
Write-WatchdogLog "Repository: $RepoRoot"
Write-WatchdogLog "Project: $ProjectDir"
Write-WatchdogLog "OpenCode log target: $logTarget"
Write-WatchdogLog "Watchdog log: $WatchdogLog"

$fatalPatterns = @(
    "OpenAI Chat stream ended without finish_reason",
    "unexpected EOF"
)

$watchStart = Get-Date
$logLengths = @{}
$errorTimes = New-Object System.Collections.Generic.List[datetime]
$restartTimes = New-Object System.Collections.Generic.List[datetime]

foreach ($file in (Get-LogFiles $logTarget)) {
    try {
        $text = Get-Content $file.FullName -Raw -Encoding UTF8 -ErrorAction Stop
        $logLengths[$file.FullName] = $text.Length
    } catch {
        $logLengths[$file.FullName] = 0
    }
}

$process = Start-OpenCodeWindow -Resume:$Continue.IsPresent

Write-WatchdogLog "Monitoring started. Close this watchdog window to stop automatic recovery."

while ($true) {
    Start-Sleep -Seconds $PollSeconds

    if ($process.HasExited) {
        Write-WatchdogLog "OpenCode exited with code $($process.ExitCode)."

        $now = Get-Date
        for ($i = $restartTimes.Count - 1; $i -ge 0; $i--) {
            if (($now - $restartTimes[$i]).TotalMinutes -gt 10) {
                $restartTimes.RemoveAt($i)
            }
        }

        if ($restartTimes.Count -ge $MaxRestartsPer10Min) {
            Write-WatchdogLog "Too many restarts in 10 minutes. Automatic recovery stopped."
            Write-Host ""
            Write-Host "Fix the underlying error, then run the watchdog again."
            break
        }

        if (-not (Test-ServiceHealthy)) {
            Write-WatchdogLog "OpenCode service looks unhealthy. Restarting service."
            try { & opencode service restart *> $null } catch {}
            Start-Sleep -Seconds 2
        }

        $restartTimes.Add((Get-Date))
        Start-Sleep -Seconds 2
        $process = Start-OpenCodeWindow -Resume:$true
        continue
    }

    $newLogText = ""
    foreach ($file in (Get-LogFiles $logTarget)) {
        try {
            $text = Get-Content $file.FullName -Raw -Encoding UTF8 -ErrorAction Stop

            if (-not $logLengths.ContainsKey($file.FullName)) {
                if ($file.CreationTime -ge $watchStart.AddSeconds(-5)) {
                    $logLengths[$file.FullName] = 0
                } else {
                    $logLengths[$file.FullName] = $text.Length
                }
            }

            $oldLength = [int]$logLengths[$file.FullName]
            if ($text.Length -lt $oldLength) { $oldLength = 0 }

            if ($text.Length -gt $oldLength) {
                $newLogText += $text.Substring($oldLength)
                $logLengths[$file.FullName] = $text.Length
            }
        } catch {}
    }

    if (-not $newLogText) { continue }

    $matched = $false
    foreach ($pattern in $fatalPatterns) {
        if ($newLogText -like "*$pattern*") {
            Write-WatchdogLog "Detected stream failure: $pattern"
            $matched = $true
            break
        }
    }

    if (-not $matched) { continue }

    $now = Get-Date
    $errorTimes.Add($now)

    for ($i = $errorTimes.Count - 1; $i -ge 0; $i--) {
        if (($now - $errorTimes[$i]).TotalSeconds -gt $RetryWindowSeconds) {
            $errorTimes.RemoveAt($i)
        }
    }

    if ($errorTimes.Count -lt $RetryThreshold) {
        Write-WatchdogLog "One stream failure detected. Waiting for OpenCode's own retry before restarting."
        continue
    }

    Write-WatchdogLog "$RetryThreshold stream failures occurred within $RetryWindowSeconds seconds. Recovering session."
    $errorTimes.Clear()

    Stop-ProcessTree -PidToStop $process.Id
    try { $process.WaitForExit(5000) | Out-Null } catch {}

    $now = Get-Date
    for ($i = $restartTimes.Count - 1; $i -ge 0; $i--) {
        if (($now - $restartTimes[$i]).TotalMinutes -gt 10) {
            $restartTimes.RemoveAt($i)
        }
    }

    if ($restartTimes.Count -ge $MaxRestartsPer10Min) {
        Write-WatchdogLog "Too many restarts in 10 minutes. Automatic recovery stopped."
        break
    }

    $restartTimes.Add((Get-Date))
    Start-Sleep -Seconds 2
    $process = Start-OpenCodeWindow -Resume:$true
}
