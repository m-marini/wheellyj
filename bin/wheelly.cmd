@echo off
call bin\setConfig.cmd
rmdir /S /Q  %TEMP%
rmdir /S /Q %KPIS%
call bin\runnit.cmd org.mmarini.wheelly.apps.Wheelly -k %KPIS% -w -i %INFERENCE%