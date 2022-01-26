#ifndef AsyncTimer_h
#define AsyncTimer_h

#include "Arduino.h"

#define MAX_INTERVALS 4

/*
 * ASynchronous timer
 */
class AsyncTimer {
  public:

    // Sets the intervals sequence
    AsyncTimer& intervals(int noIntervals, unsigned long *intervals);

    // Sets a single interval
    AsyncTimer& interval(unsigned long interval);

    // Sets true if continuos events
    AsyncTimer& continuous(boolean cont);

    // Starts the timer
    AsyncTimer& start();

    // Stops the timer
    AsyncTimer& stop();
  
    // Returns true if timer is not expired (is timing)
    bool operator!() const {return _running;}

    // Sets the callback 
    AsyncTimer& onNext(void (*callback)(int, long));

    // Polls the timer
    AsyncTimer& polling();

  private:
    bool _continuous;
    int _noIntervals;
    unsigned long _intervals[MAX_INTERVALS];
    void (*_onNext)(int, long);

    unsigned long _next;
    int _interval;
    long _cycles;
    boolean _running;
};

#endif
