#include "MotorCtrl.h"

MotorCtrl::MotorCtrl(int enablePin, int forwardBit, int backwardBit) {
  _enablePin = enablePin;
  _forwardBit = forwardBit;
  _backwardBit = backwardBit;
}

MotorCtrl& MotorCtrl::begin(Multiplexer& multiplexer){
  _multiplexer = &multiplexer;
  pinMode(_enablePin, OUTPUT);  
  return *this;
}

/* 
 * Set speed 
 */
MotorCtrl& MotorCtrl::speed(int value) {
  if (value == 0) {
    analogWrite(_enablePin, 0);
    _multiplexer->set(_forwardBit)
      .set(_backwardBit);
  } else if (value > 0) {
    _multiplexer->reset(_forwardBit)
      .set(_backwardBit);
    analogWrite(_enablePin, min(value, 255));
  } else {
    _multiplexer->set(_forwardBit)
      .reset(_backwardBit);
    analogWrite(_enablePin, min(-value, 255));      
  }
  return *this;
}
