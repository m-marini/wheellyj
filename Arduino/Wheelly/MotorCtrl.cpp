#include "MotorCtrl.h"

//#define DEBUG
#include "debug.h"

#define MAX_VALUE 255

const float defaultCorrection[] PROGMEM = { -1,  -0.5, 0, 0.5, 1};

MotorCtrl::MotorCtrl(byte forwPin, byte backPin) {
  _forwPin = forwPin;
  _backPin = backPin;
  _x = defaultCorrection;
  _y = defaultCorrection;
}

void MotorCtrl::begin() {
  pinMode(_forwPin, OUTPUT);
  pinMode(_backPin, OUTPUT);
}

void MotorCtrl::setCorrection(float *x, float *y) {
  _x = x;
  _y = y;
}

/*
   Set speed
*/
void MotorCtrl::speed(float value) {
  float pwd = func(value);
  if (value == 0) {
    analogWrite(_forwPin, 0);
    analogWrite(_backPin, 0);
  } else if (value > 0) {
    int signal = round(pwd * MAX_VALUE);
    analogWrite(_forwPin, signal);
    analogWrite(_backPin, 0);
  } else {
    int signal = -round(pwd * MAX_VALUE);
    analogWrite(_forwPin, 0);
    analogWrite(_backPin, signal);
  }
}

float MotorCtrl::func(float x) {
  int i = NO_POINTS - 2;
  float xx[NO_POINTS];
  float yy[NO_POINTS];
  memcpy_P(xx, _x, sizeof(float[NO_POINTS]));
  memcpy_P(yy, _y, sizeof(float[NO_POINTS]));
  for (int j = 1; j < NO_POINTS - 2; j++) {
    if (x < xx[j]) {
      i = j - 1;
      break;
    }
  }
  float result = (x - xx[i]) * (yy[i + 1] - yy[i]) / (xx[i + 1] - xx[i]) + yy[i];
  DEBUG_PRINT(F("// x: "));
  DEBUG_PRINT(x);
  DEBUG_PRINT(F(", f(x): "));
  DEBUG_PRINT(result);
  DEBUG_PRINTLN();
  
  return min(max(-1, result), 1);
}
