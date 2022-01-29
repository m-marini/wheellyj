#include "Multiplexer.h"

Multiplexer::Multiplexer(int latchPin, int clockPin, int dataPin) {
  _latchPin = latchPin;
  _clockPin = clockPin;
  _dataPin = dataPin;
}

Multiplexer& Multiplexer::begin() {
  pinMode(_latchPin, OUTPUT);
  pinMode(_dataPin, OUTPUT);  
  pinMode(_clockPin, OUTPUT);
  _last = 0xff;
  return flush();
}

Multiplexer& Multiplexer::reset(int index) {
  _values = bitClear(_values, index);
  return *this;
}

Multiplexer& Multiplexer::set(int index) {
  _values = bitSet(_values, index);
  return *this;
}

Multiplexer& Multiplexer::values(int data) {
  _values = data;
  return *this;
}

Multiplexer& Multiplexer::set(int index, bool value) {
  _values = bitWrite(_values, index, value);
  return *this;
}

Multiplexer& Multiplexer::reset() {
  _values = 0;
  return *this;
}

Multiplexer& Multiplexer::flush() {
  if (_values != _last) {
    digitalWrite(_latchPin, LOW);
    shiftOut(_dataPin, _clockPin, LSBFIRST, _values);
    digitalWrite(_latchPin, HIGH);
    _last = _values;
  }
  return *this;
}
