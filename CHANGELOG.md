# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] 2023-01-15

### Added

- Issue #143: Add sensors check and wheellino 0.3.0 messaging

### Changed

- Issue #146: Change explorer agent behavior
- Issue #148: Update radar map on contacts signals
- Issue #149: Rotate robot in avoiding behavior
- Issue #151: Move robot ahead when exploring
- Issue #152: Sector unknown on partial empty in polar map

## [0.4.2] 2023-01-08

### Added

- Issue #130: Add weights constraint
- Issue #137: Robot executor
- Issue #139: Imu failure report
- Issue #141: Add radar sensitivity distance

## [0.4.1] 2022-11-26

### Added

- Issue #108: Add complete mechanical test
- Issue #113: Add delta critic kpi
- Issue #116: Add explore objective
- Issue #119: Add sensor direction in explore objective
- Issue #121: Add radar polar map
- Issue #125: Move HUD opposite robot corner

### Removed:

- Issue #126: Remove default values in yaml

### Fixed

- Issue #112: Sector overlap on radar view bug
- Issue #123: Set distance limit to polar map

## [0.4.0] 2022-11-09

### Added

- Issue #78: Add radar map

## [0.3.1] 2022-11-09

### Added

- Issue #102: delta weights dashboard

### Changed

- Issue #109: Analysis of performance

### Fixed

- Issue #104: Missing kpi on backpressure
- Issue #106: Clock time error

## [0.3.0] 2022-10-23

### Added

- Issue #76: Java TD environment porting from python
- Issue #79: Agent spec transpiller
- Issue #80: Robot environment with discrete actions
- Issue #84: Generate kpis
- Issue #85: Add WiFi configuration
- Issue #89: Add state tiles coding
- Issue #91: Add partition processor
- Issue #98: Add weight's changes kpi
 
### Changed

- Issue #71: Halt goal behaviour

### Removed

- Issue #73: KC folder moved to wheellino repo
- Issue #100: remove deepl impl

### Fixed

- Issue #87: Robot out of area
- Issue #93: Check for forward move

## [0.2.0] 2022-06-09

### Added

- Issue #65: Add Reinforcement Leearning interfaces with Deep learning library
- Issue #69: RL engine replay with delta direction instead of absolute direction

## [0.1.2] 2022-05-18

### Changed

- Issue #67: Change motor power signals to speed signals

## [0.1.1] 2022-05-16

### Added

- Issue #63: Add follower behaviour

### Changed

 - Issue #61: Change status LEDs with multicolor LED

## [0.1.0] 2022-05-14

### Added

- Issue #2: Manage motor pulse in arduino
- Issue #3: Add Led image
- Issue #6: Remove shift latch
- Issue #8: Add PWM motors control
- Issue #11: Add gyroscope and accelerometer for motion traking
- Issue #13: Add direct socket connection
- Issue #16: Motion control through trim
- Issue #18: Automatic send of status and asset
- Issue #19: Speed sensor management
- Issue #22: Change messaging
- Issue #25: Adjust increasing reliability of movement algorithm
- Issue #24: Create a proxy map by robot asset
- Issue #27: Add the gyroscope data asset
- Issue #31: Add map display with robot and obstacles, create base engine to manage different automa behavior
- Issue #29  Create automa behavior
- Issue #33: Arrange the obstacles in a grid
- Issue #39: Merge pr event and st event
- Issue #41: Move the motion control in Wheelly
- Issue #43: Compute best path
- Issue #44: Optimize best path
- Issue #47: Add map mode view
- Issue #49: Add contact sensors
- Issue #50: Select random target
- Issue #51: Add select nearest safe location
- Issue #53: Add speed feedback to motor controller
- Issue #57: Add behavior to follow a defined path
- Issue #58: Let avoid to behave disengaging and moving to safe location
- Issue #59: Add random scan

### Changed

- Issue #36: Optimized IMU
- Issue #39: Merge pr event and st events

### Fixed

- Issue #1: Add missing connection management
- Issue #9: Serialize comunication with API
- Issue #26: Fix comunication bugs due to loss of signal
- Issue #32: Scanner map should add obstacles at echo location
- Issue #45: Wheelly crash bug
