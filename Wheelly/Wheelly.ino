#include "IRremote.h"
#include "Timer.h"
#include "Multiplexer.h"
#include "SR04.h"
#include "AsyncServo.h"
#include "RemoteCtrl.h"
#include "MotorCtrl.h"

#define RED1 4
#define RED2 5
#define RED3 6
#define RED4 7  
#define LEFT_BACK 0
#define LEFT_FORW 1
#define RIGHT_FORW 2
#define RIGHT_BACK 3

#define MAX_FORWARD 255
#define MAX_BACKWARD -255

#define THRESHOLD_DISTANCE  50
#define STOP_DISTANCE  (THRESHOLD_DISTANCE / 2)

#define LATCH_PIN 8
#define CLOCK_PIN 7
#define DATA_PIN 12

#define TRIGGER_PIN 9
#define ECHO_PIN 4

#define SERVO_PIN 10

#define RECEIVER_PIN 11

#define ENABLE_LEFT_PIN 5
#define ENABLE_RIGHT_PIN 6

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

Timer timer;
Timer timer1;

Multiplexer multiplexer(LATCH_PIN, CLOCK_PIN, DATA_PIN);

SR04 sr04(TRIGGER_PIN, ECHO_PIN);

AsyncServo servo;

MotorCtrl leftMotor(ENABLE_LEFT_PIN, LEFT_FORW, LEFT_BACK);
MotorCtrl rightMotor(ENABLE_RIGHT_PIN, RIGHT_FORW, RIGHT_BACK);

RemoteCtrl remoteCtrl(RECEIVER_PIN);

long counter;
unsigned long started;
int distance;
byte direction;
int speedsByDirection[][2] = {
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
  if (direction < 0 || direction >= NO_DIRECTIONS) {
    direction = STOP;
  }
  int left = speedsByDirection[direction][0];
  int right = speedsByDirection[direction][1];
  leftMotor.speed(left);
  rightMotor.speed(right);
}

void handleData(decode_results& results) {
  switch (results.value) {
    case KEY_1:
      moveTo(FORWARD_LEFT);
      break;
    case KEY_2:
      moveTo(FORWARD);
      break;
    case KEY_3:
      moveTo(FORWARD_RIGHT);
      break;
    case KEY_4:
      moveTo(LEFT);
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
      moveTo(STOP);
      break;
    case KEY_6:
      moveTo(RIGHT);
      break;
    case KEY_7:
      moveTo(BACKWARD_LEFT);
      break;
    case KEY_8:
      moveTo(BACKWARD);
      break;
    case KEY_9:
      moveTo(BACKWARD_RIGHT);
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
    moveTo(STOP);
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
