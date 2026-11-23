@echo off
rem Minimal Maven bootstrap for Windows: uses Maven from PATH if present, otherwise downloads Apache Maven once into .mvn\
rem (needs JAVA_HOME or java on PATH). Usage:  .\mvnw.cmd spring-boot:run
setlocal
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  call mvn %*
  exit /b %ERRORLEVEL%
)
set "MVN_VERSION=3.9.9"
set "MVN_HOME=%~dp0.mvn\apache-maven-%MVN_VERSION%"
if not exist "%MVN_HOME%\bin\mvn.cmd" (
  echo Downloading Apache Maven %MVN_VERSION% ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $zip=Join-Path '%~dp0.mvn' 'maven.zip'; Invoke-WebRequest -UseBasicParsing 'https://archive.apache.org/dist/maven/maven-3/%MVN_VERSION%/binaries/apache-maven-%MVN_VERSION%-bin.zip' -OutFile $zip; Expand-Archive -Force $zip '%~dp0.mvn'; Remove-Item $zip"
  if errorlevel 1 (
    echo Could not download Maven. Install it with:  winget install Apache.Maven
    exit /b 1
  )
)
call "%MVN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
