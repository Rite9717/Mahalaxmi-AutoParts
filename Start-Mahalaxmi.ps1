$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Backend = Join-Path $Root "backend"
$AppUrl = "http://localhost:8080/?v=$(Get-Date -Format 'yyyyMMddHHmmss')"
$HealthUrl = "http://localhost:8080/api/health"

function Test-Port($Port) {
    try {
        $client = New-Object Net.Sockets.TcpClient
        $connect = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $connect.AsyncWaitHandle.WaitOne(300)
        if ($ok) {
            $client.EndConnect($connect)
        }
        $client.Close()
        return $ok
    } catch {
        return $false
    }
}

function Wait-ForUrl($Url, $Seconds) {
    $end = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $end) {
        try {
            Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2 | Out-Null
            return $true
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    return $false
}

function Open-AppWindow($Url) {
    $edgePaths = @(
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
    )
    $chromePaths = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe"
    )

    $browser = @($edgePaths + $chromePaths) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if ($browser) {
        Start-Process -FilePath $browser -ArgumentList "--app=$Url"
    } else {
        Start-Process $Url
    }
}

if (-not (Test-Port 8080)) {
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "java -jar `"$Backend\target\autoparts-web-0.1.0.jar`" > `"$Root\backend-desktop.log`" 2> `"$Root\backend-desktop.err.log`"" `
        -WindowStyle Hidden
}

Wait-ForUrl $HealthUrl 60 | Out-Null
Open-AppWindow $AppUrl
