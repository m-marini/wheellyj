call bin\setConfig.cmd

type null > .running

:run

if not exist ".running" goto end

rmdir /S /Q  %KPIS%
rmdir /S /Q  %CSVS%%
rmdir /S /Q  %REPORT%

rem  Run session
rem old call bin\runnit.cmd org.mmarini.wheelly.apps.Wheelly -k %KPIS% -w -s -t 21600 -i %INFERENCE%
call bin\runnit.cmd org.mmarini.wheelly.apps.Wheelly -k %KPIS% -w -s -t 172800 -i %INFERENCE%

rem
rem Create report data
rem
call bin\report.cmd -p %KPIS% %CSVS%

rem
rem Create report document
rem
cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui dlReport.m
cd ..

goto run

:end