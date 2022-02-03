#include "debug.h"
#include "Timer.h"
#include "Multiplexer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "RemoteCtrl.h"
#include "MotorCtrl.h"

/*
 * Pins
 */
#define LATCH_PIN 8
#define CLOCK_PIN 7
#define DATA_PIN 12

#define TRIGGER_PIN 9
#define ECHO_PIN 4

#define SERVO_PIN 10

#define RECEIVER_PIN 11

#define ENABLE_LEFT_PIN 5
#define ENABLE_RIGHT_PIN 6

/*
 * Multiplexer outputs
 */
#define LEFT_BACK   0
#define LEFT_FORW   1
#define RIGHT_FORW  2
#define RIGHT_BACK  3
#define RED1        4
#define RED2        5
#define RED3        6
#define RED4        7

/*
 * Motor speeds
 */
#define MAX_FORWARD 255
#define MAX_BACKWARD -255

/*
 * Distances
 */
#define THRESHOLD_DISTANCE  90
#define STOP_DISTANCE  (THRESHOLD_DISTANCE / 3)

/*
 * Directions
 */
#define FORWARD_LEFT    0
#define FORWARD         1
#define FORWARD_RIGHT   2
#define LEFT            3
#define STOP            4
#define RIGHT           5
#define BACKWARD_LEFT   6
#define BACKWARD        7
#define BACKWARD_RIGHT  8
#define NO_DIRECTIONS (sizeof(speedsByDirection) / sizeof(speedsByDirection[0]))

#define NO_COMMAND      -1


/*
 * Intervals
 */
#define SCAN_INTERVAL   10000ul
#define MOVE_INTERVAL   750ul

/*
 * Status
 */
#define STANDBY       0
#define IDLE          1
#define SCANNING      2
#define MOVE_FORWARD  3
#define ROTATING      4
#define MOVE_BACKWARD 5

/*
 * Scanner constants
 */
#define NO_SAMPLES  3
#define FRONT_SCAN_INDEX  (NO_SCAN_DIRECTIONS / 2)
#define NO_SCAN_DIRECTIONS (sizeof(scanDirections) / sizeof(scanDirections[0]))

/*
 * Global variables
 */

const Timer ledTimer;
const Timer scanTimer;
const Timer statsTimer;
const Timer moveTimer;

Multiplexer multiplexer(LATCH_PIN, CLOCK_PIN, DATA_PIN);

const SR04 sr04(TRIGGER_PIN, ECHO_PIN);

const AsyncServo servo;

const MotorCtrl leftMotor(ENABLE_LEFT_PIN, LEFT_FORW, LEFT_BACK);
const MotorCtrl rightMotor(ENABLE_RIGHT_PIN, RIGHT_FORW, RIGHT_BACK);

long counter;
unsigned long started;
byte status;
int scanIndex;  // Scan index

/*
 * Scan directions
 */
const int scanDirections[] = {
  0, 30, 60, 90, 120, 150, 180
};
/*
 * Obstacle distances
 */
int distances[NO_SCAN_DIRECTIONS] ;

const int speedsByDirection[][2] = {
  {0, MAX_FORWARD},             // FORWARD_LEFT
  {MAX_FORWARD, MAX_FORWARD},   // FORWARD
  {MAX_FORWARD, 0},             // FORWARD_RIGHT
  {MAX_BACKWARD, MAX_FORWARD},  // LEFT
  {0, 0},                       // STOP
  {MAX_FORWARD, MAX_BACKWARD},  // RIGHT
  {MAX_BACKWARD, 0},            // BACKWARD_LEFT
  {MAX_BACKWARD, MAX_BACKWARD}, // BACKWARD
  {0, MAX_BACKWARD},            // BACKWARD_RIGHT
};

const static unsigned long standbyTime[] = {50, 1450};
const static unsigned long idleTime[] = {50, 200};

/*
 * Set up
 */
