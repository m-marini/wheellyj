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
    void begin();
    void polling(unsigned long clockTime);
    void setDirection(float leftForward, float rightForward);
    void setOnChange(void (*callback)(void* context, unsigned long clockTime, MotionSensor& sensor), void* context = NULL);

    void angle(float angle) {
      _angle = angle;
    }

    void reset();

    void setLeftPulses(int dPulse) {
      _dl = dPulse;
    }
    void setRightPulses(int dPulse) {
      _dr = dPulse;
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

    void update(unsigned long clockTime);
};

float normAngle(float angle);

#endif
