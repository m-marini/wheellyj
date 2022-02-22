//#include <Streaming.h>

#include "CommandParser.h"

#include "debug.h"
#include "Timer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "MotorCtrl.h"
#include "AsyncSerial.h"

/*
 * Pins
 */
#define RIGHT_BACK_PIN  9
#define RIGHT_FORW_PIN  3
#define LEFT_FORW_PIN   10
#define LEFT_BACK_PIN   11
#define RED_PIN         2
#define YELLOW_PIN      4
#define GREEN_PIN       8
#define BLOCK_LED_PIN   12
#define SERVO_PIN       5
#define ECHO_PIN        6
#define TRIGGER_PIN     7
#define VOLTAGE_PIN     A3

/*
 * Serial config
 */
#define SERIAL_BPS  115200

/*
 * Multiplexer outputs
 */

/*
 * Motor speeds
 */
#define MAX_FORWARD   4
#define MAX_BACKWARD  -4
#define NUM_PULSE     4

/*
 * Distances
 */
#define STOP_DISTANCE     30
#define WARNING_DISTANCE  50
#define INFO_DISTANCE     70

/*
 * Intervals
 */
#define SCAN_INTERVAL   10000ul
#define MOVE_INTERVAL   750ul
#define MOTOR_PULSE     100ul

/*
 * Scanner constants
 */
#define NO_SAMPLES          5
#define FRONT_SCAN_INDEX    (NO_SCAN_DIRECTIONS / 2)
#define NO_SCAN_DIRECTIONS  (sizeof(scanDirections) / sizeof(scanDirections[0]))
#define CAN_MOVE_MIN_DIR    (FRONT_SCAN_INDEX - 1)
#define CAN_MOVE_MAX_DIR    (FRONT_SCAN_INDEX + 1)
#define SERVO_OFFSET        -7

#define COMMANDS            3
#define COMMAND_ARGS        4
#define COMMAND_NAME_LENGTH 2
#define COMMAND_ARG_SIZE    64
#define RESPONSE_SIZE       64

typedef CommandParser<COMMANDS, COMMAND_ARGS, COMMAND_NAME_LENGTH, COMMAND_ARG_SIZE, RESPONSE_SIZE> Parser;

Parser parser;

/*
 * Global variables
 */

Timer ledTimer;
Timer statsTimer;
Timer motorsTimer;
Timer pcmMotorsTimer;

SR04 sr04(TRIGGER_PIN, ECHO_PIN);

AsyncServo servo;

MotorCtrl leftMotor(LEFT_FORW_PIN, LEFT_BACK_PIN);
MotorCtrl rightMotor(RIGHT_FORW_PIN, RIGHT_BACK_PIN);

AsyncSerial asyncSerial;

/*
 * Stats
 */
long counter;
unsigned long started;
unsigned long statsTime;
unsigned long tps;

/*
 * Obstacle distance scanner
 */
const int scanDirections[] = {
  0, 30, 60, 90, 120, 150, 180
};
bool isFullScanning;
int scanIndex;  // Scan index
int distances[NO_SCAN_DIRECTIONS];
unsigned long scanTimes[NO_SCAN_DIRECTIONS];

/*
 * Motor speeds [left, right] by direction
 */
int leftMotorSpeed;
int rightMotorSpeed;
const static unsigned long standbyTime[] = {50, 1450};

/*
 * Voltage sensor
 */
unsigned long voltageTime;
int voltageValue;
 

/*
 * Set up
 */
