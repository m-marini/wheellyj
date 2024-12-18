rmdir /S /Q  batch
rmdir /S /Q  csv
rmdir /S /Q  report

call bin\train.cmd -k batch -w data

call bin\report.cmd -p batch model csv

cd octave
"C:\Program Files\GNU Octave\Octave-7.2.0\octave-launch" --no-gui generateReport1.m
cd ..