
#include <Wire.h>

//#define DEBUG
#include "debug.h"
#include "Timer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "I2Cdev.h"

//#define WITH_IMU

#ifdef WITH_IMU
#include "IMU.h"
#endif

#include "MotionCtrl.h"

/*
   Pins
*/
#define LEFT_PIN        2
#define RIGHT_FORW_PIN  3
#define BLOCK_LED_PIN   4
#define RIGHT_BACK_PIN  5
#define LEFT_BACK_PIN   6
#define ECHO_PIN        7
#define TRIGGER_PIN     8
#define SERVO_PIN       9
#define RIGHT_PIN       10
#define LEFT_FORW_PIN   11
#define PROXY_LED_PIN   12
#define VOLTAGE_PIN     A3

/*
   Serial config
*/
#define SERIAL_BPS  115200

/*
   Multiplexer outputs
*/

/*
   Distances
*/
#define STOP_DISTANCE 20
#define WARN_DISTANCE 60
#define MAX_DISTANCE  400


/*
    Obsatcle pulses
*/
#define MIN_OBSTACLE_PULSES 3
#define MAX_OBSTACLE_PULSES 12

/*
   Scanner constants
*/
#define NO_SAMPLES          5
#define FRONT_SCAN_INDEX    (NO_SCAN_DIRECTIONS / 2)
#define NO_SCAN_DIRECTIONS  (sizeof(scanDirections) / sizeof(scanDirections[0]))
#define SERVO_OFFSET        4

/*
   Command parser definitions
*/
#define LINE_SIZE 100

/*
   Intervals
*/
#define LED_INTERVAL          50ul
#define OBSTACLE_INTERVAL     50ul
#define STATS_INTERVAL        10000ul
#define QUERIES_INTERVAL      1000ul
#define MIN_QUERIES_INTERVAL  300ul

/*
   Dividers
*/
#define LED_PULSE_DIVIDER  31
#define LED_FAST_PULSE_DIVIDER  7
#define BLOCK_PULSE_DIVIDER  11

/*
   Voltage scale
*/
#define VOLTAGE_SCALE (5.0 * 3 / 1023)

/*
   Command parser
*/
char line[LINE_SIZE];

/*
   Timers
*/

Timer ledTimer;
Timer obstacleTimer;
Timer statsTimer;
Timer motorsTimer;
Timer queriesTimer;

/*
   Proximity sensor servo
*/
AsyncServo servo;

/*
   Proximity sensor
   Proximity distance scanner
*/
SR04 sr04(TRIGGER_PIN, ECHO_PIN);
const int scanDirections[] = {
  0, 30, 60, 90, 120, 150, 180
};
bool isFullScanning;
int scanIndex;  // Scan index
int frontDistance;

/*
   Movement motors
   Motor speeds [left, right] by direction
*/
MotionCtrl motionController(LEFT_FORW_PIN, LEFT_BACK_PIN, RIGHT_FORW_PIN, RIGHT_BACK_PIN, LEFT_PIN, RIGHT_PIN);

/*
   Statistics
*/
long counter;
unsigned long started;
unsigned long statsTime;
unsigned long tps;

/*
   Voltage sensor
*/
unsigned long voltageTime;
int voltageValue;

/*
   Gyrosc
*/
#ifdef WITH_IMU
MPU6050 mpu;
IMU imu(mpu);
bool imuFailure;
#endif

/*
   Set up
*/
void setup() {
  // init hardware
#if I2CDEV_IMPLEMENTATION == I2CDEV_ARDUINO_WIRE
  Wire.begin();
  Wire.setClock(400000); // 400kHz I2C clock. Comment this line if having compilation difficulties
#elif I2CDEV_IMPLEMENTATION == I2CDEV_BUILTIN_FASTWIRE
  Fastwire::setup(400, true);
#endif

  Serial.begin(SERIAL_BPS);
  Serial.println();

  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(PROXY_LED_PIN, OUTPUT);
  pinMode(BLOCK_LED_PIN, OUTPUT);
  pinMode(LEFT_FORW_PIN, OUTPUT);
  pinMode(LEFT_BACK_PIN, OUTPUT);
  pinMode(RIGHT_FORW_PIN, OUTPUT);
  pinMode(RIGHT_BACK_PIN, OUTPUT);

  digitalWrite(LED_BUILTIN, LOW);
  digitalWrite(PROXY_LED_PIN, LOW);
  digitalWrite(BLOCK_LED_PIN, LOW);
  digitalWrite(LEFT_FORW_PIN, LOW);
  digitalWrite(LEFT_BACK_PIN, LOW);
  digitalWrite(RIGHT_FORW_PIN, LOW);
  digitalWrite(RIGHT_BACK_PIN, LOW);

#ifdef WITH_IMU
  /*
     Init IMU
  */
  imuFailure = true;
  imu.begin();
  imu
  .onData(handleImuData)
  .onWatchDog(handleWatchDog)
  .calibrate()
  .enableDMP()
  .reset();
#endif

  Serial.println();

  /*
    Init led
  */
  digitalWrite(LED_BUILTIN, LOW);
  for (int i = 0; i < 3; i++) {
    delay(100);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(50);
    digitalWrite(LED_BUILTIN, LOW);
  }
  ledTimer.onNext(handleLedTimer)
  .interval(LED_INTERVAL)
  .continuous(true)
  .start();

  /*
    Init servo and scanner
  */
  sr04.begin();
  sr04.noSamples(NO_SAMPLES).onSample(&handleSample);
  scanIndex = FRONT_SCAN_INDEX;
  servo.attach(SERVO_PIN)
  .offset(SERVO_OFFSET)
  .onReached([](void *, int angle) {
    // Handles position reached event from scan servo
    DEBUG_PRINT(F("// handleReached: dir="));
    DEBUG_PRINTLN(angle);
    sr04.start();
  })
  .angle(scanDirections[scanIndex]);

  obstacleTimer.onNext(handleObstacleTimer)
  .interval(OBSTACLE_INTERVAL)
  .continuous(true)
  .start();

  /*
     Init motor controllers
  */
  motionController.begin();

  // Init staqstistics time
  started = millis();
  statsTimer.onNext(handleStatsTimer)
  .interval(STATS_INTERVAL)
  .continuous(true)
  .start();

  /*
     Init queries timer
  */
  queriesTimer.onNext(handleQuery).continuous(true);

  // Final setup
  Serial.println(F("ha"));
#ifdef WITH_IMU
  imu.reset();
#endif
  serialFlush();
}