void setup() {
  Serial.begin(SERIAL_BPS);
  pinMode(LED_BUILTIN, OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  pinMode(YELLOW_PIN, OUTPUT);
  pinMode(RED_PIN, OUTPUT);
  pinMode(BLOCK_LED_PIN, OUTPUT);
  sr04.begin();
  servo.attach(SERVO_PIN);
  leftMotor.begin();
  rightMotor.begin();
  
  digitalWrite(LED_BUILTIN, LOW);
  for (int i = 0; i < 3; i++) {
    delay(100);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(50);
    digitalWrite(LED_BUILTIN, LOW);
  }
  
  /*
   * Init led timer
   */
  ledTimer.onNext([](void *, int i, long) {
    /*
     * Handles timeout event from LED blink timer
     */
    digitalWrite(LED_BUILTIN, i == 0 ? LOW : HIGH);
  })
  .intervals(2, standbyTime)
  .continuous(true)
  .start();

  /*
   * Init stats timer
   */
  statsTimer.onNext([](void *context, int i, long j) {
    /*
    * Handle timeout event from statistics timer
    */

    statsTime = millis();
    tps = counter * 1000 / (statsTime - started);
    started = statsTime;
    counter = 0;
#if DEBUG
    Serial.print("// Stats: ");
    Serial.println(tps);
#endif
  }).interval(10000).continuous(true).start();

  motorsTimer.interval(1).onNext([](void *, int i, long) {
    moveTo(0, 0);
  });
  pcmMotorsTimer.interval(MOTOR_PULSE)
    .onNext(&handlePcmMotorsTimer)
    .continuous(true).start();

  /*
   * Init distance sensor
   */
  sr04.noSamples(NO_SAMPLES)
    .onSample(&handleSample);

  /*
   * Init sensor servo
   */
  servo
    .offset(SERVO_OFFSET)
    .onReached([](void *, int angle) {
    /*
     * Handles position reached event from scan servo
     */
#if DEBUG
    Serial << "handleReached: dir=" << angle << endl;
#endif

    sr04.start();
  }).angle(scanDirections[FRONT_SCAN_INDEX]);

  /*
   * Init async serial port
   */
  asyncSerial.onData([](void *, const char *line, const Timing& timing) {
    processCommand(line, timing);
  });

  /*
   * Init parser
   */
  parser.registerCommand("mt", "uii", &handleMtCommand);
  parser.registerCommand("sc", "", [](Parser::Argument *args, char *response) {
    startFullScanning();
    sendStatus();
  });
  parser.registerCommand("qs", "", [](Parser::Argument *args, char *response) {
    sendStatus();
  });

  scanIndex = FRONT_SCAN_INDEX;
  servo.angle(scanDirections[scanIndex]);
  moveTo(0, 0);
    
  Serial.println();
  Serial.println(F("ha"));
  started = millis();
  serialFlush();
}

/*
 * Main loop
 */
void loop() {
  counter++;
  voltageTime = millis();
  voltageValue = analogRead(VOLTAGE_PIN);
  ledTimer.polling();
  statsTimer.polling();
  motorsTimer.polling();
  pcmMotorsTimer.polling();
  servo.polling();
  sr04.polling();
  asyncSerial.polling();
}

void serialFlush() {
  while (Serial.available() > 0) {
    Serial.read(); 
  }
}

/*
 * 
 */
void processCommand(const char *line, const Timing& timing) {

#if DEBUG
  Serial << "processCommand: " << line << endl;
#endif

  char response[Parser::MAX_RESPONSE_SIZE];
  char cmd[BUFFER_SIZE + 1];
  strtrim(cmd, line);

  if (strncmp(cmd, "ck ", 3) == 0) {
    sendClock(cmd, timing);
  } else if (strncmp(cmd, "//", 2) == 0) {
    // Ignore comments
  } else if (strncmp(cmd, "!!", 2) == 0) {
    // Ignore errors
  } else if (strlen(cmd) == 0) {
    // Ignore empty commands
  } else if (!parser.processCommand(cmd, response)) {
    Serial.print("!! ");
    Serial.println(response);
  }
}

/*
 * 
 */
void sendClock(const char *cmd, const Timing& timing) {
    Serial.print(cmd);
    Serial.print(F(" "));
    Serial.print(timing.millis);
    Serial.print(F(" "));
    unsigned long ms = millis();
    Serial.print(ms);
    Serial.println();
}

/*
 *  Handles mt command
 */
void handleMtCommand(Parser::Argument *args, char *response) {
  unsigned long timeout = args[0].asUInt64;
  int left = min(max(MAX_BACKWARD, int(args[1].asInt64)), MAX_FORWARD);
  int right = min(max(MAX_BACKWARD, int(args[2].asInt64)), MAX_FORWARD);
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
 * Handles sample event from distance sensor
 */
void handleSample(void *, int distance) {

#if DEBUG
  Serial << "handleSample: dir=" << scanDirections[scanIndex] << ", distance=" << distance << endl;
#endif

  distances[scanIndex] = distance;
  scanTimes[scanIndex] = millis();

  showDistance(distance);

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
 * Handle pcm motors timer
 */
void handlePcmMotorsTimer(void *, int, long i) {
  int left = (i % NUM_PULSE) < abs(leftMotorSpeed) ? leftMotorSpeed : 0;
  int right = (i % NUM_PULSE) < abs(rightMotorSpeed) ? rightMotorSpeed : 0;
  setMotors(left, right);
#if DEBUG
  Serial.print("// left right");
  Serial.print(left, right);
  Serial.println();
#endif
}

/*
 * 
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
 * Returns true if forward direction
 */
bool isForward(int left, int right) {
  return left > 0 || right > 0;
}

boolean isForward() {
  return isForward(leftMotorSpeed, rightMotorSpeed);
}

/*
 * Returns true if can move forward 
 */
bool canMoveForward() {
  /*
  for (int i = 1; i < NO_SCAN_DIRECTIONS - 1; i++) {
    int d = distances[i];
    if (d > 0 && d <= STOP_DISTANCE) {
      return false;
    }
  }
  */
  for (int i = CAN_MOVE_MIN_DIR; i <= CAN_MOVE_MAX_DIR; i++) {
    int d = distances[i];
    if (d > 0 && d <= STOP_DISTANCE) {
      return false;
    }
  }
  return true;
}

/*
 * Move Wheelly to direction
 */
void moveTo(int left, int right) {
  leftMotorSpeed = left;
  rightMotorSpeed = right;
  if (left == 0 && right == 0) {
    setMotors(0, 0);    
  }
}

/*
 * 
 */
void setMotors(int left, int right) {
  leftMotor.speed(left);
  rightMotor.speed(right);  
}

/*
 * Show distances
 */
void showDistance(int distance) {
  int green = distance > 0 && distance <= INFO_DISTANCE ? LOW : HIGH;
  int yellow = distance > 0 && distance <= WARNING_DISTANCE ? LOW : HIGH;
  int red = distance > 0 && distance <= STOP_DISTANCE ? LOW : HIGH;
  int block = !canMoveForward() ? LOW : HIGH;
  digitalWrite(GREEN_PIN, green);
  digitalWrite(YELLOW_PIN, yellow);
  digitalWrite(RED_PIN, red);
  digitalWrite(BLOCK_LED_PIN, block);
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
 * Process serial event 
 */
void serialEvent() {
  asyncSerial.serialEvent();
}
