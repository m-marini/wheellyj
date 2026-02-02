# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Add

- Issue [#576](https://github.com/m-marini/wheellyj/issues/576): Add lidars

## [0.25.0] 2025-10-12

### Add

- Issue [#565](https://github.com/m-marini/wheellyj/issues/565): Add MQTT architecture
- Issue [#570](https://github.com/m-marini/wheellyj/issues/570): Add convolution network to radar map

## [0.24.1] 2025-08-10

### Added

- Issue [#561](https://github.com/m-marini/wheellyj/issues/561): Add label direction to camera event
- Issue [#564](https://github.com/m-marini/wheellyj/issues/564): Add number of samples parameters in state machine

### Fixed

- Issue [#557](https://github.com/m-marini/wheellyj/issues/557): Ghost labels in simulated robot
- Issue [#563](https://github.com/m-marini/wheellyj/issues/563): Fixed missing markers in real robot

## [0.24.0] 2025-07-25

### Added

- Issue [#546](https://github.com/m-marini/wheellyj/issues/546): Mapping state
- Issue [#552](https://github.com/m-marini/wheellyj/issues/542): Add Astar-RRT search state
- Issue [#554](https://github.com/m-marini/wheellyj/issues/544): Add label stuck state

### Changed

- Issue [#545](https://github.com/m-marini/wheellyj/issues/545): Point echo
- Issue [#550](https://github.com/m-marini/wheellyj/issues/550): Full reactive robot api

### Fixed
- 
- Issue [#556](https://github.com/m-marini/wheellyj/issues/556): Missing speed parameter in LabelStuckState

## [0.23.0] 2025-06-26

### Added

- Issue [#530](https://github.com/m-marini/wheellyj/issues/530): Add markers signals
- Issue [#534](https://github.com/m-marini/wheellyj/issues/534): Add state dump

### Changed

- Issue [#527](https://github.com/m-marini/wheellyj/issues/527): Change label locating
- Issue [#532](https://github.com/m-marini/wheellyj/issues/532): Camera event following proxy
- Issue [#542](https://github.com/m-marini/wheellyj/issues/542): Move action masks creation

### Fixed

- Issue [#536](https://github.com/m-marini/wheellyj/issues/536): Overflow on command line arguments
- Issue [#538](https://github.com/m-marini/wheellyj/issues/538): Wrong inference command line argument
- Issue [#540](https://github.com/m-marini/wheellyj/issues/540): Missing reward function

## [0.22.0] 2025-03-07

### Added

- Issue [#522](https://github.com/m-marini/wheellyj/issues/522): Add relocate robot on stalemate

## [0.21.0] 2025-02-02

### Added

- Issue [#493](https://github.com/m-marini/wheellyj/issues/493): Add relative radar map
- Issue [#496](https://github.com/m-marini/wheellyj/issues/496): Add map relative direction signal
- Issue [#498](https://github.com/m-marini/wheellyj/issues/498): Add observe label goal
- Issue [#500](https://github.com/m-marini/wheellyj/issues/500): Add policy stats in report
- Issue [#502](https://github.com/m-marini/wheellyj/issues/502): Add relocate robot button
- Issue [#506](https://github.com/m-marini/wheellyj/issues/506): Add move to goal
- Issue [#512](https://github.com/m-marini/wheellyj/issues/512): Add saturation output level
- Issue [#514](https://github.com/m-marini/wheellyj/issues/514): Add critic kpi monitoring
- Issue [#516](https://github.com/m-marini/wheellyj/issues/516): Add direction and speed probability in report

### Fixed

- Issue [#504](https://github.com/m-marini/wheellyj/issues/504): Verify contact simulation
- Issue [#509](https://github.com/m-marini/wheellyj/issues/509): Wrong learning for negative reward
- Issue [#519](https://github.com/m-marini/wheellyj/issues/519): Incorrect collision resolution

## [0.20.0] 2024-12-28

### Changed

- Issue [#485](https://github.com/m-marini/wheellyj/issues/485): Change configuration files
- Issue [#489](https://github.com/m-marini/wheellyj/issues/489): Change find label objective with parametrized direction and sensor check
- Issue [#491](https://github.com/m-marini/wheellyj/issues/491):  Change find label objective

### Fixed

- Issue [#487](https://github.com/m-marini/wheellyj/issues/487): Missing batch session command and wrong model path in cmmands

## [0.19.0] 2024-12-17

### Added

- Issue [#477](https://github.com/m-marini/wheellyj/issues/477): Add generation of report yaml

### Changed

- Issue [#472](https://github.com/m-marini/wheellyj/issues/472): Combine move and direction into a single value action
- Issue [#475](https://github.com/m-marini/wheellyj/issues/475): Rescale learn panel values

### Fixed

- Issue [#480](https://github.com/m-marini/wheellyj/issues/480): Change learning parameter during training
- Issue [#482](https://github.com/m-marini/wheellyj/issues/482): Missing histogram

## [0.18.0] 2024-11-15

- Issue [#442](https://github.com/m-marini/wheellyj/issues/442): Add web cam processing
- Issue [#448](https://github.com/m-marini/wheellyj/issues/448): Add labels in polar radar map
- Issue [#449](https://github.com/m-marini/wheellyj/issues/449): Add find label state in state machine engine
- Issue [#452](https://github.com/m-marini/wheellyj/issues/452): Add label state to polar environment
- Issue [#458](https://github.com/m-marini/wheellyj/issues/458): Implement the shaping technique to reward design
- Issue [#462](https://github.com/m-marini/wheellyj/issues/462): Add kpi descriptions to generated report
- Issue [#464](https://github.com/m-marini/wheellyj/issues/464): Simple test targe
- Issue [#466](https://github.com/m-marini/wheellyj/issues/466): Add average reward in report and activity monitor
- Issue [#468](https://github.com/m-marini/wheellyj/issues/468): Add differential comparison

### Changed

- Issue [#446](https://github.com/m-marini/wheellyj/issues/446): Change state machine configuration yaml
- Issue [#455](https://github.com/m-marini/wheellyj/issues/455): Change radar map cell state update
- Issue [#457](https://github.com/m-marini/wheellyj/issues/457):  Add rotation to target direction at move state

### Fixed

- Issue [#451](https://github.com/m-marini/wheellyj/issues/451): Wrong target in exploring state
- Issue [#470](https://github.com/m-marini/wheellyj/issues/470): Wrong alpha panel slider value greater than 100

## [0.17.0] 2024-08-24

### Added

- Issue [#430](https://github.com/m-marini/wheellyj/issues/430): Add PPO alghorithm
- Issue [#437](https://github.com/m-marini/wheellyj/issues/437): Report action deltas kpi

### Changed

- Issue [#421](https://github.com/m-marini/wheellyj/issues/421): Training on trajectory sampling
- Issue [#423](https://github.com/m-marini/wheellyj/issues/423): Manage continuing task only
- Issue [#435](https://github.com/m-marini/wheellyj/issues/435): Separate learning rate from action optimization hyperparm

### Fixed

- Issue [#426](https://github.com/m-marini/wheellyj/issues/426): Wrong action probabilitites kpis
- Issue [#428](https://github.com/m-marini/wheellyj/issues/428): Ignore trained agent in case of agent reset
- Issue [#433](https://github.com/m-marini/wheellyj/issues/433): Exception when alpha changes in wheelly ui
- Issue [#439](https://github.com/m-marini/wheellyj/issues/439): Histogram report generates exception
- Issue [#441](https://github.com/m-marini/wheellyj/issues/441): Test ppo algorithm

## [0.16.1] 2024-05-05

### Added

- Issue [#407](https://github.com/m-marini/wheellyj/issues/407): Add single window mode
- Issue [#408](https://github.com/m-marini/wheellyj/issues/408): Add reset network button
- Issue [#413](https://github.com/m-marini/wheellyj/issues/413): Reset map button

### Changed

- Issue [#414](https://github.com/m-marini/wheellyj/issues/414): Reset map on connect
- Issue [#415](https://github.com/m-marini/wheellyj/issues/415): Set radar map on contacts only
- Issue [#419](https://github.com/m-marini/wheellyj/issues/419): Change action probability ratio

## [0.16.0] 2024-03-30

### Added

- Issue [#356](https://github.com/m-marini/wheellyj/issues/356): Generate dynamic simulated environment
- Issue [#359](https://github.com/m-marini/wheellyj/issues/359): Change td environment to handle the radar map tiles
- Issue [#388](https://github.com/m-marini/wheellyj/issues/388): Encode can move state
- Issue [#390](https://github.com/m-marini/wheellyj/issues/390): Added neural activity map application

### Changed

- Issue [#400](https://github.com/m-marini/wheellyj/issues/400): Optimize batch trainer

### Fixed

- Issue [#392](https://github.com/m-marini/wheellyj/issues/392): Fix NNActivityMonitor UI
- Issue [#394](https://github.com/m-marini/wheellyj/issues/394): Fix labels on kpis panel
- Issue [#395](https://github.com/m-marini/wheellyj/issues/395): Change predictability kpi
- Issue [#396](https://github.com/m-marini/wheellyj/issues/396): Verify file size on batch, report, nna
- Issue [#402](https://github.com/m-marini/wheellyj/issues/402): Fix training kpis
- Issue [#404](https://github.com/m-marini/wheellyj/issues/404): Merge kpi does not run
- Issue [#406](https://github.com/m-marini/wheellyj/issues/406): Wrong selection in learn panel

## [0.15.0] 2024-03-14

### Added

- Issue [#366](https://github.com/m-marini/wheellyj/issues/366): Merge kpis
- Issue [#368](https://github.com/m-marini/wheellyj/issues/368): Add batch training UI
- Issue [#372](https://github.com/m-marini/wheellyj/issues/372): Add performance fields in batch training window
- Issue [#374](https://github.com/m-marini/wheellyj/issues/374): Add training kpis monitor panel
- Issue [#376](https://github.com/m-marini/wheellyj/issues/376): Add learning rates panel

### Changed

- Issue [#370](https://github.com/m-marini/wheellyj/issues/370): Buffering read binary file
- Issue [#378](https://github.com/m-marini/wheellyj/issues/378): Change kpis to monitor delta eta
- Issue [#382](https://github.com/m-marini/wheellyj/issues/382): Add monitor average reward

### Fixed

- Issue [#77](https://github.com/m-marini/wheellyj/issues/77): Fixed drop out regularization
- Issue [#362](https://github.com/m-marini/wheellyj/issues/362): Error loading tdagent
- Issue [#364](https://github.com/m-marini/wheellyj/issues/364): Wrong scale computation in octave report
- Issue [#380](https://github.com/m-marini/wheellyj/issues/380): Fix average advantage
- Issue [#384](https://github.com/m-marini/wheellyj/issues/384): Inconsistent report

## [0.14.0] 2024-02-25

### Added

- Issue [#337](https://github.com/m-marini/wheellyj/issues/337): Add agent with single nn
- Issue [#346](https://github.com/m-marini/wheellyj/issues/346):  Datavec usage to train network
- Issue [#341](https://github.com/m-marini/wheellyj/issues/341): Add batch training performance evaluation
- Issue [#342](https://github.com/m-marini/wheellyj/issues/342): Separate learning rates between actor and critic
- Issue [#352](https://github.com/m-marini/wheellyj/issues/352): Add cautious behavior and objective
- Issue [#354](https://github.com/m-marini/wheellyj/issues/354): Create backup file in Wheelly app

### Changed

- Issue [#357](https://github.com/m-marini/wheellyj/issues/357): Change clean up radar map

### Fixed

- Issue [#340](https://github.com/m-marini/wheellyj/issues/340): Fix robot env failed test
- Issue [#343](https://github.com/m-marini/wheellyj/issues/343): Fix pattern match for kpi label
- Issue [#350](https://github.com/m-marini/wheellyj/issues/350): Fix octave report generator

## [0.13.0] 2024-01-28

### Changed

- Issue [#314](https://github.com/m-marini/wheellyj/issues/314): Simplify yaml file schema
- Issue [#316](https://github.com/m-marini/wheellyj/issues/316): Updating polar map takes too much cpu
- Issue [#320](https://github.com/m-marini/wheellyj/issues/320): Differentiate contact updates in the radar map
- Issue [#322](https://github.com/m-marini/wheellyj/issues/322): Monitoring distance while moving to
- Issue [#330](https://github.com/m-marini/wheellyj/issues/330): Add quadratic inequality
- Issue [#332](https://github.com/m-marini/wheellyj/issues/332): Filter cells by area

### Fixed

- Issue [#321](https://github.com/m-marini/wheellyj/issues/321) : Robot simulation stalls 
- Issue [#327](https://github.com/m-marini/wheellyj/issues/327): Find free target method takes too much cpu

## [0.12.1] 2024-01-14

### Added

- Issue #303: Set max speed on move state
- Issue #306: Move to free point in avoid state at parametrized speed

### Changed

- Issue #304: Use effective radar grid size in panel
- Issue #305: Change the ping spot to little circle
- Issue #315: Change robot simulation shape

### Fixed

- Issue #311: Windows does not lay out correctly at app start

## [0.12.0] 2024-01-08

### Added

- Issue #300: Add clean Radar map state

### Changed

- Issue #275: Unify yaml configuration files
- Issue #278: Api schema by id
- Issue #281: Add new status signals recognition to COM monitor
- Issue #286: COMMonitor with calssified status signals
- Issue #290: Review RadarMap update
- Issue #295: Move back the robot in avoid state without rotation
- Issue #298: Improve exploring state point

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
