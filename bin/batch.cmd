call bin\setConfig.cmd

if exist %TEMP%\ goto train
call bin\runnit.cmd org.mmarini.wheelly.apps.CreateDatasets -t %TEMP% %INFERENCE%

:train
call bin\runnit.cmd org.mmarini.wheelly.apps.BatchTraining -u %TEMP%

rem call bin\train.cmd -k %KPIS% -w -t %TEMP% %INFERENCE%
rem call bin\report.cmd -p %KPIS% %MODEL% %CSVS%

rem cd octave
rem "C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui generateReport1.m
rem cd ..