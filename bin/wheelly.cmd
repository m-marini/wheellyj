@echo off
call bin\setConfig.cmd
rmdir /S /Q  %TEMP%
call bin\runnit.cmd org.mmarini.wheelly.apps.Wheelly -a -w -i %INFERENCE%