void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  multiplexer.begin();
  sr04.begin();
  servo.attach(SERVO_PIN);
  leftMotor.begin(multiplexer);
  rightMotor.begin(multiplexer);
  remoteCtrl.begin();
  
  multiplexer.reset().flush();
  servo.angle(90);

  digitalWrite(LED_BUILTIN, LOW);
  for (int i = 0; i < 3; i++) {
    delay(100);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(50);
    digitalWrite(LED_BUILTIN, LOW);
  }
  
  Serial.println(F("Hello! I'm Wheelly"));

  ledTimer.onNext(&handleLedBlink)
    .continuous(true);
  statsTimer.onNext(&handleStats)
    .interval(10000)
    .continuous(true)
    .start();
  scanTimer
    .interval(SCAN_INTERVAL)
    .onNext(&handleScanTimer);
  moveTimer
    .interval(MOVE_INTERVAL)
    .onNext(&handleMoveTimer);

  sr04.noSamples(NO_SAMPLES)
    .onSample(&handleSample);

  servo.onReached(handleReached)
    .angle(scanDirections[FRONT_SCAN_INDEX]);

  remoteCtrl.onData(&handleIRData);

  started = millis();
  changeToStandby();
}

/*
 * Main loop
 */
void loop() {
  counter++;
  ledTimer.polling();
  statsTimer.polling();
  scanTimer.polling();
  moveTimer.polling();
  servo.polling();
  sr04.polling();
  remoteCtrl.polling();
  multiplexer.flush();
}

/*
 * Handle timeout event from scan timer
 */
void handleScanTimer(void *, int long) {
  changeToScanning();
}

/*
 * Handle timeout event from scan timer
 */
void handleMoveTimer(void *, int long) {
#if DEBUG
  Serial << "handleMoveTimer" << endl;
#endif
  changeToIdle();
}

/*
 * Handles data received from IR remote controller
 */
void handleIRData(decode_results& results) {
#if DEBUG
  Serial << "handleIRData " << _HEX(results.value) << endl;
#endif
  unsigned long cmd = results.value;
  handleCommand(cmd);
}

/*
 * Handles command from IR remote controller
 */
void handleCommand(unsigned long cmd) {

#if DEBUG
  Serial << "handleCommand " << _HEX(cmd) << endl;
#endif
  if (status == STANDBY) {
    if (cmd == KEY_POWER) {
#if DEBUG
  Serial << " changeToScanning " << _HEX(cmd) << endl;
#endif
      changeToScanning();
    }
  } else {
    int direction = toDirection(cmd);
    if (cmd == KEY_POWER) {
      changeToStandby();
    } else if (cmd == KEY_EQ) {
      changeToScanning();
    } else if (isRotating(direction)) {
      changeToRotating(direction);
    } else if (isBackward(direction)) {
      changeToBackward(direction);
    } else if (direction == STOP) {
      changeToIdle();
    } else if (isForward(direction) && canMoveForward()) {
      changeToForward(direction);
    }
  }
}

/*
 * Handles position reached event from scan servo
 */
void handleReached(void *, int angle) {

#if DEBUG
    Serial << "handleReached: dir=" << angle << endl;
#endif

  if (status != STANDBY) {
    sr04.start();
  }
}

/*
 * Handles sample event from distance sensor
 */
void handleSample(void *, int distance) {

#if DEBUG
    Serial << "handleSample: dir=" << scanDirections[scanIndex] << ", distance=" << distance << endl;
#endif

  if (status != STANDBY) {
    showDistance(distance);
    distances[scanIndex] = distance;
    switch (status) {
      case SCANNING:
        scanIndex++;
        if (scanIndex >= NO_SCAN_DIRECTIONS) {
          scanIndex = FRONT_SCAN_INDEX;
          changeToIdle();
        }
        servo.angle(scanDirections[scanIndex]);
        break;
      case MOVE_FORWARD:
        if (!canMoveForward()) {
          changeToScanning();
        } else {
          scanFront();
        }
        break;
      default:
        servo.angle(scanDirections[scanIndex]);
    }
  }
}

/*
 * Handles timeout event from LED blink timer
 */
void handleLedBlink(void *, int i, long) {
  if (i == 0){
    digitalWrite(LED_BUILTIN, LOW);
  } else {
    digitalWrite(LED_BUILTIN, HIGH);
  }
}

/*
 * Handle timeout event from statistics timer
 */
void handleStats(void *context, int i, long j) {

#if DEBUG
  Serial.print("Stats: ");
  Serial.println (counter * 1000 / (millis() - started));
#endif

  started = millis();
  counter = 0;
}

/*
 * Set idle status (scanning and wait for commands)
 */
