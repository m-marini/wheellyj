call bin\setConfig.cmd

rmdir /S /Q  %DATA%
rmdir /S /Q  %CSVS%%
rmdir /S /Q  %REPORT%

rem  Run session
call bin\wheelly.cmd -k %DATA% -t 21600 -a -w -s

rem Compute report data
call bin\report.cmd -p %DATA% %MODEL% %CSVS%

rem Generate report document
cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui generateReport1.m
cd ..

rem Merge data files
if not exist  %MERGE% (
    md %MERGE%
    xcopy /s %DATA% %MERGE%
    echo Data copied into %MERGE%.
) else (
   bin\runnit.cmd org.mmarini.wheelly.apps.MergeKpis -a -p %MERGE% %DATA%
   echo Data merged into %MERGE%.
)
