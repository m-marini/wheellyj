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
    void polling(unsigned long clock = micros());
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
    const uint8_t* fifoBuffer() const {
      return _fifoBuffer;
    }
    const float dt() const {
      return _dt;
    }
    const float* accel() const {
      return _accel;
    }
    const float* ypr() const {
      return _ypr;
    }
    const Quaternion& q() const {
      return _q;
    }
  private:
    MPU6050& _mpu;
    uint8_t _devStatus;
    float _accScale;
    uint8_t _fifoBuffer[64]; // FIFO storage buffer
    uint16_t _packetSize;
    unsigned long _prevTime;
    unsigned long _watchDogInterval;
    unsigned long _watchDogTime;
    void *_context;
    float _dt;
    Quaternion _q;           // [w, x, y, z]         quaternion container
    float _accel[3];
    float _ypr[3];

    void (*_onData)(void*, IMU & imu);
    void (*_onWatchDog)(void*, IMU & imu);
};

#endif
