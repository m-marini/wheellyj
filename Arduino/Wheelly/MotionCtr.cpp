#include "MotionCtrl.h"

//#define DEBUG
#include "debug.h"

#define MOTOR_SAFE_INTERVAL 10000ul
#define MOTOR_CHECK_INTERVAL 300ul

#define MOTOR_FILTER_TIME 0.1f

#define MAX_LIN_SPEED 0.280f

#define GAIN 0.3

const float leftXCorrection[] = { -1,  -0.06055, 0, 0.02311, 1};
const float leftYCorrection[] = { -1, -0.30432, 0, 0.12577, 1};
const float rightXCorrection[] = { -1, -0.03759, 0, 0.02041, 1};
const float rightYCorrection[] = { -1, -0.2667, 0, 0.12648, 1};

/*
  Creates the motion controller
*/
MotionCtrl::MotionCtrl(byte leftForwPin, byte leftBackPin, byte rightForwPin, byte rightBackPin, byte leftSensorPin, byte rightSensorPin)
  : _leftMotor(leftForwPin, leftBackPin),
    _rightMotor(rightForwPin, rightBackPin),
    _sensors(leftSensorPin, rightSensorPin) {

  _sensors.setOnChange([](void*context, unsigned long clockTime, MotionSensor&) {
    DEBUG_PRINTLN("// Motor sensors triggered");
    ((MotionCtrl *)context)-> handleMotion(clockTime);
  }, this);

  _stopTimer.interval(MOTOR_SAFE_INTERVAL)
  .onNext([](void *ctx, unsigned long) {
    DEBUG_PRINTLN("// Motor timer triggered");
    ((MotionCtrl*)ctx)->speed(0, 0);
  }, this);

  _checkTimer.interval(MOTOR_CHECK_INTERVAL)
  .continuous(true)
  .onNext([](void *ctx, unsigned long) {
    DEBUG_PRINTLN("// Motor check timer triggered");
    ((MotionCtrl*)ctx)->handleMotion(millis());
  }, this);
}

/*
  Initializes the motion controller
*/
MotionCtrl& MotionCtrl::begin() {
  _leftMotor.begin().setCorrection(leftXCorrection, leftYCorrection);
  _rightMotor.begin().setCorrection(rightXCorrection, rightYCorrection);
  _sensors.begin();

  DEBUG_PRINTLN(F("// Motion controller begin"));

  speed(0, 0);
  return *this;
}

/*
  Resets the controller
*/
MotionCtrl& MotionCtrl::reset() {
  _sensors.reset();
  return *this;
}

/*
  Sets the motion speeds
*/
MotionCtrl& MotionCtrl::speed(float left, float right) {
  DEBUG_PRINT(F("// MotionCtrl::speed "));
  DEBUG_PRINT(left);
  DEBUG_PRINT(F(" "));
  DEBUG_PRINT(right);
  DEBUG_PRINTLN();

  bool stopped = _left == 0 && _right == 0;
  _left = left;
  _right = right;
  //power(_left, _right);
  if (stopped) {
    if (left != 0 || right != 0) {
      _stopTimer.start();
      _checkTimer.start();
    }
    power(_left, _right);
  } else if (_left == 0 && _right == 0) {
    // stop
    power(0, 0);
    _stopTimer.stop();
    _checkTimer.stop();
  } else {
    _stopTimer.restart();
    handleMotion(millis());
  }
}

/*

*/
MotionCtrl& MotionCtrl::polling(unsigned long clockTime) {
  _sensors.polling(clockTime);
  _stopTimer.polling(clockTime);
  _checkTimer.polling(clockTime);
}

/*

*/
MotionCtrl& MotionCtrl::handleMotion(unsigned long clockTime) {
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

  if ((_left != 0 || _right != 0) && dt > 0) {
    float lSpeed = _sensors.leftSpeed() / MAX_LIN_SPEED;
    float dvl = _left - lSpeed;
    float left = min(max(_leftSpeed + GAIN * dvl, -1), 1);

    float rSpeed = _sensors.rightSpeed() / MAX_LIN_SPEED;
    float dvr = _right - rSpeed;
    float right = min(max(_rightSpeed + GAIN * dvr, -1), 1);

    DEBUG_PRINT(F("// MotionCtrl::handleMotion lSpeed:"));
    DEBUG_PRINT(lSpeed);
    DEBUG_PRINT(F(", rSpeed:"));
    DEBUG_PRINT(rSpeed);
    DEBUG_PRINTLN();

    // Computes low frequence filtered speeds
    float alpha = min(dt * 1e-3 / MOTOR_FILTER_TIME, 1);
    float notAlpha = 1 - alpha;
    left = _leftSpeed * notAlpha + left * alpha;
    right = _rightSpeed * notAlpha + right * alpha;

    power(left, right);
    _prevTime = clockTime;
  }
}

/*

*/
MotionCtrl& MotionCtrl::power(float left, float right) {
  DEBUG_PRINT("// MotionCtrl::power ");
  DEBUG_PRINT(dt);
  DEBUG_PRINT(", ");
  DEBUG_PRINT(left);
  DEBUG_PRINT(", ");
  DEBUG_PRINTLN(right);
  _leftSpeed = left;
  _rightSpeed = right;
  _leftMotor.speed(_leftSpeed);
  _rightMotor.speed(_rightSpeed);
  _sensors.setDirection(_leftSpeed, _rightSpeed);
}
