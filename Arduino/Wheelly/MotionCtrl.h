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
    void begin();
    void polling(unsigned long clockTime = millis());
    void reset();
    void handleMotion(unsigned long clockTime);
    void move(float direction, float speed);
    void alt();

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
    const boolean isForward() const;

    const boolean isAlt() const {
      return _alt;
    }

    void angle(float angle) {
      _sensors.angle(angle);
    }

  private:
    MotorCtrl _leftMotor;
    MotorCtrl _rightMotor;
    MotionSensor _sensors;
    Timer _stopTimer;
    Timer _checkTimer;

    float _direction;
    float _speed;
    boolean _alt;

    float _left;
    float _right;
    unsigned long _prevTime;

    void power(float left, float right);
};

#endif
