#include "Timer.h"

/*

*/
Timer& Timer::start(unsigned long timeout) {
  _counter = 0;
  _next = timeout;
  _running = true;
  return *this;
}

/*

*/
Timer& Timer::start() {
  return start(millis() + _interval);
}

/*

*/
Timer& Timer::stop() {
  _running = false;
  return *this;
}

/*

*/
Timer& Timer::restart() {
  if (_running) {
    _next = millis() + _interval;
  }
  return *this;
}

/*

*/
Timer& Timer::polling(unsigned long clockTime) {
  if (_running) {
    if (clockTime >= _next) {
      unsigned long counter = _counter;
      _counter++;
      if (!_continuous) {
        stop();
      } else {
        _next += _interval;
      }
      if (_onNext != NULL) {
        _onNext(_context, counter);
      }
    }
  }
  return *this;
}

/*

*/
Timer& Timer::onNext(void (*callback)(void*, unsigned long), void* context) {
  _onNext = callback;
  _context = context;
  return *this;
}
