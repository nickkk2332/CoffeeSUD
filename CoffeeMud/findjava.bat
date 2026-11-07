@echo off
REM Sets JAVAC and JAVA to a Java compiler and runtime that actually run.
REM Tries the JDK named by JAVA_HOME first, then every javac on PATH in
REM order.  Used by build.bat and sud.bat; exits with an error and an
REM explanation if no working JDK is found.
set "JAVAC="
set "JAVA="
set "TRIED="
if not defined JAVA_HOME goto :frompath
set "JH=%JAVA_HOME:"=%"
if not exist "%JH%\bin\javac.exe" goto :badhome
"%JH%\bin\javac.exe" -version > nul 2>&1 && set "JAVAC=%JH%\bin\javac.exe"
if not defined JAVAC set "TRIED=%JH%\bin\javac.exe"
:frompath
if defined JAVAC goto :found
for /f "delims=" %%J in ('where javac 2^>nul') do (
	if not defined JAVAC (
		"%%J" -version > nul 2>&1 && set "JAVAC=%%J"
		if not defined JAVAC set "TRIED=%%J"
	)
)
if not defined JAVAC goto :nojdk
:found
for %%D in ("%JAVAC%") do set "JAVA=%%~dpDjava.exe"
if not exist "%JAVA%" set "JAVA=java"
exit /b 0

:badhome
echo JAVA_HOME is set to "%JH%", but there is no bin\javac.exe in that folder.
echo Looking for javac on PATH instead.
goto :frompath

:nojdk
REM no parenthesized blocks here: a path such as "Program Files (x86)" would end them early
if not defined TRIED goto :nojavac
echo Found javac at "%TRIED%", but it does not run.  This is usually a link
echo left behind by an updated or uninstalled Java.
goto :advice
:nojavac
echo Could not find javac, the Java compiler.
:advice
echo Install a JDK, version 9 or later (the README's minimum), then set JAVA_HOME to its folder,
echo for example:  setx JAVA_HOME "C:\Program Files\Java\jdk-21"
echo and open a new command window.
exit /b 1
