@echo off
rem Batch file to run on Windows

rem Remove "rem" from following two lines, if you'd like to use j2sdk.
rem set JAVA_HOME=...
rem set PATH=%JAVA_HOME%\bin

rem run
rem cd ..
echo 
rem start javaw -jar "lib/${pom.build.finalName}.jar"
rem java -jar "lib/${pom.build.finalName}.jar" %1 %2
java -version > nul
IF ERRORLEVEL 2 goto noJavaw
javaw > nul
IF ERRORLEVEL 2 goto noJavaw

rem java -cp "lib/*;../classes" org.mmarini.wheelly.apps.RobotExecutor %1 %2 %3 %4 %5 %6 %7 %8
java -cp "lib/*;../classes" %1 %2 %3 %4 %5 %6 %7 %8
goto end

:noJavaw
echo.
echo Failed to run java.
echo Java runtime environment is required to run the application.
echo Setup Java environment at first.
echo.
echo The java command should be in PATH system environment variable.
echo.
echo If you would like to run java in your specified folder, you can edit runnit.bat
echo setting your JAVA_HOME as followings.
echo     before:
echo       rem set JAVA_HOME=...
echo       rem set PATH=%%JAVA_HOME%%\bin
echo     after:
echo       set JAVA_HOME=...
echo       set PATH=%%JAVA_HOME%%\bin
echo.
pause
goto end

:end