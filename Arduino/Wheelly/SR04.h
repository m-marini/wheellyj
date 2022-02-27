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
    SR04& begin();

    // Sets a single interval
    SR04& inactivity(unsigned long interval);

    // Sets a single interval
    SR04& noSamples(int noSamples);

    // Starts the sampling
    SR04& start(void *context = NULL);

    // Stops the sampling
    SR04& stop();
  
    // Returns true if timer is sampling
    bool operator!() const {return _sampling;}

    // Sets the callback 
    SR04& onSample(void (*callback)(void* context, int distance));

    // Polls the timer
    SR04& polling(unsigned long clockTime = millis());
    SR04& _send();

  private:
    unsigned long _inactivity;
    byte _triggerPin;
    byte _echoPin;
    int _noSamples;
    void (*_onSample)(void*, int);

    bool _sampling;
    int _noMeasures;
    int _noValidSamples;
    unsigned long _totalDuration;
    void* _context;
    Timer _timer;

    SR04& _measure();
};

#endif
