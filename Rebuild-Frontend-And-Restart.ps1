$ErrorActionPreference = "Stop"

Write-Host "=== Rebuilding Frontend and Backend ===" -ForegroundColor Cyan

# Stop backend
Write-Host "`n1. Stopping backend..." -ForegroundColor Yellow
Get-Process java -ErrorAction SilentlyContinue | Where-Object { 
    (netstat -ano | Select-String ":8080.*LISTENING" | Select-String $_.Id) 
} | Stop-Process -Force
Start-Sleep -Seconds 2

# Build frontend
Write-Host "`n2. Building frontend..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot\front"
& npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Host "Frontend build failed!" -ForegroundColor Red
    pause
    exit 1
}

# Copy frontend build to backend resources
Write-Host "`n3. Copying frontend to backend resources..." -ForegroundColor Yellow
$frontendBuild = "$PSScriptRoot\front\build"
$backendStatic = "$PSScriptRoot\backend\src\main\resources\static"

if (Test-Path $backendStatic) {
    Remove-Item -Recurse -Force $backendStatic
}
Copy-Item -Recurse -Force $frontendBuild $backendStatic

# Build backend JAR
Write-Host "`n4. Building backend JAR..." -ForegroundColor Yellow
Set-Location "$PSScriptRoot\backend"
& .\mvnw.cmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "Backend build failed!" -ForegroundColor Red
    pause
    exit 1
}

# Start backend
Write-Host "`n5. Starting backend..." -ForegroundColor Green
Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/c", "java -jar `"$PSScriptRoot\backend\target\autoparts-web-0.1.0.jar`" > `"$PSScriptRoot\backend-desktop.log`" 2> `"$PSScriptRoot\backend-desktop.err.log`"" `
    -WindowStyle Hidden

# Wait for backend to be ready
Write-Host "`n6. Waiting for backend to start..." -ForegroundColor Green
$healthUrl = "http://localhost:8080/api/health"
$timeout = 60
$elapsed = 0
while ($elapsed -lt $timeout) {
    try {
        Invoke-WebRequest -Uri $healthUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
        Write-Host "`nBackend is ready!" -ForegroundColor Green
        Write-Host "Opening application..." -ForegroundColor Green
        Start-Sleep -Seconds 1
        Start-Process "http://localhost:8080/?v=$(Get-Date -Format 'yyyyMMddHHmmss')"
        exit 0
    } catch {
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 1
        $elapsed++
    }
}

Write-Host "`nBackend failed to start within $timeout seconds" -ForegroundColor Red
pause
