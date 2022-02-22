#ifndef MotorCtrl_h
#define MotorCtrl_h

#include "Arduino.h"

/*
 * Multiplexer
 */
class MotorCtrl {
  public:
    MotorCtrl(int forwardPin, int backwardPin);
    MotorCtrl& begin();
    MotorCtrl& speed(int value);
  private:
    int _forwardPin;
    int _backwardPin;
};

#endif
