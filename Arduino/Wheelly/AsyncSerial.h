#ifndef AsyncCommand_h
#define AsyncCommand_h

#include "Arduino.h"
#include "Timing.h"

#define BUFFER_SIZE 63

class AsyncSerial {
  public:
    AsyncSerial();
    AsyncSerial& polling();
    AsyncSerial& serialEvent();
    AsyncSerial& onData(void (*callback)(void *context, const char *data, const Timing& timing));
    AsyncSerial& context(void* context);
    
  private:
    void *_context;
    void (*_onData)(void *, const char *, const Timing& timing);
    size_t  _noChars;
    bool _stringComplete;
    char _inputString[BUFFER_SIZE];
    Timing _timing;
};

#endif