/*
   Main loop
*/
void loop() {
  unsigned long now = millis();

#ifdef WITH_IMU
  imu.polling(now);
#endif

  counter++;
  voltageTime = now;
  voltageValue = analogRead(VOLTAGE_PIN);

  ledTimer.polling(now);

  obstacleTimer.polling(now);
  servo.polling(now);
  sr04.polling(now);

  statsTimer.polling(now);

  queriesTimer.polling(now);

  motionController
  .polling(now);

  pollSerialPort();
}

/*
   Poll the seraial port
*/
void pollSerialPort() {
  if (Serial.available()) {
    unsigned long time = millis();
    size_t n = Serial.readBytesUntil('\n', line, sizeof(line) - 1);
    line[n] = 0;
    processCommand(time);
  }
}

#ifdef WITH_IMU

/*

*/
void handleImuData(void*, IMU& imu) {
  imuFailure = false;
}

/*

*/
void handleWatchDog(void*, IMU& imu) {
  imuFailure = true;
  DEBUG_PRINT("!! Watch dog status: ");
  DEBUG_PRINTLN(imu.status());
  imu.reset()
  .kickAt(micros() + 1000000ul);
}
#endif

/*
  Handles timeout event from statistics timer
*/
void handleStatsTimer(void *context, unsigned long) {
  statsTime = millis();
  tps = counter * 1000 / (statsTime - started);
  started = statsTime;
  counter = 0;
  DEBUG_PRINT(F("// Stats: "));
  DEBUG_PRINTLN(tps);
}

/*
   Handles query
*/
void handleQuery(void *, unsigned long) {
  sendStatus();
}

/*
   Handles the led timer
*/
void handleLedTimer(void *, unsigned long n) {
#ifdef WITH_IMU
  unsigned long ledDivider = imuFailure ? LED_FAST_PULSE_DIVIDER : LED_PULSE_DIVIDER;
#else
  unsigned long ledDivider = LED_PULSE_DIVIDER;
#endif
  digitalWrite(LED_BUILTIN, (n % ledDivider) == 0 ? HIGH : LOW);
  digitalWrite(BLOCK_LED_PIN, !canMoveForward() &&  (n % BLOCK_PULSE_DIVIDER) == 0 ? HIGH : LOW);
}

/*
   Handles obstacle timer
*/
void handleObstacleTimer(void *, unsigned long i) {
  static unsigned long last = 0;
  if (frontDistance == 0 || frontDistance > WARN_DISTANCE) {
    digitalWrite(PROXY_LED_PIN, LOW);
  } else if (frontDistance <= STOP_DISTANCE) {
    digitalWrite(PROXY_LED_PIN, HIGH);
    last = i;
  } else {
    int n = map(frontDistance,
                STOP_DISTANCE, WARN_DISTANCE,
                MIN_OBSTACLE_PULSES, MAX_OBSTACLE_PULSES);
    if (i >= last + n) {
      digitalWrite(PROXY_LED_PIN, HIGH);
      last = i;
    } else {
      digitalWrite(PROXY_LED_PIN, LOW);
    }
  }
}

/*
    Handles mt command
*/
void handleMtCommand(const char* parms) {
  String args = parms;
  int s1 = args.indexOf(' ');
  if (s1 <= 0) {
    Serial.println(F("!! Wrong arg[1]"));
    return;
  }
  float left = min(max(-1.0, args.substring(0 , s1).toFloat()), 1.0);
  float right = min(max(-1.0, args.substring(s1 + 1).toFloat()), 1.0);
  if (isForward(left, right) && !canMoveForward()) {
    left = right = 0;
  }
  motionController.speed(left, right);
}

