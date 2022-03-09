#ifndef MotionCtrl_h
#define MotionCtrl_h

#include "MotorCtrl.h"
#include "Timer.h"

/*
   Motor speeds
*/
#define MAX_FORWARD   255
#define MAX_BACKWARD  -255

/*
   Multiplexer
*/
class MotionCtrl {
  public:
    MotionCtrl(byte leftForwPin, byte leftBackPin, byte rightForwPin, byte rightBackPin);
    MotionCtrl& begin();
    MotionCtrl& polling(unsigned long clockTime = millis());
    MotionCtrl& speed(int left, int right);
    MotionCtrl& yaw(float yaw) {
      _yaw = yaw;
      return *this;
    };
    MotionCtrl& assetTime(unsigned long value) {
      _assetTime = value;
      return *this;
    };

    const int leftSpeed() const {
      return _leftSpeed;
    }
    const int rightSpeed() const {
      return _rightSpeed;
    }
    const boolean isForward() const {
      return _leftSpeed > 0 || _rightSpeed > 0;
    }


  private:
    MotorCtrl _leftMotor;
    MotorCtrl _rightMotor;
    float _komega;
    float _kpower;
    Timer _stopTimer;
    float _yaw;
    float _expectedYaw;
    int _leftSpeed;
    int _rightSpeed;
    unsigned long _assetTime;
    unsigned long _prevTime;
    bool _assetControl;

    MotionCtrl& power(int left, int right);
    MotionCtrl& computePower(unsigned long clockTime = millis());
};

#endif
