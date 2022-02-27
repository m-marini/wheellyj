#include "AsyncServo.h"

#define DEFAULT_INTERVAL (180 * 1000 / 400)

AsyncServo::AsyncServo(){
  _timer.interval(DEFAULT_INTERVAL)
    .onNext(_handleTimeout);
}

AsyncServo& AsyncServo::attach(int pin){
  _servo.attach(pin);
  return *this;      
}

AsyncServo& AsyncServo::onReached(void (*callback)(void*, int)){
  _onReached = callback;
  return *this;
}

AsyncServo& AsyncServo::offset(const int value) {
  _offset = value;
  return *this;
}

AsyncServo& AsyncServo::angle(void* context, int value){
  _timer.stop();
  _angle = value;
  _context = context;
  _servo.write(value + _offset);
  _timer.start(this);
  return *this;
}

void AsyncServo::_handleTimeout(void * context, unsigned long) {
  AsyncServo* servo = (AsyncServo*) context;
  if (servo->_onReached != NULL) {
    servo->_onReached(servo->_context, servo->_angle);
  }
}

AsyncServo& AsyncServo::polling(unsigned long clockTime) {
  _timer.polling(clockTime);
  return *this;
}
