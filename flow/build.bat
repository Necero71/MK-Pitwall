@echo off
rem Builds Flow and copies the APKs into .\dist
cd /d "%~dp0"

if not exist gradlew.bat (
  for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\*") do (
    call "%%D\gradle-8.9\bin\gradle.bat" wrapper --gradle-version 8.9 -q
  )
)

cd /d "%~dp0"
call "%~dp0gradlew.bat" assembleDebug assembleRelease --console=plain
if errorlevel 1 (
  echo BUILD FAILED
  exit /b 1
)

if not exist dist mkdir dist
if exist "%TEMP%\flowtv-build\app\outputs\apk" for /r "%TEMP%\flowtv-build\app\outputs\apk" %%F in (*.apk) do copy /y "%%F" dist\ >nul
if exist "app\build\outputs\apk" for /r "app\build\outputs\apk" %%F in (*.apk) do copy /y "%%F" dist\ >nul
dir /b dist
echo BUILD OK
