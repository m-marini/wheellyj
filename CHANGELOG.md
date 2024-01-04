# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Issue #275: Unify yaml configuration files
- Issue #278: Api schema by id
- Issue #281: Add new status signals recognition to COM monitor
- Issue #286: COMMonitor with calssified status signals
- Issue #290: Review RadarMap update
- Issue #295: Move back the robot in avoid state without rotation

### Fixed

- Issue #280: RobotExecutor stuck
- Issue #284: Wrong speed with simulated robot
- Issue #288: Wrong command timing in RobotExecutor
- Issue #291: Wrong timing when restart robot
- Issue #294: Handle contacts in radar map

## [0.11.1] 2023-12-24

### Changed

- Issue #273: java version 21

## [0.11.0] 2023-12-20

### Added

- Issue #266: Add ExploringPointState
- Issue #269: Add asynchronous status message

## [0.10.1] 2023-11-29

### Changed

- Issue #262: Add ping signals in polar panel
- Issue #264: Change auto scan in state machine agent

### Fixed

- Issue #258: Polar Radar Check
- Issue #260: Wrong version in pom

## [0.10.0] 2023-11-17

### Added

- Issue #255: Add move to state

## [0.9.0] 2023-11-12

### Added

- Issue #240: Add stiction test app
- Issue #242: Add dump signals
- Issue #244: Friction at 8.5V
- Issue #246: Add trigger and state monitor
- Issue #248: Add contacts trigger on state machine agent
- Issue #250: Add load dump file
- Issue #252: Add power and target speed on status record

### Changed

- Issue #238: Change motion control parameters

## [0.8.1] 2023-10-20

### Added

- Issue #77: Add drop regularization
- Issue #232: Add supply sensor configuration
- Issue #236: Add physic measures app

### Changed

- Issue #222: Reactive Controller
- Issue #225: Shows reward on sensor monitor
- Issue #233: json schema validator

### Fixed

- Issue #227: Wrong reward on explore by imitation
- Issue #229: Wrong Resnet block

## [0.8.0] 2023-02-17

### Added

- Issue #208: Add octave engine calibration
- Issue #214: Add sensor monitor

### Changed

- Issue #210: Motor controller configuration
- Issue #216: Change motors configuration for acceleration limits
- Issue #218: Add move command monitoring

### Fixed

- Issue #211: ExploringState generates wrong sc command

## [0.7.0] 2023-02-09

### Added

- Issue #202: Configure contact sensor thresholds
- Issue #206: Configure max angular velocity

### Changed

- Issue #201: Radar map by samples median

## [0.6.1] 2023-02-07

### Changed

- Issue #197: Asynchronous socket implementation

### Fixed

- Issue #198: ComMonitor cannot be scrolled

## [0.6.0] 2023-02-03

### Added

- Issue #144: Add resiliency management
- Issue #179: Add real measure of reaction time
- Issue #185: Setting simulation speed
- Issue #187: Add com monitor to apps
- Issue #195: Explore by imitation objective

### Changed

- Issue #191: Unified halt and move actions

### Fixed

- Issue #178: Compiler error on org.mmarini.wheelly.objectives.MockEnvironment
- Issue #180: SimRobotObstacle Test Error
- Issue #188: Com frame is not resizable
- Issue #190: Blind polar radar
- Issue #193: Fix octave for speed action with halt

## [0.5.2] 2023-01-25

### Added

- Issue# 174: Add robot configuration in apps

## [0.5.1] 2023-01-20

### Added

- Issue #160: Add motor theta configuration
- Issue #162: Add sensor receptive angle in radar
- Issue #164: Add matrix monitor app
- Issue #171: Add IO callback to Robot

### Changed

- Issue #158: Move radar management away from robot status
- Issue #169: Load robot config in check apps

### Fixed

- Issue #167: Improve windows UI on MatrixMonitor

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
- Issue #29 Create automa behavior
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
