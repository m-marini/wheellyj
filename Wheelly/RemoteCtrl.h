#ifndef RemoteCtrl_h
#define RemoteCtrl_h

#include "Arduino.h"
#include "IRremote.h"

#define KEY_POWER         0xFFA25Dul
#define KEY_VOL_PLUS      0xFF629Dul
#define KEY_FUNC_STOP     0xFFE21Dul
#define KEY_FAST_BACK     0xFF22DDul
#define KEY_PAUSE         0xFF02FDul
#define KEY_FAST_FORWARD  0xFFC23Dul
#define KEY_DOWN          0xFFE01Ful
#define KEY_VOL_MINUS     0xFFA857ul
#define KEY_0             0xFF6897ul
#define KEY_UP            0xFF906Ful
#define KEY_EQ            0xFF9867ul
#define KEY_ST_REPT       0xFFB04Ful
#define KEY_1             0xFF30CFul
#define KEY_2             0xFF18E7ul
#define KEY_3             0xFF7A85ul
#define KEY_4             0xFF10EFul
#define KEY_5             0xFF38C7ul
#define KEY_6             0xFF5AA5ul
#define KEY_7             0xFF42BDul
#define KEY_8             0xFF4AB5ul
#define KEY_9             0xFF52ADul
#define KEY_REPEAT        0xFFFFFFFFul

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
