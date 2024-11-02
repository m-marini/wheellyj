rmdir /S /Q  data
rmdir /S /Q  csv
rmdir /S /Q  report

call bin\wheelly.cmd -k data -t 3600 -w -a -s

call bin\report.cmd  -p data csv

cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui generateReport1.m
cd ..