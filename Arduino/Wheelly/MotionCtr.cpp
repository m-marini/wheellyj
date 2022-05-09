#include "MotionCtrl.h"

//#define DEBUG
#include "debug.h"
#include "Utils.h"
#include "Fuzzy.h"

#define MOTOR_SAFE_INTERVAL 1000ul
#define MOTOR_CHECK_INTERVAL 300ul

#define MOTOR_FILTER_TIME 0.1f

#define MAX_LIN_SPEED 0.280f

#define GAIN 0.3

#define ON_DIRECTION_RAD  (90 * PI / 180)
#define LINEAR_DIRECTION_RAD  (30 * PI / 180)

const float leftXCorrection[] PROGMEM = { -1,  -0.06055, 0, 0.02311, 1};
const float leftYCorrection[] PROGMEM = { -1, -0.30432, 0, 0.12577, 1};
const float rightXCorrection[] PROGMEM = { -1, -0.03759, 0, 0.02041, 1};
const float rightYCorrection[] PROGMEM = { -1, -0.2667, 0, 0.12648, 1};

/*
  Creates the motion controller
*/
MotionCtrl::MotionCtrl(byte leftForwPin, byte leftBackPin, byte rightForwPin, byte rightBackPin, byte leftSensorPin, byte rightSensorPin)
  : _leftMotor(leftForwPin, leftBackPin),
    _rightMotor(rightForwPin, rightBackPin),
    _sensors(leftSensorPin, rightSensorPin) {

  _sensors.setOnChange([](void*context, unsigned long clockTime, MotionSensor&) {
    DEBUG_PRINTLN(F("// Motor sensors triggered"));
    ((MotionCtrl *)context)-> handleMotion(clockTime);
  }, this);

  _stopTimer.interval(MOTOR_SAFE_INTERVAL);
  _stopTimer.onNext([](void *ctx, unsigned long) {
    DEBUG_PRINTLN(F("// Motor timer triggered"));
    ((MotionCtrl*)ctx)->alt();
  }, this);

  _checkTimer.interval(MOTOR_CHECK_INTERVAL);
  _checkTimer.continuous(true);
  _checkTimer.onNext([](void *ctx, unsigned long) {
    DEBUG_PRINTLN("// Motor check timer triggered");
    ((MotionCtrl*)ctx)->handleMotion(millis());
  }, this);
}

/*
  Initializes the motion controller
*/
void MotionCtrl::begin() {
  _leftMotor.begin();
  _leftMotor.setCorrection(leftXCorrection, leftYCorrection);
  _rightMotor.begin();
  _rightMotor.setCorrection(rightXCorrection, rightYCorrection);
  _sensors.begin();

  DEBUG_PRINTLN(F("// Motion controller begin"));

  alt();
}

/*
  Resets the controller
*/
void MotionCtrl::reset() {
  DEBUG_PRINTLN(F("// MotionCtrl::reset"));
  _sensors.reset();
}

/*

*/
void MotionCtrl::alt() {
  DEBUG_PRINTLN(F("// MotionCtrl::alt"));
  _speed = 0;
  _alt = true;
  power(0, 0);
  _stopTimer.stop();
  _checkTimer.stop();
}

/*

*/
void MotionCtrl::move(float direction, float speed) {
  DEBUG_PRINT(F("// MotionCtrl::move "));
  DEBUG_PRINTF(direction * 180 / PI, 0);
  DEBUG_PRINT(F(" "));
  DEBUG_PRINT(speed);
  DEBUG_PRINTLN();
  _direction = direction;
  _speed = speed;
  if (_alt) {
    _alt = false;
    _stopTimer.start();
    _checkTimer.start();
  } else {
    _stopTimer.restart();
    handleMotion(millis());
  }
}
/*

*/
void MotionCtrl::polling(unsigned long clockTime) {
  _sensors.polling(clockTime);
  _stopTimer.polling(clockTime);
  _checkTimer.polling(clockTime);
}

/*

*/
const boolean MotionCtrl::isForward() const {
  return _speed > 0 || _left > 0 || _right > 0;
}

/*

*/
const boolean MotionCtrl::isBackward() const {
  return _speed < 0 || _left < 0 || _right < 0;
}

/*

*/
void MotionCtrl::handleMotion(unsigned long clockTime) {
  unsigned long dt = clockTime - _prevTime;
  DEBUG_PRINT(F("// MotionCtrl::handleMotion "));
  DEBUG_PRINT(clockTime);
  DEBUG_PRINT(F(", dt: "));
  DEBUG_PRINT(dt);
  DEBUG_PRINT(F(", left: "));
  DEBUG_PRINT(_left);
  DEBUG_PRINT(F(", right: "));
  DEBUG_PRINT(_right);
  DEBUG_PRINTLN();
  if (!_alt && dt > 0) {
    // Compute motor power
    float dir = angle();
    float toDir = _direction;
    float turn = normalRad(toDir - dir);

    DEBUG_PRINT(F("//   dir: "));
    DEBUG_PRINTF(dir * 180 / 2, 0);
    DEBUG_PRINT(F(", to: "));
    DEBUG_PRINTF(toDir * 180 / 2, 0);
    DEBUG_PRINT(F(", turn: "));
    DEBUG_PRINTF(turn * 180 / 2, 0);
    DEBUG_PRINTLN();

    float isCw = fuzzyPositive(turn, ON_DIRECTION_RAD);
    float isCcw = fuzzyPositive(-turn, ON_DIRECTION_RAD);
    float isLin = 1 - fuzzyPositive(abs(turn), LINEAR_DIRECTION_RAD);
    Fuzzy fuzzy;

    fuzzy.add(1, isCw);
    fuzzy.add(-1, isCcw);
    fuzzy.add(0, 1 - max(isCw, isCcw));
    float cwSpeed = fuzzy.defuzzy();

    fuzzy.reset();
    fuzzy.add(_speed, isLin);
    fuzzy.add(0, 1 - isLin);
    float linSpeed = fuzzy.defuzzy();

    DEBUG_PRINT(F("//    isCw: "));
    DEBUG_PRINT(isCw);
    DEBUG_PRINT(F(", isCcw: "));
    DEBUG_PRINT(isCcw);
    DEBUG_PRINT(F(", isLin: "));
    DEBUG_PRINT(isLin);
    DEBUG_PRINT(F(", cwSpeed: "));
    DEBUG_PRINT(cwSpeed);
    DEBUG_PRINT(F(", linSpeed: "));
    DEBUG_PRINT(linSpeed);
    DEBUG_PRINTLN();

    float left = linSpeed + cwSpeed;
    float right = linSpeed - cwSpeed;

    float mx = max(max(abs(left), abs(right)), 1);

    left /= mx;
    right /= mx;

    DEBUG_PRINT(F("// motors: "));
    DEBUG_PRINT(left);
    DEBUG_PRINT(F(", "));
    DEBUG_PRINT(right);
    DEBUG_PRINTLN();
    power(left, right);
  }
}

/*

*/
void MotionCtrl::power(float left, float right) {
  DEBUG_PRINT(F("// MotionCtrl::power "));
  DEBUG_PRINT(left);
  DEBUG_PRINT(F(", "));
  DEBUG_PRINTLN(right);
  _left = left;
  _right = right;
  _leftMotor.speed(_left);
  _rightMotor.speed(_right);
  _sensors.setDirection(_left, _right);
}
