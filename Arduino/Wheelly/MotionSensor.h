#ifndef MotionSensor_h
#define MotionSensor_h

#include "Arduino.h"
#include "MotorSensor.h"

#define TRACK               0.136f

/*
   Multiplexer
*/
class MotionSensor {
  public:
    MotionSensor(byte leftPin, byte rightPin);
    MotionSensor& begin();
    MotionSensor& polling(unsigned long clockTime);
    MotionSensor& setDirection(float leftForward, float rightForward);
    MotionSensor& setOnChange(void (*callback)(void* context, unsigned long clockTime, MotionSensor& sensor), void* context = NULL);

    MotionSensor& angle(float angle) {
      _angle = angle;
      return *this;
    }

    MotionSensor& reset();

    MotionSensor& setLeftPulses(int dPulse) {
      _dl = dPulse;
      return *this;
    }
    MotionSensor& setRightPulses(int dPulse) {
      _dr = dPulse;
      return *this;
    }

    const float angle() const {
      return _angle;
    }

    const float xPulses() const {
      return _xPulses;
    }

    const float yPulses() const {
      return _yPulses;
    }

    const long rightPulses() const {
      return _rightSensor.pulses();
    }

    const long leftPulses() const {
      return _leftSensor.pulses();
    }

    const float x() const {
      return _xPulses * DISTANCE_PER_PULSE;
    }
    const float y() const {
      return _yPulses * DISTANCE_PER_PULSE;
    }

    const float leftSpeed() const {
      return _leftSensor.speed();
    }

    const float rightSpeed() const {
      return _rightSensor.speed();
    }

  private:
    MotorSensor _leftSensor;
    MotorSensor _rightSensor;
    float _angle;
    float _xPulses;
    float _yPulses;
    int _dl;
    int _dr;
    void (*_onChange)(void*, unsigned long, MotionSensor&);
    void* _context;

    MotionSensor& update(unsigned long clockTime);
};

float normAngle(float angle);

#endif
