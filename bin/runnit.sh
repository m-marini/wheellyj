!/bin/sh
# $Id: runnit.sh,v 1.2 2008/02/27 15:00:25 marco Exp $
# Remove "#" from following two lines, if you'd like to use j2sdk.
# set JAVA_HOME=C:\j2sdk1.4.2_08
# set PATH=%JAVA_HOME%\bin

# run
cd ..
java -jar "lib/wheelly-0.22.0.jar" $1 $2
cd bin
