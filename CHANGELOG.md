# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Issue #71: Halt goal behaviour

## [0.2.0]

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
