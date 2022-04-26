#ifndef SR04_h
#define SR04_h

#include "Arduino.h"
#include "Timer.h"

/*
 * ASynchronous SR04
 */
class SR04 {
  public:
    SR04(byte triggerPin, byte echoPin);

    // Sets a single interval
    void begin();

    // Sets a single interval
    void inactivity(unsigned long interval);

    // Sets a single interval
    void noSamples(int noSamples);

    // Starts the sampling
    void start();

    // Stops the sampling
    void stop();
  
    // Returns true if timer is sampling
    bool operator!() const {return _sampling;}

    // Sets the callback 
    void onSample(void (*callback)(void* context, int distance), void* context = NULL);

    // Polls the timer
    void polling(unsigned long clockTime = millis());
    void _send();

  private:
    unsigned long _inactivity;
    byte _triggerPin;
    byte _echoPin;
    byte _noSamples;
    void (*_onSample)(void*, int);

    bool _sampling;
    byte _noMeasures;
    byte _noValidSamples;
    unsigned long _totalDuration;
    void* _context;
    Timer _timer;

    void _measure();
};

#endif
