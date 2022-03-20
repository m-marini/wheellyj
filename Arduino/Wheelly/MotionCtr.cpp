#include "MotionCtrl.h"

//#define DEBUG
#include "debug.h"

#define MOTOR_SAFE_INTERVAL 1000ul
#define VALID_ASSET_INTERVAL 500ul

// Power coefficient (1/RAD)
#define K_POWER (180 / 30 / PI)

// max rotation speed 4.66 RAD/sec
#define K_OMEGA (4.66 / 2)

/*
   Returns the normalized angle in range (-PI, PI)
*/
float normAngle(float angle) {
  while (angle <= PI) {
    angle += (PI + PI);
  }
  while (angle > PI) {
    angle -= (PI + PI);
  }
  return angle;
}

/*
  Creates the motion controller
*/
MotionCtrl::MotionCtrl(byte leftForwPin, byte leftBackPin, byte rightForwPin, byte rightBackPin)
  : _leftMotor(leftForwPin, leftBackPin), _rightMotor(rightForwPin, rightBackPin) {
  _kpower = K_POWER; // angle correction power (10 DEG = full rotation power)
  _komega = K_OMEGA;
}

/*
  Initializes the motion controller
*/
MotionCtrl& MotionCtrl::begin() {
  _leftMotor.begin();
  _rightMotor.begin();
  DEBUG_PRINTLN(F("// Motion controller begin"));
  _stopTimer.interval(MOTOR_SAFE_INTERVAL).onNext([](void *ctx, unsigned long) {
    DEBUG_PRINTLN("// Motor timer triggered");
    ((MotionCtrl*)ctx)->speed(0, 0);
  });
  speed(0, 0);
  return *this;
}

/*
  Sets the motion speeds
*/
MotionCtrl& MotionCtrl::speed(int left, int right) {
  _leftSpeed = left;
  _rightSpeed = right;
  unsigned long now = millis();
  if (_leftSpeed == 0 && _rightSpeed == 0) {
    _prevTime = now;
    _expectedYaw = _yaw;
    _assetControl = (now - _assetTime) <= VALID_ASSET_INTERVAL;
  }
  computePower(now);
  if (left == 0 && right == 0) {
    _stopTimer.stop();
  } else  {
    DEBUG_PRINTLN(F("// Motor timer started"));
    _stopTimer.start(this);
  }
}

/*

*/
MotionCtrl& MotionCtrl::polling(unsigned long clockTime) {
  _stopTimer.polling(clockTime);
  unsigned long dt = clockTime - _prevTime;
  float sd = (_leftSpeed - _rightSpeed) / MAX_FORWARD;
  _expectedYaw += sd * _komega * dt * 1e-3;
  _prevTime = clockTime;
  computePower(clockTime);
}

/*

*/
MotionCtrl& MotionCtrl::computePower(unsigned long clockTime) {
  unsigned long dt = clockTime - _assetTime;
  if ((_leftSpeed == 0 && _rightSpeed == 0) || !_assetControl || dt >= VALID_ASSET_INTERVAL) {
    // Invalid asset, disable power control by asset
    power(_leftSpeed, _rightSpeed);
  } else {
    float sl = (float)_leftSpeed / MAX_FORWARD;
    float sr = (float)_rightSpeed / MAX_FORWARD;
    float ss = sl + sr;
    float sd = sl - sr;
    float deltaYaw = normAngle(_yaw - _expectedYaw);
    float ps = ss * cos(deltaYaw);
    float pd = sd - deltaYaw * _kpower;
    float pl = (ps + pd) / 2;
    float pr = (ps - pd) / 2;
    float pk = max(1.0, max(abs(pl), abs(pr)));
    if (pk > 1) {
      pl /= pk;
      pr /= pk;
    }
    DEBUG_PRINT(F("// sl,sr,ss,sd: "));
    DEBUG_PRINT(sl);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINT(sr);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINT(ss);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINTLN(sd);
    DEBUG_PRINT(F("// yaw,yaw0,expYaw,deltaYaw: "));
    DEBUG_PRINT(_yaw * 180 / PI);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINT(_yaw0 * 180 / PI);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINT(expYaw * 180 / PI);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINTLN(deltaYaw * 180 / PI);
    DEBUG_PRINT(F("// pl,pr,ps,pd: "));
    DEBUG_PRINT(pl);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINT(pr);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINT(ps);
    DEBUG_PRINT(F(" "));
    DEBUG_PRINTLN(pd);
    power(round(pl * MAX_FORWARD), round(pr * MAX_FORWARD));
  }
  return *this;
}

/*

*/
MotionCtrl& MotionCtrl::power(int left, int right) {
  /*
    DEBUG_PRINT("// Power ");
    DEBUG_PRINT(left);
    DEBUG_PRINT(", ");
    DEBUG_PRINTLN(right);
  */
  _leftMotor.speed(left);
  _rightMotor.speed(right);
}
