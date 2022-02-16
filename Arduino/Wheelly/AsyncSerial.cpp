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
    // get the new byte:
    char inChar = (char) Serial.read();
    
    // get the time of first char
    if (_noChars == 0) {
      _timing.millis = millis();
      _timing.micros = micros();
    }
    
    // add it to the inputString:
    _inputString[_noChars++] = inChar;

    // if the incoming character is a newline, set a flag of line completed
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
