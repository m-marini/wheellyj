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
    IMU& vx(float vx) {
      _vx = vx;
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
    const unsigned long lastTime() const {
      return _lastTime;
    }
    const float* acc() const {
      return _acc;
    }
    const float* linAcc() const {
      return _linAcc;
    }
    const float* worldAcc() const {
      return _worldAcc;
    }
    const float* ypr() const {
      return _ypr;
    }
    const float* gyro() const {
      return _gyro;
    }
    const Quaternion& q() const {
      return _q;
    }
    const float vx() const {
      return _vx;
    }
  private:
    MPU6050& _mpu;
    uint8_t _devStatus;
    float _accScale;
    float _gyroScale;
    uint8_t _fifoBuffer[64]; // FIFO storage buffer
    uint16_t _packetSize;
    unsigned long _prevTime;
    unsigned long _lastTime;
    unsigned long _watchDogInterval;
    unsigned long _watchDogTime;
    void *_context;
    float _dt;
    Quaternion _q;           // [w, x, y, z]         quaternion container
    float _acc[3];
    float _worldAcc[3];
    float _gyro[3];
    float _linAcc[3];
    float _ypr[3];
    float _vx;

    void (*_onData)(void*, IMU & imu);
    void (*_onWatchDog)(void*, IMU & imu);
};

#endif
