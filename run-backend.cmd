@echo off
setlocal
set "JAVA_EXE=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe"
set "JAR_PATH=E:\Ritesh\backend\target\autoparts-web-0.1.0.jar"
start "" "%JAVA_EXE%" -jar "%JAR_PATH%"
endlocal
