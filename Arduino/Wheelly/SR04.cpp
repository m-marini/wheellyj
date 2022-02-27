#include "SR04.h"

#define INACTIVITY 50ul

void _handleTimeout(void *context, unsigned long){
  ((SR04*)context)->_send();
}

SR04::SR04(byte triggerPin, byte echoPin)
  : _triggerPin{triggerPin}, _echoPin{echoPin}  {
  _inactivity = INACTIVITY;
  _noSamples = 1;
  _sampling = false;
  _timer.onNext(&_handleTimeout);
}

SR04& SR04::begin() {
  pinMode(_echoPin, INPUT);
  pinMode(_triggerPin, OUTPUT);
  return *this;
}

SR04& SR04::inactivity(unsigned long inactivity) {
  _inactivity = inactivity;
  return *this;
}

SR04& SR04::noSamples(int noSamples) {
  _noSamples = noSamples;
  return *this;
}

SR04& SR04::onSample(void (*callback)(void* context, int distance)) {
  _onSample = callback;
  return *this;
}

SR04& SR04::start(void *context) {
  _context = context;
  _sampling = true;
  _timer.stop().interval(_inactivity);
  _noMeasures = 0;
  _noValidSamples = 0;
  _totalDuration = 0;
  _measure();
  _timer.start(this);
  return *this;
}

SR04& SR04::stop() {
  _timer.stop();
  _sampling = false;
  return *this;
}

SR04& SR04::_measure() {
  digitalWrite(_triggerPin, LOW);
  delayMicroseconds(2);
  digitalWrite(_triggerPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(_triggerPin, LOW);
  delayMicroseconds(2);
  unsigned long to = _inactivity * 1000;
  long duration = pulseIn(_echoPin, HIGH, to);
  _noMeasures++;
  if (duration > 0 || duration < _inactivity * 1000) {
    _totalDuration += duration;
    _noValidSamples++;
  }
  return *this;
}

SR04& SR04::polling(unsigned long clockTime) {
  _timer.polling(clockTime);
  return *this;
}

SR04& SR04::_send() {
  if (_noMeasures >= _noSamples) {
    _sampling = false;
    long distance = 0;
    if (_noValidSamples > 0) {
      distance = (_totalDuration * 100) / _noValidSamples / 5882;
    }
    if (_onSample != NULL) {
      _onSample(_context, distance);
    }
  } else {
    _measure();
    _timer.start(this);
  }
}
