#ifndef MotorCtrl_h
#define MotorCtrl_h

#include "Arduino.h"

#define NO_POINTS 5

/*
 * Multiplexer
 */
class MotorCtrl {
  public:
    MotorCtrl(byte forwPin, byte backPin);
    void begin();
    void speed(float value);
    void setCorrection(float *x, float *y);
    
  private:
    byte _forwPin;
    byte _backPin;
    float *_x, *_y;
    float func(float x);
};

#endif
