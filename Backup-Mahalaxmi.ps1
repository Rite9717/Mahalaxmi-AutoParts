$backupDir = "E:\Ritesh\Mahalaxmi-Backups"
$date = Get-Date -Format "yyyy-MM-dd_HH-mm"
$dbBackup = "$backupDir\mahalaxmi_web_$date.sql"
$errorLog = "$backupDir\mahalaxmi_web_$date.err.txt"
$mysqldump = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe"

New-Item -ItemType Directory -Force $backupDir | Out-Null

& $mysqldump -u root -proot mahalaxmi_web 2> $errorLog | Out-File -FilePath $dbBackup -Encoding utf8

if ($LASTEXITCODE -ne 0 -or !(Test-Path $dbBackup) -or (Get-Item $dbBackup).Length -eq 0) {
  throw "Database backup failed. Check MySQL is running and password is correct."
}

Compress-Archive -Path $dbBackup -DestinationPath "$backupDir\mahalaxmi_web_$date.zip" -Force
Remove-Item $dbBackup
Remove-Item $errorLog -ErrorAction SilentlyContinue

Get-ChildItem $backupDir -Filter "*.zip" |
  Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-30) } |
  Remove-Item -Force
