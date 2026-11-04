@echo off

set TOPICS=#

if not "%~1" == "" (
    set TOPICS="+/wheelly/+/v2/%~1"
)

@echo on
d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -F "%%I %%t %%p" -t %TOPICS%
