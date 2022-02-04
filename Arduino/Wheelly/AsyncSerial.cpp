#include <Streaming.h>
#include "AsyncSerial.h"

/*
 * 
 */
AsyncSerial::AsyncSerial() {
}

/*
 * 
 */
AsyncSerial& AsyncSerial::serialEvent() {
  while (Serial.available() && _noChars < BUFFER_SIZE) {
    if (_noChars == 0) {
      _timing.millis = millis();
      _timing.micros = micros();
    }
    // get the new byte:
    char inChar = (char) Serial.read();
    // add it to the inputString:
    // if the incoming character is a newline, set a flag so the main loop can
    // do something about it:
    _inputString[_noChars++] = inChar;
    if (inChar == '\n' || _noChars >= BUFFER_SIZE) {
      _inputString[_noChars] = '\0';
      _stringComplete = true;
      break;
    }
  }
  return *this;
}

/*
 * 
 */
AsyncSerial& AsyncSerial::polling() {
  if (_stringComplete) {
    if (_onData != NULL) {
      _onData(_context, _inputString, _timing);
    }
    _noChars = 0;
    _stringComplete = false;
  }
  return *this;
}

/*
 * 
 */
AsyncSerial& AsyncSerial::onData(void (*callback)(void *, const char *, const Timing&)) {
  _onData = callback;
  return *this;
}
