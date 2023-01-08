A wheels' robot (Wheelly)

The robot is consists of

- Chassis with two lateral wheels driven by two DC motors and a free wheel.
- 2 IR speed sensors for the wheels
- Motor controller board
- 12V DC rechargeable Li battery
- Step down DC-DC 12V-5V converter
- Power supply module 5/3.6 V
- MPU6050 gyroscope module
- ESP8266 Wifi module
- SG90 Micro-servo
- SR04 Ultrasonic proximity sensor
- 8 contact micro-switch contact sensors
- Arduino UNO main controller board

The Ardunio controller is driving all the units to allow the robot to collect the sensor data, compute the position
relative the initial state, drive the proximity sensor direction, drive the robot for a specific direction and speed by
feeding back from gyroscope and speed sensors and communicate with remote server via Wi-Fi sending status and receiving
commands.

The code of Arduino controller is at [Wheellino project](https://github.com/m-marini/wheellino).

### Maven build

To build the server run

```
mvn assembly:assembly -DskipTests
```

In `target` folder you will find an installation zip file

### Configure robot Wifi connection

After installed the application run the command:

```bash
java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.WiFiConf win
```

or by CLP (Command Line Processor)

```bash
>java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.WiFiConf -h

usage: org.mmarini.wheelly.apps.WiFiConf
       [-h] [-v] [-s SSID] [-a ADDRESS] [-p PASSWORD]
       {list,show,act,inact,win}

Configure wifi.

positional arguments:
  {list,show,act,inact,win}
                         specify the action

named arguments:
  -h, --help             show this help message and exit
  -v, --version          show current version
  -s SSID, --ssid SSID   specify the SSID (network identification)
  -a ADDRESS, --address ADDRESS
                         specify   the   host    api    address   (default:
                         192.168.4.1)
  -p PASSWORD, --password PASSWORD
                         specify the network pass phrase
```

### Run the server

After installed the application run the command:

```bash
java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.Wheelly
```

the options are

```bash
> java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.Wheelly -h
usage: org.mmarini.wheelly.apps.Wheelly
       [-h] [-v] [-r ROBOT] [-e ENV] [-a AGENT] [-k KPIS] [-l LABELS]
       [-s] [-t TIME]

Run a session of interaction between robot and environment.

named arguments:
  -h, --help             show this help message and exit
  -v, --version          show current version
  -r ROBOT, --robot ROBOT
                         specify robot  yaml  configuration  file (default:
                         robot.yml)
  -e ENV, --env ENV      specify  environment   yaml   configuration   file
                         (default: env.yml)
  -a AGENT, --agent AGENT
                         specify agent  yaml  configuration  file (default:
                         agent.yml)
  -k KPIS, --kpis KPIS   specify kpis path (default: )
  -l LABELS, --labels LABELS
                         specify kpi labels  comma  separated  (all for all
                         kpi) (default: )
  -s, --silent           specify  silent  closing   (no   window  messages)
                         (default: false)
  -t TIME, --time TIME   specify number  of  seconds  of  session  duration
                         (default: 43200)
```

### Run agent info

After installed the application run the command:

```bash
java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.WeightsStats
```

the options are

```bash
> java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.WeightsStats -h

usage: org.mmarini.wheelly.apps.WeightsStats
       [-h] [-v] modelPath

Dump the weights statistic

positional arguments:
  modelPath              specify the model path

named arguments:
  -h, --help             show this help message and exit
  -v, --version          show current version

```

### Analysis tools

The analysis tools are located in `octave` folder.
To collect the kpis the `-k <kpi_folder>` command option is required during agent training.

For information about performance analysis please refer to `docs/Analisys.md` file.

### Agent performance

The octave program `td_report` generates the agent performance report.
The kpi folder and the report folder should be selected.

### Delta weights

The weight changes are ploted with `td_delta_weights.m` octave program.

## Run the robot executor

After installed the application run the command:

```bash
java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.RobotExecutor
```

the options are

```bash
> java -classpath lib/wheelly-0.3.0.jar org.mmarini.wheelly.apps.RobotExecutor -h
usage: org.mmarini.wheelly.apps.RobotExecutor
       [-h] [-v] [-r ROBOT] [-a AGENT] [-s] [-t TIME]

Run a session of interaction between robot and environment.

named arguments:
  -h, --help             show this help message and exit
  -v, --version          show current version
  -r ROBOT, --robot ROBOT
                         specify robot  yaml  configuration  file (default:
                         robot.yml)
  -a AGENT, --agent AGENT
                         specify agent  yaml  configuration  file (default:
                         agent.yml)
  -s, --silent           specify  silent  closing   (no   window  messages)
                         (default: false)
  -t TIME, --time TIME   specify number  of  seconds  of  session  duration
                         (default: 43200)
```

### State machine agent

The robot executor used a state machine base agent.
The configuration of state machine agent defines the the flow of process.
It defines the states of the flow, the transitions and the entry state.

Transitions are defined by specifying the starting state, the arrival state and the trigger condition
(a regex on the signal generated by the state).
The onTransition command for each transition can be specified as well.

An example of state machine flow is:

```yaml

flow:
  entry: start
  states:
    start:
      class: org.mmarini.wheelly.engines.HaltState
      timeout: 2000
    exploring:
      class: org.mmarini.wheelly.engines.ExploringState
      scanInterval: 1000
      minSensorDir: -45
      maxSensorDir: 45
      sensorDirNumber: 5
      minMoveDistance: 0.5
      maxMoveDirection: 30
    avoiding:
      class: org.mmarini.wheelly.engines.AvoidingState
  transitions:
    - from: start
      to: exploring
      trigger: timeout
    - from: exploring
      to: avoiding
      trigger: blocked
    - from: avoiding
      to: exploring
      trigger: completed

```

### State specification

Each state is defined by the java implementation class, the onInit, onEntry, onExit commands
and specific parameters value for the state node behaviors.

### Halt state `org.mmarini.wheelly.engines.HaltState`

The halt state stops the robot and may activates the automatic movement of scanner.

The `timeout` signal is generated whenever the timeout interval has elapsed from state entry instant. 
The `blocked` signal is generated when robot has obstacle contacts.

Parameter are:

- `timeout` timeout interval (ms)
- `scanInterval` interval between scanner sensor movement (ms)
- `minSensorDir` minimum scanner direction (DEG)
- `maxSensorDir` maximum scanner direction (DEG)
- `sensorDirNumber` number of directions to scan

### Avoiding state `org.mmarini.wheelly.engines.AvoidingState`

The avoiding state moves the robot away from contact obstacle.

The `timeout` signal is generated whenever the timeout interval has elapsed from state entry instant.
The `completed` signal is generated whenever the robot has no more obstacle contacts.

Parameter are:

- `timeout` timeout interval (ms)

### Exploring state `org.mmarini.wheelly.engines.ExploringState`

The exploring state moves the robot round the environment to explore the unknwon zones.
During the exploration the sensor may be moved automatically.

The `timeout` signal is generated whenever the timeout interval has elapsed from state entry instant.
The `blocked` signal is generated when robot has obstacle contacts.

Parameter are:

- `timeout` timeout interval (ms)
- `scanInterval` interval between scanner sensor movement (ms)
- `minSensorDir` minimum scanner direction (DEG)
- `maxSensorDir` maximum scanner direction (DEG)
- `sensorDirNumber` number of directions to scan
- `minMoveDistance` minimum distance from obstacles to move the robot (m)
- `maxMoveDirection`: maximum direction change to move the robot (DEG), beyond which the robot only turns 

### Command specification

Each command is specified by a sequence of operators.

Base operator are:

- `add` performs the sum on last 2 operand in the stack `op(n-1) + op(n)`
- `sub` performs the difference on last 2 operand in the stack `op(n-1) - op2(n)`
- `mul` performs the product on last 2 operand in the stack `op(n-1) * op(n)`
- `div` performs the division on last 2 operand in the stack `op(n-1) / op(n)`
- `neg` performs the negation on last operand in the stack `-op(n)`
- `get` performs the read of value in the key, value map `map.get(key=op(n)`
- `put` performs the write of value in the key, value map `map.put(key=op(n-1), value=op(n))`
- `swap` performs the swap position in the stack of last two operands
- `time` push in the stack the time value (ms)

any other string defines operator that pushes in the stack the string value

Example:

Store the value `3=(1+2)` in the key, value map with key `aKey` 
```yaml
onInit:
  - aKey
  - 1
  - 2
  - add
  - put
```

## Release 0.5.0

Compatible with Wheellino 0.3.x

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
