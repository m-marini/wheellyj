#ifndef Multiplexer_h
#define Multiplexer_h

#include "Arduino.h"

/*
 * Multiplexer
 */
class Multiplexer {
  public:
    Multiplexer(int latchPin, int clockPin, int dataPin);

    Multiplexer& begin();

    Multiplexer& flush();

    Multiplexer& set(int index);
    Multiplexer& set(int index, bool value);
    Multiplexer& reset(int index);

    Multiplexer& values(int data);
    Multiplexer& reset();

    Multiplexer& resetAll();

    int values() const {return _values;}
  private:
    int _latchPin;
    int _clockPin;
    int _dataPin;
    int _values;
    int _last;
};

#endif
