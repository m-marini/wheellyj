#include "IRremote.h"
#include "Timer.h"
#include "Multiplexer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "RemoteCtrl.h"
#include "MotorCtrl.h"

#define RED1 3
#define RED2 2
#define RED3 1
#define RED4 0
#define LEFT_BACK 4
#define LEFT_FORW 5
#define RIGHT_BACK 7
#define RIGHT_FORW 6

#define MAX_FORWARD 255
#define MAX_BACKWARD -255

#define THRESHOLD_DISTANCE  60
#define STOP_DISTANCE  (THRESHOLD_DISTANCE / 2)

#define LATCH_PIN 8
#define CLOCK_PIN 7
#define DATA_PIN 12

#define TRIGGER_PIN 9
#define ECHO_PIN 4

#define SERVO_PIN 10

#define RECEIVER_PIN 11

#define ENABLE_PIN 5

#define DIR_0   0
#define DIR_N   1
#define DIR_NE  2
#define DIR_E   3
#define DIR_SE  4
#define DIR_S   5
#define DIR_SW  6
#define DIR_W   7
#define DIR_NW   8

#define NO_DIRECTIONS (sizeof(speedsByDirection) / sizeof(speedsByDirection[0]))

Timer timer;
Timer timer1;

Multiplexer multiplexer(LATCH_PIN, CLOCK_PIN, DATA_PIN);

SR04 sr04(TRIGGER_PIN, ECHO_PIN);

AsyncServo servo;

MotorCtrl leftMotor(ENABLE_PIN, LEFT_FORW, LEFT_BACK);
MotorCtrl rightMotor(ENABLE_PIN, RIGHT_FORW, RIGHT_BACK);

RemoteCtrl remoteCtrl(RECEIVER_PIN);

long counter;
unsigned long started;
int distance;
byte direction;
int speedsByDirection[][2] = {
  {0, 0},
  {MAX_FORWARD, MAX_FORWARD},   // N
  {MAX_FORWARD, 0},             // NE
  {MAX_FORWARD, MAX_BACKWARD},  // E
  {0, MAX_BACKWARD},            // SE
  {MAX_BACKWARD, MAX_BACKWARD}, // S
  {MAX_BACKWARD, 0},            // SW
  {MAX_BACKWARD, MAX_FORWARD},  // W
  {0, MAX_FORWARD},             // NW
};

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
  multiplexer.begin();
  sr04.begin();
  servo.attach(SERVO_PIN);
  leftMotor.begin(multiplexer);
  rightMotor.begin(multiplexer);
  remoteCtrl.begin();
  
  servo.angle(90);
  multiplexer.reset().flush();

  digitalWrite(LED_BUILTIN, LOW);
  for (int i = 0; i < 3; i++) {
    delay(100);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(50);
    digitalWrite(LED_BUILTIN, LOW);
  }
  
  Serial.println(F("Hello! I'm Wheelly"));

  unsigned long times[] = {50, 950};
  timer.onNext(&handleNext)
    .intervals(2, times)
    .continuous(true)
    .start();
  timer1.onNext(&handleStats)
    .interval(10000)
    .continuous(true)
    .start();

  sr04.noSamples(3)
    .onSample(&handleSample)
    .start();

  servo.onReached(handleReached)
    .angle(90);

  remoteCtrl.onData(&handleData);

  started = millis();
}

void loop() {
  counter++;
  timer.polling();
  timer1.polling();
  servo.polling();
  sr04.polling();
  remoteCtrl.polling();
  multiplexer.flush();
} 

void moveTo(int direction) {
  direction = max(direction, DIR_0);
  if (direction >= NO_DIRECTIONS) {
    direction = DIR_0;
  }
  int left = speedsByDirection[direction][0];
  int right = speedsByDirection[direction][1];
  leftMotor.speed(left);
  rightMotor.speed(right);
}

void handleData(decode_results& results) {
  switch (results.value) {
    case KEY_1:
      moveTo(DIR_NW);
      break;
    case KEY_2:
      moveTo(DIR_N);
      break;
    case KEY_3:
      moveTo(DIR_NE);
      break;
    case KEY_4:
      moveTo(DIR_W);
      break;
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
      moveTo(DIR_0);
      break;
    case KEY_6:
      moveTo(DIR_E);
      break;
    case KEY_7:
      moveTo(DIR_SW);
      break;
    case KEY_8:
      moveTo(DIR_S);
      break;
    case KEY_9:
      moveTo(DIR_SE);
      break;
  }
}

void handleReached(void *, int angle) {
  Serial.print("Reached ");  
  Serial.println(angle);
}

void handleSample(void *, int distance) {
  showDistance(distance);
  if (distance < STOP_DISTANCE) {
    moveTo(DIR_0);
  }
  sr04.start();
}

void showDistance(int distance) {
  multiplexer.set(RED1, distance <= THRESHOLD_DISTANCE);
  multiplexer.set(RED2, distance <= (THRESHOLD_DISTANCE * 3) / 4);
  multiplexer.set(RED3, distance <= THRESHOLD_DISTANCE / 2);
  multiplexer.set(RED4, distance <= THRESHOLD_DISTANCE / 4);  
}

void handleNext(void *, int i, long) {
  if (i == 0){
    digitalWrite(LED_BUILTIN, LOW);
  } else {
    digitalWrite(LED_BUILTIN, HIGH);
  }
}

void handleStats(void *context, int i, long j) {
  Serial.print("Stats: ");
  Serial.println (counter * 1000 / (millis() - started));
  started = millis();
  counter = 0;
}
