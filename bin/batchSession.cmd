call bin\setConfig.cmd

rmdir /S /Q  %KPIS%
rmdir /S /Q  %CSVS%
rmdir /S /Q  %REPORT%

call bin\train.cmd -k %KPIS% -w -t %TEMP% %INFERENCE%

call bin\report.cmd -p %KPIS% %MODEL% %CSVS%

cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui generateReport1.m
cd ..