@echo off

set ID=#

if not "%~1" == "" (
    set ID=%~1
)
@echo on
d:\mosquitto\mosquitto_pub -u wheelly -P wheelly -t cmd/wheelly/e05a1b66f89c/v2/%ID% -l
