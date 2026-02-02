@echo off
call bin\setConfig.cmd
rmdir /S /Q  %TEMP%
call bin\runnit.cmd org.mmarini.wheelly.apps.RobotExecutor -w -d %INFERENCE% %*