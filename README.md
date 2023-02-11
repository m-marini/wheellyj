This project is a set of java and octave applications to handle the Wheelly robot.
Wheelly is a wheels' robot base on Arduino system.

The code of Arduino controller is at [Wheellino project](https://github.com/m-marini/wheellino).

The documentaion is in the [Wiki section](https://github.com/m-marini/wheellyj/wiki).

## Release 0.8.0

Compatible with Wheellino 0.5.0

## Release 0.7.0

Compatible with Wheellino 0.4.1

Added some robot configurations and radar map by samples median

## Release 0.6.1

Compatible with Wheellino 0.3.2

Minor fix and change

## Release 0.6.0

Compatible with Wheellino 0.3.2

Added resiliency management

## Release 0.5.2

Compatible with Wheellino 0.3.2

Added robot configuration in apps

## Release 0.5.1

Compatible with Wheellino 0.3.1

Added motor theta configuration
Added sensor receptive angle in radar
Added matrix monitor app

## Release 0.5.0

Compatible with Wheellino 0.3.0

## Release 0.4.2

Added the polar radar map component.
Added the esplorer objective.
Changed the yaml version to 0.4.

Added robot executor.
Added IMU failure report
Add radar sensitivity distance in yaml

## Release 0.4.1

Added the polar radar map component.
Added the esplorer objective.
Changed the yaml version to 0.4.

## Release 0.4.0

Added the radar map component that processes the proxy sensor signals creating a map of obstacles.
The map feeds the neural network as environment state signals.

Compatible with Wheellino 0.2.x 

## Release 0.3.0

The remote server is Java software running in JVM that implements the interaction between environment and agent.
The environment can collect and drive the remote robot by Wi-Fi connection or simulate the robot in a virtual
environment.

The agent is based on Temporal Difference (TD) actor-critic algorithms with eligibility trace, the critic component use
the residual advantage state value to evaluate the policy of actor.

## Release 0.2.0

The remote server is Java software running in JVM that allow to drive the robot running different inference engine
models (simple manual controller via joystick or state machine base engine).
The software creates a 2D space model from the proximity and contact sensors and implements different basic behaviors
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
