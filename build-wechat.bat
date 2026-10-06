@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "NODE_ROOT=%~dp0tools\node-v24.21.0-win-x64"
echo 正在构建微信小程序...
"%NODE_ROOT%\node.exe" "%NODE_ROOT%\node_modules\npm\bin\npm-cli.js" run build:mp-weixin
if %errorlevel% neq 0 (
  echo 构建失败，请查看上面的错误信息。
) else (
  echo 构建完成：%~dp0dist\build\mp-weixin
)
pause