void changeToIdle() {
  status = IDLE;
  ledTimer
    .intervals(2, idleTime)
    .start();
  moveTo(STOP);
  scanTimer.start();
  moveTimer.stop();
}

/**
 * Set standby status do nothing
 */
void changeToStandby() {
  status = STANDBY;
  ledTimer
    .intervals(2, standbyTime)
    .start();
  moveTo(STOP);
  multiplexer.reset();
  scanTimer.stop();
  moveTimer.stop();
}

/*
 * Set scanning status
 */
void changeToScanning() {
  status = SCANNING;
  scanTimer.stop();
  scanIndex = 0;
  moveTo(STOP);
  servo.angle(scanDirections[scanIndex]);
  moveTimer.stop();
}

/*
 * Set forward status
 */
void changeToForward(int direction) {
  status = MOVE_FORWARD;
  moveTo(direction);
  scanTimer.stop();
  moveTimer.stop();
  scanFront();
}

/*
 * Set rotating status
 */
void changeToRotating(int direction) {
  status = ROTATING;
  scanTimer.stop();
  moveTo(direction);
  moveTimer.start();
  scanFront();
}

/*
 * Set rotating status
 */
void changeToBackward(int direction) {
  status = MOVE_BACKWARD;
  scanTimer.stop();
  moveTo(direction);
  moveTimer.start();
  scanFront();
}

/*
 * Returns true if forward direction
 */
bool isForward(int direction) {
  switch (direction) {
    case FORWARD_LEFT:
    case FORWARD:
    case FORWARD_RIGHT:
      return true;
    default:
      return false;
  }
}

/*
 * Returns true if forward direction
 */
bool isRotating(int direction) {
  switch (direction) {
    case LEFT:
    case RIGHT:
      return true;
    default:
      return false;
  }
}

/*
 * Returns true if forward direction
 */
bool isBackward(int direction) {
  switch (direction) {
    case BACKWARD_LEFT:
    case BACKWARD_RIGHT:
    case BACKWARD:
      return true;
    default:
      return false;
  }
}

/*
 * Returns true if can move forward 
 */
bool canMoveForward() {
  for (int i = 1; i < NO_SCAN_DIRECTIONS - 1; i++) {
    int d = distances[i];
    if (d > 0 && d <= STOP_DISTANCE) {
      return false;
    }
  }
  return true;
}

void scanFront() {
  scanIndex = FRONT_SCAN_INDEX;
  servo.angle(scanDirections[scanIndex]);
}

/*
 * Move Wheelly to direction
 */
void moveTo(int direction) {
  if (direction < 0 || direction >= NO_DIRECTIONS) {
    direction = STOP;
  }
  int left = speedsByDirection[direction][0];
  int right = speedsByDirection[direction][1];
  leftMotor.speed(left);
  rightMotor.speed(right);
}

/*
 * Returns the directino from a IR receiver data
 */
int toDirection(unsigned long cmd) {
  switch (cmd) {
    case KEY_1:
      return FORWARD_LEFT;
    case KEY_2:
       return FORWARD;
    case KEY_3:
      return FORWARD_RIGHT;
    case KEY_4:
      return LEFT;
    case KEY_0:
    case KEY_5:
    case KEY_POWER:
    case KEY_VOL_PLUS:
    case KEY_FUNC_STOP:
    case KEY_FAST_BACK:
    case KEY_PAUSE:
    case KEY_FAST_FORWARD:
    case KEY_DOWN:
    case KEY_VOL_MINUS:
    case KEY_UP:
    case KEY_EQ:
    case KEY_ST_REPT:
      return STOP;
    case KEY_6:
      return RIGHT;
    case KEY_7:
      return BACKWARD_LEFT;
    case KEY_8:
      return BACKWARD;
    case KEY_9:
      return BACKWARD_RIGHT;
  }
  return NO_COMMAND;
}

/*
 * Show distances
 */
void showDistance(int distance) {
  multiplexer.set(RED1, distance <= THRESHOLD_DISTANCE);
  multiplexer.set(RED2, distance <= (THRESHOLD_DISTANCE * 2) / 3);
  multiplexer.set(RED3, distance <= THRESHOLD_DISTANCE / 3);
  multiplexer.set(RED4, !canMoveForward());
}
