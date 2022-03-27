#include "MotionCtrl.h"

//#define DEBUG
#include "debug.h"

#define MOTOR_SAFE_INTERVAL 1000ul

#define MAX_LIN_SPEED 0.319
#define MIN_REACTION_DISTANCE (2 * DISTANCE_PER_PULSE)

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
}

/*
  Initializes the motion controller
*/
MotionCtrl& MotionCtrl::begin() {
  _leftMotor.begin().setCorrection(leftXCorrection, leftYCorrection);
  _rightMotor.begin().setCorrection(rightXCorrection, rightYCorrection);
  _sensors.begin();
  
  DEBUG_PRINTLN(F("// Motion controller begin"));
  _stopTimer.interval(MOTOR_SAFE_INTERVAL).onNext([](void *ctx, unsigned long) {
    DEBUG_PRINTLN("// Motor timer triggered");
    ((MotionCtrl*)ctx)->speed(0, 0);
  });
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
  _left = left;
  _right = right;

  unsigned long now = millis();
  if (_left != 0 && _right != 0) {
    _stopTimer.start(this);
    _prevTime = now;
    _expectedYaw = _sensors.angle();
    _expectedX = _sensors.x();
    _expectedY = _sensors.y();
  } else {
    _stopTimer.stop();
  }
  power(_left, _right);
}

/*

*/
MotionCtrl& MotionCtrl::polling(unsigned long clockTime) {
  _sensors.polling(clockTime);
  _stopTimer.polling(clockTime);

  // Computes exepected asset
  if (_left != 0 && _right != 0) {
    unsigned long dt = clockTime - _prevTime;
    if (dt > 0) {
      float ds = (_left + _right) * MAX_LIN_SPEED * dt / 2000;
      _expectedX += ds * cosf(_expectedYaw);
      _expectedY += ds * sinf(_expectedYaw);

      float dyaw = (_left - _right) * MAX_LIN_SPEED * dt / 1000 / TRACK;
      _expectedYaw = normAngle(_expectedYaw + dyaw);
      DEBUG_PRINT(F("// dt:"));
      DEBUG_PRINT(dt);
      DEBUG_PRINT(F(" expX:"));
      DEBUG_PRINT(_expectedX);
      DEBUG_PRINT(F(" expY:"));
      DEBUG_PRINT(_expectedY);
      DEBUG_PRINT(F(" expYaw:"));
      DEBUG_PRINT(_expectedYaw * 180 / PI);
      DEBUG_PRINTLN();
      computePower(dt);
    }
  }
  _prevTime = clockTime;
}

/*

*/
MotionCtrl& MotionCtrl::computePower(unsigned long dt) {
  float dx = _expectedX - _sensors.x();
  float dy = _expectedY - _sensors.y();
  float alpha = _sensors.angle();
  float dq = dx * cos(alpha) + dy * sin(alpha);
  float dbeta = normAngle(_expectedYaw - alpha);
  float l = 0, r = 0;
  DEBUG_PRINT(F("// dx:"));
  DEBUG_PRINT(dx);
  DEBUG_PRINT(F(" dy:"));
  DEBUG_PRINT(dy);
  DEBUG_PRINT(F(" dq:"));
  DEBUG_PRINT(dq);
  DEBUG_PRINT(F(" alpha:"));
  DEBUG_PRINT(_sensors.angle() * 180 / PI);
  DEBUG_PRINTLN();

  float sumLR = dq * 2000.0 / MAX_LIN_SPEED / dt;
  float diffLR = dbeta * 1000.0 * TRACK / MAX_LIN_SPEED / dt;

  float expLeft = (sumLR + diffLR) / 2;
  float expRight = (sumLR - diffLR) / 2;

  float lambda = min(1, 1 / max(abs(expLeft), abs(expRight)));
  l = expLeft * lambda;
  r = expRight * lambda;

  DEBUG_PRINT(F("// beta:"));
  DEBUG_PRINT(_expectedYaw * 180 / PI);
  DEBUG_PRINT(F(" dbeta:"));
  DEBUG_PRINT(dbeta * 180 / PI);
  DEBUG_PRINT(F(" sumLR:"));
  DEBUG_PRINT(sumLR);
  DEBUG_PRINT(F(" diffLR:"));
  DEBUG_PRINT(diffLR);
  DEBUG_PRINT(F(" expL:"));
  DEBUG_PRINT(expLeft);
  DEBUG_PRINT(F(" expR:"));
  DEBUG_PRINT(expRight);
  DEBUG_PRINT(F(" lambda:"));
  DEBUG_PRINT(lambda);
  DEBUG_PRINTLN();

  power(l, r);
  DEBUG_PRINT(F("// left:"));
  DEBUG_PRINT(l);
  DEBUG_PRINT(F(" right:"));
  DEBUG_PRINT(r);
  DEBUG_PRINTLN();
  return *this;
}

/*

*/
MotionCtrl& MotionCtrl::power(float left, float right) {
  /*
    DEBUG_PRINT("// Power ");
    DEBUG_PRINT(left);
    DEBUG_PRINT(", ");
    DEBUG_PRINTLN(right);
  */
  _leftMotor.speed(left);
  _rightMotor.speed(right);
  _sensors.setDirection(left, right);
}
