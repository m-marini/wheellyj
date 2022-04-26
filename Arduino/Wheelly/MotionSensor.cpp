#include "MotionSensor.h"

//#define DEBUG
#include "debug.h"

#define ANGLE_PER_PULSE (DISTANCE_PER_PULSE / TRACK)

/*

*/
void handleLeftSensor(void* context, int dPulse, unsigned long, MotorSensor&) {
  DEBUG_PRINT(F("// MotionSensor::handleLeftSensor "));
  DEBUG_PRINT(dPulse);
  DEBUG_PRINTLN();
  ((MotionSensor*) context)->setLeftPulses(dPulse);
}

/*

*/
void handleRightSensor(void* context, int dPulse, unsigned long, MotorSensor&) {
  DEBUG_PRINT(F("// MotionSensor::handleRightSensor "));
  DEBUG_PRINT(dPulse);
  DEBUG_PRINTLN();
  ((MotionSensor*) context)->setRightPulses(dPulse);
}

/*

*/
MotionSensor::MotionSensor(byte leftPin, byte rightPin) :
  _leftSensor(leftPin), _rightSensor(rightPin) {
  _leftSensor.onSample(handleLeftSensor, this);
  _rightSensor.onSample(handleRightSensor, this);
}

void MotionSensor::begin() {
  _leftSensor.begin();
  _rightSensor.begin();
}

void MotionSensor::reset() {
  _leftSensor.reset();
  _rightSensor.reset();
  _xPulses = 0;
  _yPulses = 0;
  _angle = 0;
}

void MotionSensor::setDirection(float left, float right) {
  DEBUG_PRINT(F("// MotionSensor::setDirection "));
  DEBUG_PRINT(left);
  DEBUG_PRINT(F(" "));
  DEBUG_PRINT(right);
  DEBUG_PRINTLN();

  _leftSensor.setDirection(left);
  _rightSensor.setDirection(right);
}

void MotionSensor::polling(unsigned long clockTime) {
  _dl = _dr = 0;
  _leftSensor.polling(clockTime);
  _rightSensor.polling(clockTime);
  if (_dl != 0 || _dr != 0) {
    update(clockTime);
  }
}

void MotionSensor::update(unsigned long clockTime) {
  DEBUG_PRINT(F("// MotionSensor::update "));
  DEBUG_PRINT(_dl);
  DEBUG_PRINT(F(" "));
  DEBUG_PRINT(_dr);
  DEBUG_PRINTLN();

  // Updates location
  float sa = sinf(_angle);
  float ca = cosf(_angle);
  float ds = ((float)(_dl + _dr)) / 2;
  _xPulses += ca * ds;
  _yPulses += sa * ds;

  DEBUG_PRINT(F("// x,y "));
  DEBUG_PRINT(_xPulses);
  DEBUG_PRINT(F(" "));
  DEBUG_PRINT(_yPulses);
  DEBUG_PRINTLN();

  // Updates angle
  _angle = normAngle(_angle + (_dl - _dr) * ANGLE_PER_PULSE);

  DEBUG_PRINT(F("// angle "));
  DEBUG_PRINT(_angle * 180 / PI);
  DEBUG_PRINTLN();

  DEBUG_PRINT(F("// location "));
  DEBUG_PRINT(x());
  DEBUG_PRINT(F(", "));
  DEBUG_PRINT(y());
  DEBUG_PRINTLN();

  if (_onChange != NULL) {
    _onChange(_context, clockTime, *this);
  }
}

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
 * 
 */
void MotionSensor::setOnChange(void (*callback)(void* context, unsigned long clockTime, MotionSensor& sensor), void* context = NULL){
  _onChange = callback;
  _context = context;
}
