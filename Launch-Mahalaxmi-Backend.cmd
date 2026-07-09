@echo off
setlocal
set "JAVA_EXE=C:\Program Files\Java\jdk-21.0.11\bin\java.exe"
set "JAR_PATH=E:\Ritesh\backend\target\autoparts-web-0.1.0.jar"
set "OUT_LOG=E:\Ritesh\backend-desktop.log"
set "ERR_LOG=E:\Ritesh\backend-desktop.err.log"
for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v OPENAI_API_KEY 2^>nul ^| find "OPENAI_API_KEY"') do set "OPENAI_API_KEY=%%B"

break > "%OUT_LOG%"
break > "%ERR_LOG%"
"%JAVA_EXE%" -jar "%JAR_PATH%" >> "%OUT_LOG%" 2>> "%ERR_LOG%"
endlocal
