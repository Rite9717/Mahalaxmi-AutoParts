$ErrorActionPreference = "Stop"

Write-Host "=== Starting Development Mode ===" -ForegroundColor Cyan
Write-Host "This will run frontend and backend separately for faster development." -ForegroundColor Yellow
Write-Host ""

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

# Start backend with auto-reload
Write-Host "Starting backend with hot-reload..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$Root\backend'; .\mvnw.cmd spring-boot:run"

# Wait a bit for backend to initialize
Start-Sleep -Seconds 3

# Start frontend dev server
Write-Host "Starting frontend dev server..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$Root\front'; npm start"

Write-Host ""
Write-Host "Development servers starting..." -ForegroundColor Cyan
Write-Host "Backend: http://localhost:8080" -ForegroundColor Yellow
Write-Host "Frontend: http://localhost:3000 (will open automatically)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Changes to frontend code will auto-reload in browser." -ForegroundColor Green
Write-Host "Changes to backend code will auto-reload on save." -ForegroundColor Green
Write-Host ""
Write-Host "Press Enter to continue..." -ForegroundColor Cyan
Read-Host
