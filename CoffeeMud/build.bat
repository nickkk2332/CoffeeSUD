@echo off
REM Compiles every source file in place. CMClass finds game classes by
REM scanning the package folders, so the output must sit next to the
REM sources. It always starts clean, so that no class file from a deleted
REM source is left behind to be loaded. Takes about a minute.
cd /d "%~dp0"
del /s /q com\*.class > /dev/null 2>&1
dir /s /b com\*.java > .sources.list
javac -nowarn -Xlint:none -g -encoding UTF-8 -J-Xmx2g -classpath ".;.\lib\js.jar;.\lib\jzlib.jar" -d . @.sources.list
set STATUS=%ERRORLEVEL%
del .sources.list
if %STATUS% neq 0 (echo Build FAILED.) else (echo Build OK.)
exit /b %STATUS%
