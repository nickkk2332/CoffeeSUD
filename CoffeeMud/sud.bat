@echo off
setlocal
REM Play the single-player game in this console.  Extra NAME=VALUE arguments
REM override coffeemud.ini.  Builds first if needed; run build.bat again
REM after pulling changes.
cd /d "%~dp0"
call findjava.bat
if errorlevel 1 exit /b 1
if not exist com\planet_ink\coffee_mud\application\SUD.class (
	call build.bat
	if errorlevel 1 exit /b 1
)
"%JAVA%" -classpath ".;lib\js.jar;lib\jzlib.jar" -Djava.awt.headless=true -Xmx1g com.planet_ink.coffee_mud.application.SUD %*
