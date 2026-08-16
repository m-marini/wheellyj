call bin\setConfig.cmd

rem
rem Convert inference into training data
rem
if exist %TEMP%\ goto train
call bin\runnit.cmd org.mmarini.wheelly.apps.CreateDatasets -t %TEMP% %INFERENCE%

:train

type null > .running

:run

if not exist ".running" goto end

rem
rem Run batch training
rem
rmdir /S /Q %KPIS%
call bin\runnit.cmd org.mmarini.wheelly.apps.BatchTraining -u -k %KPIS% %TEMP%

rem
rem Create report data
rem
call bin\report.cmd -p -r %TEMP%/rewards.bin %KPIS% %CSVS%

rem
rem Create report document
rem
cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui dlReport.m
cd ..

goto run

:end