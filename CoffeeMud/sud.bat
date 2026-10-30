@echo off
REM Play the single-player game in this console. Extra NAME=VALUE arguments
REM override coffeemud.ini. Builds first if needed; run build.bat again
REM after pulling changes.
cd /d "%~dp0"
if not exist com\planet_ink\coffee_mud\application\SUD.class call build.bat || exit /b 1
java -classpath ".;.\lib\js.jar;.\lib\jzlib.jar" -Djava.awt.headless=true -Xmx1g com.planet_ink.coffee_mud.application.SUD %*
