#ifndef AsyncServo_h
#define AsyncServo_h

#include "Arduino.h"
#include "Timer.h"
#include <Servo.h>

/*
 * Multiplexer
 */
class AsyncServo {
  public:
    AsyncServo();
    AsyncServo& attach(int pin);
    AsyncServo& onReached(void (*callback)(void *context, int angle));
    AsyncServo& angle(void* context, int value);
    AsyncServo& angle(int value) {return angle(NULL, value);}
    AsyncServo& polling();

    int angle() const {return _angle;}

  private:
    Servo _servo;
    Timer _timer;
    void (*_onReached)(void *context, int angle);
    int _angle;
    void *_context;

    static void _handleTimeout(void *, int, long);
};

#endif
