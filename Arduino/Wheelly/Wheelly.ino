
#include <Wire.h>

#include "debug.h"
#include "Timer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "MotorCtrl.h"
#include "I2Cdev.h"
#include "IMU.h"

/*
   Pins
*/
#define RIGHT_FORW_PIN  3
#define BLOCK_LED_PIN   4
#define RIGHT_BACK_PIN  5
#define LEFT_BACK_PIN   6
#define ECHO_PIN        7
#define TRIGGER_PIN     8
#define SERVO_PIN       9
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
   Motor speeds
*/
#define MAX_FORWARD   255
#define MAX_BACKWARD  -255

/*
   Distances
*/
#define STOP_DISTANCE 40
#define WARN_DISTANCE 80
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
#define CAN_MOVE_MIN_DIR    (FRONT_SCAN_INDEX - 1)
#define CAN_MOVE_MAX_DIR    (FRONT_SCAN_INDEX + 1)
#define SERVO_OFFSET        -7

/*
   Command parser definitions
*/
#define LINE_SIZE 100

/*
   Intervals
*/
#define LED_INTERVAL      50ul
#define OBSTACLE_INTERVAL 50ul

/*
   Dividers
*/
#define LED_PULSE_DIVIDER  31
#define LED_FAST_PULSE_DIVIDER  7
#define BLOCK_PULSE_DIVIDER  11

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
int distances[NO_SCAN_DIRECTIONS];
unsigned long scanTimes[NO_SCAN_DIRECTIONS];

/*
   Movement motors
   Motor speeds [left, right] by direction
*/
MotorCtrl leftMotor(LEFT_FORW_PIN, LEFT_BACK_PIN);
MotorCtrl rightMotor(RIGHT_FORW_PIN, RIGHT_BACK_PIN);
int leftMotorSpeed;
int rightMotorSpeed;

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
MPU6050 mpu;
IMU imu(mpu);
unsigned long assetTime;
bool imuFailure;

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

  // Init IMU
  imuFailure = true;
  imu.begin();
  imu
  .onData(handleImuData)
  .onWatchDog(handleWatchDog)
  .calibrate()
  .enableDMP()
  .reset();

  // Init led
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

  // Init servo and scanner
  sr04.begin();
  sr04.noSamples(NO_SAMPLES)
  .onSample(&handleSample);

  scanIndex = FRONT_SCAN_INDEX;
  servo.attach(SERVO_PIN)
  .offset(SERVO_OFFSET)
  .onReached([](void *, int angle) {
    // Handles position reached event from scan servo
#if DEBUG
    Serial << "handleReached: dir=" << angle << endl;
#endif
    sr04.start();
  })
  .angle(scanDirections[scanIndex]);

  obstacleTimer.onNext(handleObstacleTimer)
  .interval(OBSTACLE_INTERVAL)
  .continuous(true)
  .start();

  // Init motor controllers
  leftMotor.begin();
  rightMotor.begin();
  motorsTimer.interval(1).onNext([](void *, unsigned long) {
    moveTo(0, 0);
  });
  moveTo(0, 0);

  // Init staqstistics time
  started = millis();
  statsTimer.onNext(handleStatsTimer)
  .interval(10000)
  .continuous(true)
  .start();

  // Final setup
  Serial.println(F("ha"));
  imu.reset();
  serialFlush();
}