/*
    Handles start queries
*/
void handleStartQueries(const char* parms) {
  String args = parms;
  long interval = args.toInt();
  queriesTimer.stop();
  if (interval > 0) {
    queriesTimer.interval(max(interval, MIN_QUERIES_INTERVAL)).start();
  }
}

/*
   Handles sample event from distance sensor
*/
void handleSample(void *, int dist) {

  DEBUG_PRINT(F("// handleSample: dir="));
  DEBUG_PRINT(scanDirections[scanIndex]);
  DEBUG_PRINT(F(", distance="));
  DEBUG_PRINTLN(dist);

  if (scanIndex == FRONT_SCAN_INDEX) {
    frontDistance = dist;
    if (motionController.isForward() && !canMoveForward()) {
      motionController.speed(0, 0);
    }
  }
  sendSample(dist);
  if (isFullScanning) {
    scanIndex++;
    if (scanIndex >= NO_SCAN_DIRECTIONS) {
      scanIndex = FRONT_SCAN_INDEX;
      isFullScanning = false;
    }
  }
  servo.angle(scanDirections[scanIndex]);
}

/*
  Process a command from serial port
*/
void processCommand(unsigned long time) {
  DEBUG_PRINT(F("// processCommand: "));
  DEBUG_PRINTLN(line);
  strtrim(line, line);
  if (strncmp(line, "ck ", 3) == 0) {
    sendClock(line, time);
  } else if (strcmp(line, "rs") == 0) {
    resetWhelly();
  } else if (strcmp(line, "sc") == 0) {
    startFullScanning();
  } else if (strncmp(line, "sq ", 3) == 0) {
    handleStartQueries(line + 3);
  } else if (strncmp(line, "mt ", 3) == 0) {
    handleMtCommand(line + 3);
  } else if (strncmp(line, "//", 2) == 0
             || strncmp(line, "!!", 2) == 0
             || strlen(line) == 0) {
    // Ignore comments, errors, empty line
  } else {
    Serial.println(F("!! Wrong command"));
  }
}

/*
   Reset Wheelly
*/
void resetWhelly() {
  motionController.reset();
}

/*

*/
void sendSample(int distance) {
  Serial.print(F("pr "));
  Serial.print(millis());
  Serial.print(F(" "));
  Serial.print(scanDirections[scanIndex] - 90);
  Serial.print(F(" "));
  Serial.print(distance * 0.001, 3);
  Serial.print(F(" "));
  Serial.print(motionController.x(), 3);
  Serial.print(F(" "));
  Serial.print(motionController.y(), 3);
  Serial.print(F(" "));
  Serial.print(motionController.angle() * 180 / PI, 0);
  Serial.println();
}

/*

*/
void printVect(float* vect) {
  Serial.print(*vect++);
  Serial.print(F(" "));
  Serial.print(*vect++);
  Serial.print(F(" "));
  Serial.print(*vect++);
}

/*

*/
void sendClock(const char *cmd, unsigned long timing) {
  Serial.print(cmd);
  Serial.print(F(" "));
  Serial.print(timing);
  Serial.print(F(" "));
  unsigned long ms = millis();
  Serial.print(ms);
  Serial.println();
}

/*

*/
char *strtrim(char *out, const char *from) {
  while (isSpace(*from)) {
    from++;
  }
  const char *to = from + strlen(from) - 1;
  while (to >= from && isSpace(*to)) {
    to--;
  }
  char *s = out;
  while (from <= to) {
    *s++ = *from++;
  }
  *s = '\0';
  return out;
}

/*
   Returns true if forward direction
*/
bool isForward(float left, float right) {
  return left > 0 || right > 0;
}

/*
   Returns true if can move forward
*/
bool canMoveForward() {
  return forwardBlockDistance() > STOP_DISTANCE;
}

int forwardBlockDistance() {
  return (frontDistance > 0 && frontDistance <= MAX_DISTANCE) ? frontDistance : MAX_DISTANCE;
}

/*

*/
void startFullScanning() {
  sr04.stop();

  DEBUG_PRINTLN(F("startFullScanning"));

  isFullScanning = true;
  scanIndex = 0;
  servo.angle(scanDirections[scanIndex]);
}

/*

*/
void sendStatus() {
  Serial.print(F("st "));
  Serial.print(millis());
  Serial.print(F(" "));
  Serial.print(motionController.x(), 3);
  Serial.print(F(" "));
  Serial.print(motionController.y(), 3);
  Serial.print(F(" "));
  Serial.print(motionController.angle() * 180 / PI, 0);
  Serial.print(F(" "));
  Serial.print(motionController.left(), 3);
  Serial.print(F(" "));
  Serial.print(motionController.right(), 3);
  Serial.print(F(" "));
  Serial.print(voltageTime);
  Serial.print(F(" "));
  Serial.print(voltageValue * VOLTAGE_SCALE);
  Serial.print(F(" "));
  Serial.print(statsTime);
  Serial.print(F(" "));
  Serial.print(tps);
  Serial.println();
}

/*

*/
void serialFlush() {
  while (Serial.available() > 0) {
    Serial.read();
  }
}
