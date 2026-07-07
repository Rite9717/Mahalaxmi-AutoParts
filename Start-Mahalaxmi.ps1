$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Backend = Join-Path $Root "backend"
$JavaExe = "C:\Program Files\Java\jdk-21.0.11\bin\java.exe"
$AppUrl = "http://localhost:8080/?v=$(Get-Date -Format 'yyyyMMddHHmmss')"
$HealthUrl = "http://localhost:8080/api/health"
$BackendJar = Join-Path $Backend "target\autoparts-web-0.1.0.jar"
$BackendLog = Join-Path $Root "backend-desktop.log"
$BackendErrLog = Join-Path $Root "backend-desktop.err.log"
$BackendLauncher = Join-Path $Root "Launch-Mahalaxmi-Backend.cmd"
$NetworkAccessInstaller = Join-Path $Root "Install-Network-Access.ps1"
$FirewallRuleName = "Mahalaxmi Auto Parts Web"

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

function Test-NetworkAccessRule {
    try {
        $output = & netsh advfirewall firewall show rule name="$FirewallRuleName" 2>$null
        return ($LASTEXITCODE -eq 0 -and ($output -join "`n") -match "LocalPort:\s*8080")
    } catch {
        return $false
    }
}

function Ensure-NetworkAccess {
    if (Test-NetworkAccessRule) {
        return
    }
    if (Test-Path $NetworkAccessInstaller) {
        $result = [System.Windows.Forms.MessageBox]::Show(
            "Laptop/phone access is not enabled yet.`n`nWindows will ask for admin permission once to make it permanent.",
            "Mahalaxmi Auto Parts",
            [System.Windows.Forms.MessageBoxButtons]::OKCancel,
            [System.Windows.Forms.MessageBoxIcon]::Information
        )
        if ($result -eq [System.Windows.Forms.DialogResult]::OK) {
            Start-Process -FilePath powershell.exe -Verb RunAs -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$NetworkAccessInstaller`"" -Wait
        }
    }
}

Ensure-NetworkAccess

if (-not (Test-Port 8080)) {
    if (-not (Test-Path $JavaExe)) {
        [System.Windows.Forms.MessageBox]::Show("Java 21 was not found at:`n$JavaExe`n`nPlease install Java 21 or update Start-Mahalaxmi.ps1 with the correct Java path.", "Mahalaxmi Auto Parts")
        exit 1
    }
    if (-not (Test-Path $BackendJar)) {
        [System.Windows.Forms.MessageBox]::Show("Backend jar was not found at:`n$BackendJar`n`nPlease rebuild the software first.", "Mahalaxmi Auto Parts")
        exit 1
    }
    if (-not (Test-Path $BackendLauncher)) {
        [System.Windows.Forms.MessageBox]::Show("Backend launcher was not found at:`n$BackendLauncher", "Mahalaxmi Auto Parts")
        exit 1
    }
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", "`"$BackendLauncher`"" -WindowStyle Hidden
}

if (Wait-ForUrl $HealthUrl 90) {
    Open-AppWindow $AppUrl
} else {
    $errorText = ""
    if (Test-Path $BackendErrLog) {
        $errorText = (Get-Content $BackendErrLog -Tail 12) -join "`n"
    }
    if ([string]::IsNullOrWhiteSpace($errorText)) {
        $errorText = "Backend did not become ready on port 8080 within 90 seconds."
    }
    [System.Windows.Forms.MessageBox]::Show("Mahalaxmi Auto Parts could not start.`n`n$errorText", "Mahalaxmi Auto Parts")
    exit 1
}
