@echo off
setlocal
set "JAVA_EXE=C:\Program Files\Java\jdk-21.0.11\bin\javaw.exe"
set "JAR_PATH=E:\Ritesh\backend\target\autoparts-web-0.1.0.jar"
start "" "%JAVA_EXE%" -jar "%JAR_PATH%"
endlocal
