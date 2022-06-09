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
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.Obstacle;
import org.mmarini.wheelly.model.WheellyStatus;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
public class FullFeaturesSignalEncoder implements SignalEncoder {

    public static final int DIRECTION_OFFSET = 0;
    public static final int SENSOR_OFFSET = 24;
    public static final int DISTANCE_OFFSET = 33;
    public static final int CONTACTS_OFFSET = 63;
    public static final int BLOCK_OFFSET = 79;
    public static final int IMU_FAILURE_OFFSET = 80;
    public static final int LOCATION_OFFSET = 89;
    public static final int VOLTAGE_OFFSET = 84;
    public static final int NUM_SPACE_LOCATIONS = 51 * 51;
    public static final int MAP_OFFSET = 90 + NUM_SPACE_LOCATIONS;
    public static final int NUM_SIGNALS = MAP_OFFSET + NUM_SPACE_LOCATIONS;
    private static final FullFeaturesSignalEncoder SINGLETON = new FullFeaturesSignalEncoder();

    public static FullFeaturesSignalEncoder create() {
        return SINGLETON;
    }

    /**
     * Returns the encoded direction (0-23)
     *
     * @param direction the direction DEG
     */
    static int encodeDirection(int direction) {
        return max((direction + 187) / 15, 0) % 24;
    }

    /**
     * Returns the encoded distance (0-29)
     *
     * @param distance the distance in m
     */
    static int encodeDistance(double distance) {
        return min(max((int) floor(distance / 0.1), 0), 29);
    }

    /**
     * Returns the encoded linear coordinate (0-50)
     *
     * @param x the liner coordinate
     */
    static int encodeLinear(double x) {
        return min(max((int) round((x + 5) / 0.2), 0), 50);
    }

    /**
     * Returns the encoded location (0-2600)
     *
     * @param location the location
     */
    static int encodeLocation(Point2D location) {
        return encodeLinear(location.getX()) + 51 * encodeLinear(location.getY());
    }

    /**
     * Encodes the map based of robot asset
     *
     * @param wheelly the robot status
     * @param map     the obstacle map
     */
    private static IntStream encodeMap(WheellyStatus wheelly, GridScannerMap map) {
        return encodePoints(
                transform(map.getObstacles().stream().map(Obstacle::getLocation),
                        wheelly.getRobotLocation(),
                        wheelly.getSensorRad()));
    }

    /**
     * Returns the encoded location of a stream of points
     *
     * @param points the points
     */
    public static IntStream encodePoints(Stream<Point2D> points) {
        return points.mapToInt(FullFeaturesSignalEncoder::encodeLocation);
    }

    /**
     * Returns the encoded sensor direction (0-8)
     *
     * @param direction the sensor direction DEG
     */
    static int encodeSensor(int direction) {
        return min(max((direction + 90) / 20, 0), 8);
    }

    /**
     * Returns the encoded voltage (0-4)
     *
     * @param voltage the power voltage
     */
    static int encodeVoltage(double voltage) {
        return min(max((int) floor((voltage - 8)), 0), 4);
    }

    /**
     * Returns the transformed points by off-setting and rotate
     *
     * @param points the stream of points
     * @param offset the offset
     * @param theta  the rotation angle RAD
     */
    public static Stream<Point2D> transform(Stream<Point2D> points, Point2D offset, double theta) {
        AffineTransform tr = AffineTransform.getRotateInstance(-theta);
        tr.translate(-offset.getX(), -offset.getX());
        return points.map(pt -> tr.transform(pt, null));
    }

    @Override
    public INDArray encode(Timed<MapStatus> status) {
        INDArray result = Nd4j.zeros(1, NUM_SIGNALS);
        WheellyStatus wheelly = status.value().getWheelly();
        GridScannerMap map = status.value().getMap();

        int direction = encodeDirection(wheelly.getRobotDeg());
        int sensor = encodeSensor(wheelly.getSensorRelativeDeg());
        int distance = encodeDistance(wheelly.getSampleDistance());
        int contacts = wheelly.getContactSensors();
        int block = (wheelly.getCannotMoveForward() ? 1 : 0)
                + (wheelly.getCannotMoveBackward() ? 2 : 0);
        boolean imuFailure = wheelly.isImuFailure();
        int voltage = encodeVoltage(wheelly.getVoltage());
        int location = encodeLocation(wheelly.getRobotLocation());

        result.putScalar(0, direction + DIRECTION_OFFSET, 1d);
        result.putScalar(0, sensor + SENSOR_OFFSET, 1d);
        result.putScalar(0, distance + DISTANCE_OFFSET, 1d);
        result.putScalar(0, contacts + CONTACTS_OFFSET, 1d);
        result.putScalar(0, block + BLOCK_OFFSET, 1d);
        if (imuFailure) {
            result.putScalar(0, IMU_FAILURE_OFFSET, 1d);
        }
        result.putScalar(0, voltage + VOLTAGE_OFFSET, 1);
        result.putScalar(0, location + LOCATION_OFFSET, 1);

        encodeMap(wheelly, map).forEach(
                idx -> result.putScalar(0, idx + MAP_OFFSET, 1)
        );

        return result;
    }

    @Override
    public int getNumSignals() {
        return NUM_SIGNALS;
    }
}
