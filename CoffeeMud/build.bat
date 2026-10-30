@echo off
setlocal EnableDelayedExpansion
REM Compiles every source file in place.  CMClass finds game classes by
REM scanning the package folders, so the output must sit next to the
REM sources.  It always starts clean, so that no class file from a deleted
REM source is left behind to be loaded.  Takes about a minute.
cd /d "%~dp0"
call findjava.bat
if errorlevel 1 exit /b 1
echo Compiling with "%JAVAC%"
del /s /q com\*.class > nul 2>&1
REM javac reads each quoted path in the list with backslash as an escape
REM character, so backslashes are doubled; quoting keeps spaces and
REM parentheses in the folder's path safe.
(for /r com %%F in (*.java) do (
	set "P=%%F"
	echo "!P:\=\\!"
)) > .sources.list
"%JAVAC%" -nowarn -Xlint:none -g -encoding UTF-8 -J-Xmx1g -classpath ".;lib\js.jar;lib\jzlib.jar" -d . @.sources.list
set STATUS=!ERRORLEVEL!
del .sources.list
if !STATUS! neq 0 (echo Build FAILED.) else (echo Build OK.)
exit /b !STATUS!
