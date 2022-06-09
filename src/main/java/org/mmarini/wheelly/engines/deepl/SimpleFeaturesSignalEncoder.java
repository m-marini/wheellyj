/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.engines.deepl;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.WheellyStatus;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static java.lang.Math.*;

/**
 * Encoded wheelly status to neural network signals.
 * <p>
 * The status coded signals are in range 0, 1
 * <pre
 * (0-23)   24 head directions at 15 DEG intervals from -180 to 165
 * (24-32)  9 sensor directions at 20 DEG intervals from -90 to 90
 * (33-66)  30 obstacle distance at 0.1 m intervals from 0 m to 3m
 * (63-78)  16 contact signals (decoded from wheelly signal)
 * (79-82)  4 block signals
 * (83)     imu failure
 * (84-88)  5 voltage signals at 1V intervals from 8V to 12V
 * (89-2689) 51 x 51 cartesian cells for robot location at interval of 0.2 m from -5m to 5m
 * (2690-5290) 51 x 51 cartesian cells for obstacle locations at interval of 0.2 m from -5m to 5m
 * </pre>
 */
public class SimpleFeaturesSignalEncoder implements SignalEncoder {
    public static final int DIRECTION_NUM_SIGNALS = 24;
    public static final int SENSOR_NUM_SIGNALS = 9;
    public static final int DISTANCE_NUM_SIGNALS = 30;
    public static final int CONTACT_NUM_SIGNALS = 16;
    public static final int BLOCK_NUM_SIGNALS = 4;

    public static final int DIRECTION_OFFSET = 0;
    public static final int SENSOR_OFFSET = DIRECTION_OFFSET + DIRECTION_NUM_SIGNALS;
    public static final int DISTANCE_OFFSET = SENSOR_OFFSET + SENSOR_NUM_SIGNALS;
    public static final int CONTACTS_OFFSET = DISTANCE_OFFSET + DISTANCE_NUM_SIGNALS;
    public static final int BLOCK_OFFSET = CONTACTS_OFFSET + CONTACT_NUM_SIGNALS;
    public static final int IMU_FAILURE_OFFSET = BLOCK_OFFSET + BLOCK_NUM_SIGNALS;
    public static final int NUM_SIGNALS = IMU_FAILURE_OFFSET + 1;

    public static final int DIRECTION_SENSITIVITY = (360 / DIRECTION_NUM_SIGNALS);
    public static final int SENSOR_SENSITIVITY = (180 / SENSOR_NUM_SIGNALS);
    public static final double DISTANCE_SENSITIVITY = 0.1;

    private static final SimpleFeaturesSignalEncoder SINGLETON = new SimpleFeaturesSignalEncoder();

    public static SimpleFeaturesSignalEncoder create() {
        return SINGLETON;
    }

    /**
     * Returns the encoded direction (0-23)
     *
     * @param direction the direction DEG
     */
    static int encodeDirection(int direction) {
        return max((direction + 187) / DIRECTION_SENSITIVITY, 0) % DIRECTION_NUM_SIGNALS;
    }

    /**
     * Returns the encoded distance (0-29)
     *
     * @param distance the distance in m
     */
    static int encodeDistance(double distance) {
        return min(max((int) floor(distance / DISTANCE_SENSITIVITY), 0), DISTANCE_NUM_SIGNALS - 1);
    }

    /**
     * Returns the encoded sensor direction (0-8)
     *
     * @param direction the sensor direction DEG
     */
    static int encodeSensor(int direction) {
        return min(max((direction + 90) / SENSOR_SENSITIVITY, 0), SENSOR_NUM_SIGNALS - 1);
    }

    @Override
    public INDArray encode(Timed<MapStatus> status) {
        INDArray result = Nd4j.zeros(1, NUM_SIGNALS);
        WheellyStatus wheelly = status.value().getWheelly();

        int direction = encodeDirection(wheelly.getRobotDeg());
        int sensor = encodeSensor(wheelly.getSensorRelativeDeg());
        int distance = encodeDistance(wheelly.getSampleDistance());
        int contacts = wheelly.getContactSensors();
        int block = (wheelly.getCannotMoveForward() ? 1 : 0)
                + (wheelly.getCannotMoveBackward() ? 2 : 0);
        boolean imuFailure = wheelly.isImuFailure();

        result.putScalar(0, direction + DIRECTION_OFFSET, 1d);
        result.putScalar(0, sensor + SENSOR_OFFSET, 1d);
        result.putScalar(0, distance + DISTANCE_OFFSET, 1d);
        result.putScalar(0, contacts + CONTACTS_OFFSET, 1d);
        result.putScalar(0, block + BLOCK_OFFSET, 1d);
        if (imuFailure) {
            result.putScalar(0, IMU_FAILURE_OFFSET, 1d);
        }
        return result;
    }

    @Override
    public int getNumSignals() {
        return NUM_SIGNALS;
    }
}
