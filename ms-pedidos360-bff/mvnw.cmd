@echo off
rem Maven Wrapper simplificado (no necesita la carpeta .mvn).
rem Usa "mvn" si esta instalado; si no, descarga Maven una sola vez en %USERPROFILE%\.m2\wrapper.
setlocal
set MAVEN_VERSION=3.9.9

where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  call mvn %*
  exit /b %ERRORLEVEL%
)

set "MAVEN_DIR=%USERPROFILE%\.m2\wrapper\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_DIR%\bin\mvn.cmd" (
  echo Descargando Maven %MAVEN_VERSION% ...
  powershell -NoProfile -Command "$ErrorActionPreference=Stop; $d=Join-Path $env:USERPROFILE .m2\wrapper; New-Item -ItemType Directory -Force $d | Out-Null; $z=Join-Path $d maven.zip; Invoke-WebRequest https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip -OutFile $z; Expand-Archive $z $d -Force; Remove-Item $z"
  if errorlevel 1 exit /b 1
)

call "%MAVEN_DIR%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
