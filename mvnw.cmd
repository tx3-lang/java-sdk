@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "PROPERTIES=%PROJECT_DIR%.mvn\wrapper\maven-wrapper.properties"
set "WRAPPER_JAR=%PROJECT_DIR%.mvn\wrapper\maven-wrapper.jar"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Error: JAVA_HOME must point to a JDK. 1>&2
  exit /b 1
)

for /f "tokens=1,* delims==" %%A in (%PROPERTIES%) do (
  if "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
  if "%%A"=="wrapperSha256Sum" set "WRAPPER_SHA=%%B"
)

if not exist "%WRAPPER_JAR%" (
  powershell -NoProfile -Command "$ErrorActionPreference='Stop'; (New-Object Net.WebClient).DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')"
  if errorlevel 1 exit /b 1
)

for /f %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%WRAPPER_JAR%').Hash.ToLower()"') do set "ACTUAL_SHA=%%H"
if not "%ACTUAL_SHA%"=="%WRAPPER_SHA%" (
  echo Error: Maven Wrapper checksum mismatch. 1>&2
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" %MAVEN_OPTS% -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%PROJECT_DIR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
