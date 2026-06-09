@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$root='%~dp0'.TrimEnd('\'); $desktop=[Environment]::GetFolderPath('Desktop'); $shortcut=(New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $desktop 'Mahalaxmi Auto Parts.lnk')); $shortcut.TargetPath=(Join-Path $root 'Start Mahalaxmi Auto Parts.cmd'); $shortcut.WorkingDirectory=$root; $shortcut.IconLocation=(Join-Path $root 'mahalaxmi-auto-parts.ico'); $shortcut.Save(); Write-Host 'Desktop shortcut created: Mahalaxmi Auto Parts'"
pause
endlocal
