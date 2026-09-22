$base = "http://192.168.0.2:3000"
$head = "123"
$results = @()
try {
    for ($x = 1; $x -le 25; $x++) {
        $ip = "192.168.0.$x"
        $healthUrl = "http://$ip:3000/api/health"
        $resp = Invoke-WebRequest -Uri $healthUrl -Method GET -TimeoutSec 3 -UseBasicParsing
        $results += [PSCustomObject]@{ IP = $ip; Reachable = $resp.StatusCode }
    }
} catch {}
$results | Select-Object IP, Reachable | Format-Table -AutoSize
foreach ($o in $results) { Write-Output "$($o.IP) => $($o.Reachable)" }