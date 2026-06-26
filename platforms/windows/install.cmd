@echo off
setlocal
set "SRC=%~dp0app"
set "DEST=%LOCALAPPDATA%\VPN BOSS Windows"

if not exist "%DEST%" mkdir "%DEST%"
xcopy "%SRC%\*" "%DEST%\" /E /I /Y >nul

reg add "HKCU\Software\Classes\vpnboss" /ve /d "URL:VPN BOSS Protocol" /f >nul
reg add "HKCU\Software\Classes\vpnboss" /v "URL Protocol" /d "" /f >nul
reg add "HKCU\Software\Classes\vpnboss\shell\open\command" /ve /d "\"%DEST%\VPN BOSS Windows.exe\" \"%%1\"" /f >nul

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop') + '\VPN BOSS Windows.lnk');" ^
  "$s.TargetPath='%DEST%\VPN BOSS Windows.exe';" ^
  "$s.WorkingDirectory='%DEST%';" ^
  "$s.Save()"

start "" "%DEST%\VPN BOSS Windows.exe"
exit /b 0
