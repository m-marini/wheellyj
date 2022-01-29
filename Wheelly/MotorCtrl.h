#ifndef MotorCtrl_h
#define MotorCtrl_h

#include "Arduino.h"
#include "Multiplexer.h"
/*
 * Multiplexer
 */
class MotorCtrl {
  public:
    MotorCtrl(int enablePin, int forwardBit, int backwardBit);
    MotorCtrl& begin(Multiplexer& multiplexer);
    MotorCtrl& speed(int value);
  private:
    Multiplexer* _multiplexer;
    int _enablePin;
    int _forwardBit;
    int _backwardBit;
};

#endif
