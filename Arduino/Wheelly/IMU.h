#ifndef IMU_H
#define IMU_H

#include "MPU6050_6Axis_MotionApps20.h"

#define IMU_RUNNING_STATUS  0
#define IMU_FAILURE_STATUS  10

/*

*/
class IMU {
  public:
    IMU(MPU6050& mpu);
    void begin();
    IMU& calibrate(int steps = 6);
    IMU& enableDMP();
    void polling(unsigned long clockMillis = millis(), unsigned long clockMicros = micros());
    IMU& reset();
    IMU& kickAt(unsigned long time) {
      _watchDogTime = time;
      return *this;
    }
    IMU& kick() {
      _watchDogTime = micros() + _watchDogInterval;
      return *this;
    }

    IMU& onData(void (*callback)(void* context, IMU & imu)) {
      _onData = callback;
      return *this;
    }
    IMU& onWatchDog(void (*callback)(void* context, IMU & imu)) {
      _onWatchDog = callback;
      return *this;
    }
    IMU& context(void * ctx) {
      _context = ctx;
      return *this;
    }
    MPU6050& mpu() const {
      return _mpu;
    }

    const uint8_t status() const {
      return _devStatus;
    }
    const float* ypr() const {
      return _ypr;
    }
  private:
    MPU6050& _mpu;
    uint8_t _devStatus;
    uint16_t _packetSize;
    unsigned long _prevTime;
    unsigned long _lastTime;
    unsigned long _watchDogInterval;
    unsigned long _watchDogTime;
    void *_context;
    float _dt;
    float _ypr[3];

    void (*_onData)(void*, IMU & imu);
    void (*_onWatchDog)(void*, IMU & imu);
};

#endif
