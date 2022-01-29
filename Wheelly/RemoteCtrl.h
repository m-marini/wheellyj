#ifndef RemoteCtrl_h
#define RemoteCtrl_h

#include "Arduino.h"
#include "IRremote.h"

#define KEY_POWER         0xFFA25D
#define KEY_VOL_PLUS      0xFF629D
#define KEY_FUNC_STOP     0xFFE21D
#define KEY_FAST_BACK     0xFF22DD
#define KEY_PAUSE         0xFF02FD
#define KEY_FAST_FORWARD  0xFFC23D
#define KEY_DOWN          0xFFE01F
#define KEY_VOL_MINUS     0xFFA857
#define KEY_0             0xFF6897
#define KEY_UP            0xFF906F
#define KEY_EQ            0xFF9867
#define KEY_ST_REPT       0xFFB04F
#define KEY_1             0xFF30CF
#define KEY_2             0xFF18E7
#define KEY_3             0xFF7A85
#define KEY_4             0xFF10EF
#define KEY_5             0xFF38C7
#define KEY_6             0xFF5AA5
#define KEY_7             0xFF42BD
#define KEY_8             0xFF4AB5
#define KEY_9             0xFF52AD
#define KEY_REPEAT        0xFFFFFFFF

/*
 * Multiplexer
 */
class RemoteCtrl {
  public:
    RemoteCtrl(int sensorPin);

    RemoteCtrl& begin();
    RemoteCtrl& polling();

    RemoteCtrl& onData(void (*callback)(decode_results& results));

  private:
    IRrecv _receiver;
    void (*_onData)(decode_results& results);
};

#endif
