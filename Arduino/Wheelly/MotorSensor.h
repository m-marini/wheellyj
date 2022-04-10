#ifndef MotorSensor_h
#define MotorSensor_h

#include "Arduino.h"

#define PULSES_PER_ROOT     40
#define WHEEL_DIAMETER      0.067f

#define DISTANCE_PER_PULSE  (WHEEL_DIAMETER * PI / PULSES_PER_ROOT)

#define SPEED_BUFFER_SIZE 4

/*
   Multiplexer
*/
class MotorSensor {
  public:
    MotorSensor(byte sensorPin);
    MotorSensor& begin();
    MotorSensor& polling(unsigned long clockTime);
    MotorSensor& setDirection(float speed);
    MotorSensor& reset();
    // Sets the callback
    MotorSensor& onSample(void (*callback)(void* context, int dPulse, unsigned long clockTime, MotorSensor& sensor), void* context = NULL) {
      _onSample = callback;
      _context = context;
      return *this;
    }

    const long pulses() const {
      return _pulses;
    }

    const float distance() const {
      return _pulses * DISTANCE_PER_PULSE;
    }

    const float frequency() const;

    const float speed() const {
      return frequency() * DISTANCE_PER_PULSE;
    }

  private:
    byte _sensorPin;
    byte _pulse;
    long _pulses;
    bool _forward;
    void (*_onSample)(void*, int, unsigned long clockTime, MotorSensor&);
    void *_context;
    unsigned long _prevTime;

    byte _index;
    byte _noSamples;
    unsigned long _timestamp[SPEED_BUFFER_SIZE];
    long _pulsesBuf[SPEED_BUFFER_SIZE];

    MotorSensor& update(int dPulse, unsigned long clockTime);
};

#endif
