#include "MotorCtrl.h"

MotorCtrl::MotorCtrl(int forwardPin, int backwardPin) {
  _forwardPin = forwardPin;
  _backwardPin = backwardPin;
}

MotorCtrl& MotorCtrl::begin(){
  pinMode(_forwardPin, OUTPUT);  
  pinMode(_backwardPin, OUTPUT);  
  return *this;
}

/* 
 * Set speed 
 */
MotorCtrl& MotorCtrl::speed(int value) {
  if (value == 0) {
    digitalWrite(_forwardPin, HIGH);
    digitalWrite(_backwardPin, HIGH);
  } else if (value > 0) {
    digitalWrite(_forwardPin, LOW);
    digitalWrite(_backwardPin, HIGH);
  } else {
    digitalWrite(_forwardPin, HIGH);
    digitalWrite(_backwardPin, LOW);
  }
  return *this;
}
