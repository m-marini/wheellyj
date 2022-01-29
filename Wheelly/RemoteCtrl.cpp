#include "RemoteCtrl.h"

RemoteCtrl::RemoteCtrl(int sensorPin) : _receiver(sensorPin) {
}

RemoteCtrl& RemoteCtrl::begin() {
  _receiver.enableIRIn();
  return *this;
}

RemoteCtrl& RemoteCtrl::onData(void (*callback)(decode_results& results)){
  _onData = callback;
  return *this;
}

RemoteCtrl& RemoteCtrl::polling() {
  decode_results results;      // create instance of 'decode_results'
  if (_receiver.decode(&results)) {
    //translateIR(); 
    if (_onData != NULL) {
      _onData(results);
    }
    _receiver.resume(); // receive the next value
  }  
  return *this;
}
