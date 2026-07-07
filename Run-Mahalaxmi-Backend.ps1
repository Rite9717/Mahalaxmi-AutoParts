$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$JavaExe = "C:\Program Files\Java\jdk-21.0.11\bin\java.exe"
$JarPath = Join-Path $Root "backend\target\autoparts-web-0.1.0.jar"
$OutLog = Join-Path $Root "backend-desktop.log"
$ErrLog = Join-Path $Root "backend-desktop.err.log"

Add-Type @"
using System;
using System.Runtime.InteropServices;

public static class SleepGuard {
    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern uint SetThreadExecutionState(uint esFlags);
}
"@

$ES_CONTINUOUS = [uint32]0x80000000
$ES_SYSTEM_REQUIRED = [uint32]0x00000001
$ES_AWAYMODE_REQUIRED = [uint32]0x00000040

Set-Content -Path $OutLog -Value ""
Set-Content -Path $ErrLog -Value ""

if (-not (Test-Path $JavaExe)) {
    throw "Java was not found at $JavaExe"
}
if (-not (Test-Path $JarPath)) {
    throw "Backend jar was not found at $JarPath"
}

try {
    [SleepGuard]::SetThreadExecutionState($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED -bor $ES_AWAYMODE_REQUIRED) | Out-Null
    & $JavaExe -jar $JarPath >> $OutLog 2>> $ErrLog
} finally {
    [SleepGuard]::SetThreadExecutionState($ES_CONTINUOUS) | Out-Null
}
