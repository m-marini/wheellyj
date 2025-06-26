This project is a set of java and octave applications to handle the Wheelly robot.
Wheelly is a wheels' robot base on Arduino system.

The code of Arduino controller is at [Wheellino project](https://github.com/m-marini/wheellino).

The code of Webcam controller is at [Wheellycam project](https://github.com/m-marini/wheellycam).

The documentaion is in the [Wiki section](https://github.com/m-marini/wheellyj/wiki).

## Release 0.23.0

- Add markers signals
- Add state dump
- Change label locating
- Camera event following proxy
- Move action masks creation

## Release 0.22.0

- Added relocate robot on stalemate

## Release 0.21.0

Added new features: 

- Relative radar map
- Map relative direction signal
- observe label goal
- Policy stats in report
- Relocate robot button
- Move to goal
- Saturation output level
- Critic kpi monitoring
- Direction and speed probability in report

Fixed some bugs:

- Wrong learning for negative reward
- Incorrect collision resolution

## Release 0.20.0

- Changed configuration files
- Find label objective with parametrized direction and sensor check
- Fixed missing batch session command and wrong model path in commands

## Release 0.19.0

- Add generation of report yaml
- Combine move and direction into a single value action
- Rescale learn panel values

## Release 0.18.0

- Add web cam processing
- Add labels in polar radar map
- Add find label state in state machine engine
- Add label state to polar environment
- Implement the shaping technique to reward design
- Add kpi descriptions to generated report
- Simple test targe
- Add average reward in report and activity monitor
- Add differential comparison
- Add rotation to target direction at move state

## Release 0.17.0

- Add PPO alghorithm
- Report action deltas kpi
- Training on trajectory sampling
- Manage continuing task only
- Separate learning rate from action optimization hyperparm
- Some fixs

## Release 0.16.1

- Single window mode
- Reset network button
- Reset map button and on connect
- Set radar map on contacts only
- Change action probability ratio

## Release 0.16.0

- Generate dynamic simulated environment
- Change td environment to handle the radar map tiles
- Encode can move state
- Added neural activity map application
- Optimize batch trainer

## Release 0.15.0

- Merge kpis
- Batch training UI
- Training kpis monitor panel
- Learning rates panel
- Fixed drop out regularization
- Minor fixs

## Release 0.14.0

- Batch training and performance evaluation
- Agent with single neural network
- Cautious behavior and objective

## Release 0.13.0

- Simplified yaml file schema
- Added filtering RadaMap cells by quadratic inequalities (performance improve)
- Fixed robot simulation stall and performance issues

## Release 0.12.1

- Set max speed on move state
- Move to free point in avoid state at parametrized speed
- Change robot simulation shape

## Release 0.12.0

- Added clean Radar map state
- Unified yaml configuration files
- schema by id
- new status signals recognition to COM monitor
- Review RadarMap update
- Move back the robot in avoid state without rotation
- Improve exploring state point
- Some fixes

## Release 0.11.1

Java 21 support

## Release 0.11.0

Add asynchronous status message and ExploringPointState

Compatible with Wheellino 0.8.0

## Release 0.10.1

Added ping signals in polar map, changed auto scan and some minor fix

## Release 0.10.0

Compatible with Wheellino 0.7.0

- Add move to state

## Release 0.9.0

Compatible with Wheellino 0.7.0

- Add friction/stiction test app
- Add dump signals abd dump reader aoo
- Add trigger and state monitor in monitor app
- Add contacts trigger on state machine agent
- Add power and target speed on status record

## Release 0.8.1

Compatible with Wheellino 0.6.1

- Add drop regularization
- Add supply sensor configuration
- Add physic measures app

## Release 0.8.0

Compatible with Wheellino 0.5.0

- Add octave engine calibration, sensor monitor

## Release 0.7.0

Compatible with Wheellino 0.4.1

- Added some robot configurations and radar map by samples median

## Release 0.6.1

Compatible with Wheellino 0.3.2

- Minor fix and change

## Release 0.6.0

Compatible with Wheellino 0.3.2

- Added resiliency management

## Release 0.5.2

Compatible with Wheellino 0.3.2

- Added robot configuration in apps

## Release 0.5.1

Compatible with Wheellino 0.3.1

- Added motor theta configuration
- Added sensor receptive angle in radar
- Added matrix monitor app

## Release 0.5.0

Compatible with Wheellino 0.3.0

## Release 0.4.2

- Added the polar radar map component.
- Added the esplorer objective.
- Changed the yaml version to 0.4.

- Added robot executor.
- Added IMU failure report
- Add radar sensitivity distance in yaml

## Release 0.4.1

- Added the polar radar map component.
- Added the esplorer objective.
- Changed the yaml version to 0.4.

## Release 0.4.0

Compatible with Wheellino 0.2.x

- Added the radar map component that processes the proxy sensor signals creating a map of obstacles.
- The map feeds the neural network as environment state signals.

## Release 0.3.0

- The remote server is Java software running in JVM that implements the interaction between environment and agent.
- The environment can collect and drive the remote robot by Wi-Fi connection or simulate the robot in a virtual
  environment.

- The agent is based on Temporal Difference (TD) actor-critic algorithms with eligibility trace, the critic component
  use
  the residual advantage state value to evaluate the policy of actor.

## Release 0.2.0

- The remote server is Java software running in JVM that allow to drive the robot running different inference engine
  models (simple manual controller via joystick or state machine base engine).
- The software creates a 2D space model from the proximity and contact sensors and implements different basic behaviors
  that can be composed to build the state machine engine.

The basic behaviors are:

- stop for a while,
- single or random sensor scan with obstacle detection
- move direct to a location with sensor scanning for obstacles
- Find path to location avoiding obstacles (A* algorithm)
- Select sequentially target location from a list
- Select random target location from a list
- Complex behavior to secure the robot in a safe location (far way from obstacles)
- Complex behavior to follow the nearest obstacle at a safe distance
- Wait for robot unblock (robot is in a block state if it cannot move in any way)
