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

    Multiplexer& values(byte data) {_values = data; return *this;}
    Multiplexer& reset(){_values = 0; return *this;}

    Multiplexer& resetAll();

  private:
    int _latchPin;
    int _clockPin;
    int _dataPin;
    byte _values;
    byte _last;
};

#endif
