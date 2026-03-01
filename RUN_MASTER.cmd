@echo off
setlocal
cd /d "%~dp0local\master"

set "JDK21=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
if exist "%JDK21%\bin\java.exe" (
  set "JAVA_HOME=%JDK21%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
)

if not exist "paper-1.21.4.jar" (
  echo paper-1.21.4.jar not found in %cd%
  echo Download Paper 1.21.4 and place it here, then run again.
  pause
  exit /b 1
)

java -Xms1G -Xmx1G -jar paper-1.21.4.jar --nogui