/*
   Main loop
*/
void loop() {
  unsigned long now = millis();
  imu.polling();
  counter++;
  voltageTime = now;
  voltageValue = analogRead(VOLTAGE_PIN);

  ledTimer.polling(now);

  obstacleTimer.polling(now);
  servo.polling(now);
  sr04.polling(now);

  statsTimer.polling(now);

  motorsTimer.polling(now);

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

/*

*/
void handleImuData(void*, IMU& imu) {
  imuFailure = false;
  assetTime = millis();
}

/*

*/
void handleWatchDog(void*, IMU& imu) {
  imuFailure = true;
  Serial.print("!! Watch dog status: ");
  Serial.print(imu.status());
  Serial.println();
  imu.reset()
  .kickAt(micros() + 1000000ul);
}

/*
  Handles timeout event from statistics timer
*/
void handleStatsTimer(void *context, unsigned long) {
  statsTime = millis();
  tps = counter * 1000 / (statsTime - started);
  started = statsTime;
  counter = 0;
#if DEBUG
  Serial.print("// Stats: ");
  Serial.println(tps);
#endif
}

/*
   Handles the led timer
*/
void handleLedTimer(void *, unsigned long n) {
  unsigned long ledDivider = imuFailure ? LED_FAST_PULSE_DIVIDER : LED_PULSE_DIVIDER;
  digitalWrite(LED_BUILTIN, (n % ledDivider) == 0 ? HIGH : LOW);

  digitalWrite(BLOCK_LED_PIN, !canMoveForward() &&  (n % BLOCK_PULSE_DIVIDER) == 0 ? LOW : HIGH);
}

/*
   Handles obstacle timer
*/
void handleObstacleTimer(void *, unsigned long i) {
  static unsigned long last = 0;
  int distance = distances[FRONT_SCAN_INDEX];
  if (distance <= STOP_DISTANCE) {
    digitalWrite(PROXY_LED_PIN, LOW);
    last = i;
  } else if (distance > WARN_DISTANCE) {
    digitalWrite(PROXY_LED_PIN, HIGH);
  } else {
    int n = map(distance,
                STOP_DISTANCE, WARN_DISTANCE,
                MIN_OBSTACLE_PULSES, MAX_OBSTACLE_PULSES);
    if (i >= last + n) {
      digitalWrite(PROXY_LED_PIN, LOW);
      last = i;
    } else {
      digitalWrite(PROXY_LED_PIN, HIGH);
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
  unsigned long timeout = args.substring(0, s1).toInt();

  int s2 = args.indexOf(' ', s1 + 1);
  if (s2 <= 0) {
    Serial.println(F("!! Wrong arg[2]"));
    return;
  }
  int left = min(max(MAX_BACKWARD, int(args.substring(s1 + 1, s2).toInt())), MAX_FORWARD);
  int right = min(max(MAX_BACKWARD, int(args.substring(s2 + 1).toInt())), MAX_FORWARD);

  if (isForward(left, right) && !canMoveForward()) {
    left = right = 0;
  }
  moveTo(left, right);
  if (left == 0 && right == 0) {
    motorsTimer.stop();
  } else  {
    motorsTimer.start(timeout);
  }
  sendStatus();
}

/*
   Handles sample event from distance sensor
*/
void handleSample(void *, int distance) {

#if DEBUG
  Serial << "// handleSample: dir=" << scanDirections[scanIndex] << ", distance=" << distance << endl;
#endif

  distances[scanIndex] = distance;
  scanTimes[scanIndex] = millis();

  if (isForward() && !canMoveForward()) {
    moveTo(0, 0);
  }
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
#if DEBUG
  Serial << "processCommand: " << line << endl;
#endif
  strtrim(line, line);
  if (strncmp(line, "ck ", 3) == 0) {
    sendClock(line, time);
  } else if (strcmp(line, "sc") == 0) {
    startFullScanning();
    sendStatus();
  } else if (strcmp(line, "qs") == 0) {
    sendStatus();
  } else if (strcmp(line, "qa") == 0) {
    sendAsset();
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

*/
void sendAsset() {
  float* ypr = imu.ypr();
  float* acc = imu.accel();
  Serial.print(F("as "));
  Serial.print(assetTime);
  Serial.print(F(" "));
  Serial.print(ypr[0]);
  Serial.print(F(" "));
  Serial.print(ypr[1]);
  Serial.print(F(" "));
  Serial.print(ypr[2]);
  Serial.print(F(" "));
  Serial.print(acc[0]);
  Serial.print(F(" "));
  Serial.print(acc[1]);
  Serial.print(F(" "));
  Serial.print(acc[2]);
  Serial.println();
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
bool isForward(int left, int right) {
  return left > 0 || right > 0;
}

boolean isForward() {
  return isForward(leftMotorSpeed, rightMotorSpeed);
}

/*
   Returns true if can move forward
*/
bool canMoveForward() {
  return forwardBlockDistance() > STOP_DISTANCE;
}

int forwardBlockDistance() {
  int dist = MAX_DISTANCE;
  for (int i = CAN_MOVE_MIN_DIR; i <= CAN_MOVE_MAX_DIR; i++) {
    int d = distances[i];
    if (d > 0 && d <= dist) {
      dist = d;
    }
  }
  return dist;
}

/*
   Move Wheelly to direction
*/
void moveTo(int left, int right) {
  leftMotorSpeed = left;
  rightMotorSpeed = right;
  setMotors(left, right);
}

/*

*/
void setMotors(int left, int right) {
  leftMotor.speed(left);
  rightMotor.speed(right);
}

void startFullScanning() {
  sr04.stop();

#if DEBUG
  Serial << "startFullScanning" << endl;
#endif

  isFullScanning = true;
  scanIndex = 0;
  servo.angle(scanDirections[scanIndex]);
}

void sendStatus() {
  Serial.print(F("st "));
  Serial.print(millis());
  Serial.print(F(" "));
  Serial.print(leftMotorSpeed);
  Serial.print(F(" "));
  Serial.print(rightMotorSpeed);
  Serial.print(F(" "));
  for (int i = 0; i < NO_SCAN_DIRECTIONS; i++) {
    Serial.print(scanTimes[i]);
    Serial.print(F(" "));
    Serial.print(scanDirections[i]);
    Serial.print(F(" "));
    Serial.print(distances[i]);
    Serial.print(F(" "));
  }
  Serial.print(voltageTime);
  Serial.print(F(" "));
  Serial.print(voltageValue);
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
