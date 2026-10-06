@echo off
rem d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -t # ">d:\mosquitto\wheelly.log
rem d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -t +/wheellycam/#
rem d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -t +/wheelly/#
rem d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -t +/wheelly/+/v2/rg
rem d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -t +/wheelly/+/v2/$1

set ID=#

if not "%~1" == "" (
    set ID=%~1
)
@echo on
d:\mosquitto\mosquitto_sub -u wheelly -P wheelly -v -t +/wheelly/+/v2/%ID%
