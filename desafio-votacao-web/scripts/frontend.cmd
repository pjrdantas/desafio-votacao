@echo off
setlocal
set "frontendTask=%~1"
if "%frontendTask%"=="" set "frontendTask=start"
if /i "%frontendTask%"=="start" goto run
if /i "%frontendTask%"=="build" goto run
if /i "%frontendTask%"=="test" goto run
if /i "%frontendTask%"=="install" goto run
echo Uso: frontend.cmd [start^|build^|test^|install]
exit /b 1

:run
if exist "%~dp0..\.tools\node-v24.15.0-win-x64\node.exe" set "PATH=%~dp0..\.tools\node-v24.15.0-win-x64;%PATH%"
where node >nul 2>nul
if errorlevel 1 (
  echo Instale Node.js 24.15 ou superior da serie 24.
  exit /b 1
)
pushd "%~dp0.."
if errorlevel 1 exit /b 1
if /i "%frontendTask%"=="install" (
  call npm.cmd ci
) else (
  call npm.cmd run %frontendTask%
)
set "frontendExitCode=%ERRORLEVEL%"
popd
exit /b %frontendExitCode%