@echo off
rem SPDX-License-Identifier: Apache-2.0
rem Launch one demo from the extracted distribution, preserving optional arguments.
setlocal
cd /d "%~dp0"
call "%~dp0bin\Valthorne-examples.bat" @DEMO_ID@ %*
set "demo_exit=%ERRORLEVEL%"
rem Keep errors visible for double-click launches; automated runs pass arguments.
if not "%demo_exit%"=="0" if "%~1"=="" pause
exit /b %demo_exit%
