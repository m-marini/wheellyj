call bin\setConfig.cmd

rmdir /S /Q  %KPIS%
rmdir /S /Q  %CSVS%%
rmdir /S /Q  %REPORT%

rem  Run session
call bin\wheelly.cmd -k %KPIS% -t 21600 -a -w -s -i %INFERENCE%

rem Compute report data
call bin\report.cmd -p %DATA% %MODEL% %CSVS%

rem Generate report document
cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui generateReport1.m
cd ..
