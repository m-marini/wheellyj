#ifndef MotionCtrl_h
#define MotionCtrl_h

#include "MotorCtrl.h"
#include "MotionSensor.h"
#include "Timer.h"

/*
   Multiplexer
*/
class MotionCtrl {
  public:
    MotionCtrl(byte leftForwPin, byte leftBackPin, byte rightForwPin, byte rightBackPin, byte leftSensorPin, byte rightSensorPin);
    MotionCtrl& begin();
    MotionCtrl& polling(unsigned long clockTime = millis());
    MotionCtrl& speed(float left, float right);
    MotionCtrl& reset();
    MotionCtrl& handleMotion(unsigned long clockTime);

    const float x() const {
      return _sensors.x();
    }
    const float y() const {
      return _sensors.y();
    }
    const float angle() const {
      return _sensors.angle();
    }
    const float left() const {
      return _left;
    }
    const float right() const {
      return _right;
    }
    const boolean isForward() const {
      return _left > 0 || _right > 0;
    }


  private:
    MotorCtrl _leftMotor;
    MotorCtrl _rightMotor;
    MotionSensor _sensors;
    Timer _stopTimer;
    Timer _checkTimer;
    float _left;
    float _right;
    float _leftSpeed;
    float _rightSpeed;
    unsigned long _prevTime;

    MotionCtrl& power(float left, float right);
};

#endif
