#include "MotorSensor.h"

//#define DEBUG
#include "debug.h"

#define STOP_TIME 500ul

/*

*/
MotorSensor::MotorSensor(byte sensorPin) {
  _sensorPin = sensorPin;
  _forward = true;
}

/*

*/
MotorSensor& MotorSensor::begin() {
  pinMode(_sensorPin, INPUT);
  _prevTime = millis();
  return *this;
}

/*

*/
MotorSensor& MotorSensor::reset() {
  _forward = true;
  _pulses = 0;
  _noSamples = 0;
  _index = 0;
  _prevTime = millis();
  return *this;
}

/*

*/
MotorSensor& MotorSensor::setDirection(float speed) {
  DEBUG_PRINT(F("// MotorSensor::setDirection "));
  DEBUG_PRINT(speed);
  DEBUG_PRINTLN();

  if (speed > 0) {
    _forward = true;
  } else if (speed < 0) {
    _forward = false;
  }
  return *this;
}

/*

*/
MotorSensor& MotorSensor::polling(unsigned long clockTime) {
  int pulse = digitalRead(_sensorPin);
  int dPulse = pulse != _pulse ? _forward ? 1 : -1 : 0;

  _pulse = pulse;
  unsigned long dt = clockTime - _prevTime;
  if (dPulse != 0) {
    update(dPulse, clockTime);
  } else if (dt > STOP_TIME) {
    _noSamples = 0;
    _index = 0;
    _prevTime = clockTime;
  }
  return *this;
}

/*

*/
MotorSensor& MotorSensor::update(int dPulse, unsigned long clockTime) {
  _pulses += dPulse;

  DEBUG_PRINT(F("// MotorSensor::update dPulse:"));
  DEBUG_PRINT(dPulse);
  DEBUG_PRINT(F(", _pulses "));
  DEBUG_PRINT(_pulses);
  DEBUG_PRINTLN();

  _timestamp[_index] = clockTime;
  _pulsesBuf[_index] = _pulses;

  _index++;
  if (_index >= SPEED_BUFFER_SIZE) {
    _index = 0;
  }
  if (_noSamples < SPEED_BUFFER_SIZE) {
    _noSamples++;
  }

  if (_onSample != NULL) {
    _onSample(_context, dPulse, clockTime, *this);
  }
  return *this;
}

/*

*/
const float MotorSensor::frequency() const {
  if (_noSamples < SPEED_BUFFER_SIZE) {
    return 0;
  } else {
    int last = (_index + SPEED_BUFFER_SIZE - 1) % SPEED_BUFFER_SIZE;
    DEBUG_PRINT(F("// MotorSensor::frequency _index:"));
    DEBUG_PRINT(_index);
    DEBUG_PRINT(F(", last:"));
    DEBUG_PRINT(last);
    DEBUG_PRINTLN();

    long dp = _pulsesBuf[last] - _pulsesBuf[_index];
    unsigned long dt = _timestamp[last] - _timestamp[_index];
    float f = ((float)dp) * 1000 / dt;
    DEBUG_PRINT(F("// MotorSensor::frequency dp:"));
    DEBUG_PRINT(dp);
    DEBUG_PRINT(F(", dt:"));
    DEBUG_PRINT(dt);
    DEBUG_PRINT(F(", f:"));
    DEBUG_PRINT(f);
    DEBUG_PRINTLN();
    return f;
  }
}
