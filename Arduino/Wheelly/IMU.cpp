#include "IMU.h"

#define G_VALUE             9.80665
#define ACC_SCALE           (4.0 * G_VALUE / 32768.0)
#define GYRO_SCALE           (1.0 / 32768.0)
#define WATCH_DOG_INTERVAL  100000ul

IMU::IMU(MPU6050& mpu) : _mpu {mpu} {
  _accScale = ACC_SCALE;
  _gyroScale = GYRO_SCALE;
  _watchDogInterval = WATCH_DOG_INTERVAL;
  _devStatus = IMU_FAILURE_STATUS;
}

void IMU::begin() {
  _devStatus = IMU_FAILURE_STATUS;
  _mpu.initialize();

  int rc;
  if (!(rc = _mpu.testConnection())) {
    Serial.println(F("!! Connection IMU failed."));
    return;
  }
  _devStatus = _mpu.dmpInitialize();
  if (_devStatus) {
    Serial.print(F("!! DMP initialize failed: "));
    Serial.print(_devStatus);
    Serial.println();
  }
}

/*

*/
IMU& IMU::enableDMP() {
  if (_devStatus == 0) {
    // turn on the DMP, now that it's ready
    _mpu.setDMPEnabled(true);

    // get expected DMP packet size for later comparison
    _packetSize = _mpu.dmpGetFIFOPacketSize();
  }
  return *this;
}

/*

*/
IMU& IMU::reset() {
  if (_devStatus == 0) {
    _mpu.resetFIFO();
    _prevTime = micros();
    kickAt(_prevTime + _watchDogInterval);
    return *this;
  }
}

/*

*/
void IMU::polling(unsigned long clockMillis, unsigned long clockMicros) {
  if (_devStatus == 0) {
    if (_mpu.getFIFOCount() >= _packetSize) {
      // read a packet from FIFO
      _mpu.getFIFOBytes(_fifoBuffer, _packetSize);
      _mpu.resetFIFO();
      kickAt(clockMicros + _watchDogInterval);

      _dt = (float)(clockMicros - _prevTime) * 1e-6;
      if (_dt > 0 && _dt < 1.0) {
        _lastTime = clockMillis;
        _prevTime = clockMicros;
        VectorInt16 accel;      // [x, y, z]            accel sensor measurements
        VectorInt16 gyro;       // [x, y, z]            gyro sensor measurements
        VectorInt16 linAcc;     // [x, y, z]            gravity-free accel sensor measurements
        VectorInt16 worldAcc;   // [x, y, z]            world-frame accel sensor measurements
        VectorFloat gravity;    // [x, y, z]            gravity vector
        _mpu.dmpGetQuaternion(&_q, _fifoBuffer);
        _mpu.dmpGetAccel(&accel, _fifoBuffer);
        _mpu.dmpGetGyro(&gyro, _fifoBuffer);
        _mpu.dmpGetGravity(&gravity, &_q);
        _mpu.dmpGetLinearAccel(&linAcc, &accel, &gravity);
        _mpu.dmpGetLinearAccelInWorld(&worldAcc, &linAcc, &_q);
        _mpu.dmpGetYawPitchRoll(_ypr, &_q, &gravity);

        _acc[0] = accel.x * _accScale;
        _acc[1] = accel.y * _accScale;
        _acc[2] = accel.z * _accScale;

        _linAcc[0] = linAcc.x * _accScale;
        _linAcc[1] = linAcc.y * _accScale;
        _linAcc[2] = linAcc.z * _accScale;

        _worldAcc[0] = worldAcc.x * _accScale;
        _worldAcc[1] = worldAcc.y * _accScale;
        _worldAcc[2] = worldAcc.z * _accScale;

        _gyro[0] = gyro.x * _gyroScale;
        _gyro[1] = gyro.y * _gyroScale;
        _gyro[2] = gyro.z * _gyroScale;

        _vx += _linAcc[0] * _dt;

        if (_onData != NULL) {
          _onData(_context, *this);
        }
      }
    } else if (clockMicros >= _watchDogTime && _onWatchDog != NULL) {
      kickAt(clockMicros + _watchDogInterval);
      _onWatchDog(_context, *this);
    }
  }
}

/*

*/
IMU& IMU::calibrate(int steps) {
  if (_devStatus == 0) {
    _mpu.setXAccelOffset(0);
    _mpu.setYAccelOffset(0);
    _mpu.setZAccelOffset(0);

    _mpu.setXGyroOffset(0);
    _mpu.setYGyroOffset(0);
    _mpu.setZGyroOffset(0);

    // Calibration Time: generate offsets and calibrate our MPU6050
    _mpu.CalibrateAccel(steps);
    _mpu.CalibrateGyro(steps);
#ifdef DEBUG
    _mpu.PrintActiveOffsets();
#endif
  }
  _vx = 0;
  return *this;
}
