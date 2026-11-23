@echo off

set TOPICS=#

if not "%~1" == "" (
rem     set TOPICS="+/wheelly/+/v2/%~1"
     set TOPICS="%~1"
)

@echo on
d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -F "%%I | %%t | length: %%l\n    %%x" -t %TOPICS%
