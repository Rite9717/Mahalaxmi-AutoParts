# Stop backend
Write-Host "Stopping backend..." -ForegroundColor Yellow
Get-Process java -ErrorAction SilentlyContinue | Where-Object { (netstat -ano | Select-String ":8080.*LISTENING" | Select-String $_.Id) } | Stop-Process -Force
Start-Sleep -Seconds 2

# Rebuild JAR
Write-Host "Building new JAR..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot\backend"
& .\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    pause
    exit 1
}

# Start backend
Write-Host "Starting backend..." -ForegroundColor Green
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "java -jar `"$PSScriptRoot\backend\target\autoparts-web-0.1.0.jar`" > `"$PSScriptRoot\backend-desktop.log`" 2> `"$PSScriptRoot\backend-desktop.err.log`"" `
    -WindowStyle Hidden

Write-Host "Backend is starting... waiting for health check..." -ForegroundColor Green
$healthUrl = "http://localhost:8080/api/health"
$timeout = 60
$elapsed = 0
while ($elapsed -lt $timeout) {
    try {
        Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
        Write-Host "Backend is ready!" -ForegroundColor Green
        Start-Sleep -Seconds 1
        Start-Process "http://localhost:8080"
        exit 0
    } catch {
        Start-Sleep -Seconds 1
        $elapsed++
    }
}

Write-Host "Backend failed to start within $timeout seconds" -ForegroundColor Red
pause
