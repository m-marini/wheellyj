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

The code of Arduino controller is at [Wheellino project](https://github.com/m-marini/wheellino)

## Release 0.3.0

The remote server is Java software running in JVM that implements the interaction between environment and agent.
The environment can collect and drive the remote robot by Wi-Fi connection or simulate the robot in a virtual
environment.

The agent is based on Temporal Difference (TD) actor-critic algorithms with eligibility trace, the critic component use
the  residual advantage state value to evaluate the policy of actor.

### Maven build

To build the server run

```
mvn assembly:assembly -DskipTests
```

In `target` folder you will find an installation zip file

### Configure Robot Wifi connection 

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

usage: Wheelly [-h] [-v] [-r ROBOT] [-e ENV] [-a AGENT] [-k KPIS]
               [-l LABELS] [-s] [-t TIME]

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
                         specify kpi labels comma separated (default: )
  -s, --silent           specify  silent  closing   (no   window  messages)
                         (default: false)
  -t TIME, --time TIME   specify number  of  seconds  of  session  duration
                         (default: 43200)
```

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
