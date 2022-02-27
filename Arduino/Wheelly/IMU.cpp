#include "IMU.h"

#define G_VALUE             9.80665
#define ACC_SCALE           (4.0 * G_VALUE / 32768.0)
#define WATCH_DOG_INTERVAL  100000ul

IMU::IMU(MPU6050& mpu) : _mpu {mpu} {
  _accScale = ACC_SCALE;
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
void IMU::polling(unsigned long clockTime) {
  if (_devStatus == 0) {
    if (_mpu.getFIFOCount() >= _packetSize) {
      // read a packet from FIFO
      _mpu.getFIFOBytes(_fifoBuffer, _packetSize);
      _mpu.resetFIFO();
      kickAt(clockTime + _watchDogInterval);

      _dt = (float)(clockTime - _prevTime) * 1e-6;
      if (_dt > 0 && _dt < 1.0) {
        _prevTime = clockTime;
        VectorInt16 aa;         // [x, y, z]            accel sensor measurements
        VectorInt16 aaReal;     // [x, y, z]            gravity-free accel sensor measurements
        VectorInt16 aaWorld;    // [x, y, z]            world-frame accel sensor measurements
        VectorFloat gravity;    // [x, y, z]            gravity vector
        _mpu.dmpGetQuaternion(&_q, _fifoBuffer);
        _mpu.dmpGetAccel(&aa, _fifoBuffer);
        _mpu.dmpGetGravity(&gravity, &_q);
        _mpu.dmpGetLinearAccel(&aaReal, &aa, &gravity);
        _mpu.dmpGetLinearAccelInWorld(&aaWorld, &aaReal, &_q);
        _mpu.dmpGetYawPitchRoll(_ypr, &_q, &gravity);

        _accel[0] = aaWorld.x * _accScale;
        _accel[1] = aaWorld.y * _accScale;
        _accel[2] = aaWorld.z * _accScale;

        if (_onData != NULL) {
          _onData(_context, *this);
        }
      }
    } else if (clockTime >= _watchDogTime && _onWatchDog != NULL) {
      kickAt(clockTime + _watchDogInterval);
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
    _mpu.PrintActiveOffsets();
  }
  return *this;
}
