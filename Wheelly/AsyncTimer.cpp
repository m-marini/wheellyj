
#include "AsyncTimer.h"

AsyncTimer& AsyncTimer::continuous(bool cont) {
  _continuous = cont;
  return *this;
}

AsyncTimer& AsyncTimer::interval(unsigned long interval) {
  return intervals(1, &interval);
}

AsyncTimer& AsyncTimer::intervals(int noIntervals, unsigned long *intervals) {
  if (noIntervals >= 1) {
    if (noIntervals > MAX_INTERVALS) {
      noIntervals = MAX_INTERVALS;
    }
    for (int i = 0; i < noIntervals; i++) {
      _intervals[i] = intervals[i];
    }
    _noIntervals = noIntervals;
  }
  return *this;
}

AsyncTimer& AsyncTimer::start() {
  _next = millis();
  _interval = 0;
  _cycles = 0;
  _running = true;
  return *this;
}

AsyncTimer& AsyncTimer::stop() {
  _running = false;
  return *this;
}

AsyncTimer& AsyncTimer::polling() {
  if (_running) {
    unsigned long clockTime = millis();
    if (clockTime >= _next) {
      _next += _intervals[_interval];
      if (_onNext != NULL) {
        _onNext(_interval, _cycles);
      }
      _interval++;
      if (_interval >= _noIntervals) {
        _interval = 0;
        _cycles++;
        if (!_continuous) {
          _running = false;
        }
      }
    }
  }
  return *this;
}

AsyncTimer& AsyncTimer::onNext(void (*callback)(int, long)) {
  _onNext = callback;
  return *this;
}
