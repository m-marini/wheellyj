/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import io.reactivex.rxjava3.schedulers.Timed;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.model.Utils.*;

class ScannerMapPropertiesTest {

    public static final double MAX_DISTANCE = 3d;
    public static final double MIN_DISTANCE = 0.1;
    public static final int MAX_DEG_DIRECTION = 359;
    private static final double LOCATION_RANGE = 3;

    static Stream<Arguments> argsCreateNoSampleProps() {
        return createStream(1234,
                uniform(-LOCATION_RANGE, LOCATION_RANGE),
                uniform(-LOCATION_RANGE, LOCATION_RANGE),
                uniform(0, MAX_DEG_DIRECTION),
                exponential(MIN_DISTANCE, MAX_DISTANCE),
                uniform(-180, 179)
        );
    }

    static Stream<Arguments> argsCreateSampleProps() {
        return createStream(1234,
                uniform(-LOCATION_RANGE, LOCATION_RANGE),
                uniform(-LOCATION_RANGE, LOCATION_RANGE),
                uniform(0, MAX_DEG_DIRECTION),
                exponential(MIN_DISTANCE, MAX_DISTANCE),
                exponential(MIN_DISTANCE, MAX_DISTANCE),
                uniform(-180, 179)
        );
    }

    static Stream<Arguments> argsCreateSampleProps1() {
        return createStream(1234,
                uniform(-LOCATION_RANGE, LOCATION_RANGE),
                uniform(-LOCATION_RANGE, LOCATION_RANGE),
                uniform(0, MAX_DEG_DIRECTION),
                uniform(0, MAX_DEG_DIRECTION),
                exponential(MIN_DISTANCE, MAX_DISTANCE),
                exponential(MIN_DISTANCE, MAX_DISTANCE),
                uniform(-180, 179)
        );
    }

    @ParameterizedTest
    @MethodSource("argsCreateNoSampleProps")
    void testCreateNoSampleProperties(double robotX, double robotY,
                                      int sensorDirection,
                                      double obstacleDistance, int sensorRelativeDirection) {
        /*
        Given a robot asset
        with a sensor direction
        and an obstacle distance
        and an obstacle direction relative the sensor
         */
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        long sampleTimestamp = System.currentTimeMillis();

        double obstacleDirection = normalizeAngle(toRadians(sensorDirection + sensorRelativeDirection));
        double sensorRelativeRadDirection = normalizeAngle(toRadians(sensorRelativeDirection));
        double x = robotX + obstacleDistance * cos(obstacleDirection);
        double y = robotY + obstacleDistance * sin(obstacleDirection);
        Obstacle obstacle = Obstacle.create(new Point2D.Double(x, y), sampleTimestamp, 1);

        /*
        When create the obstacle, sample properties
         */
        ObstacleSampleProperties result = GridScannerMap.createSampleProperties(obstacle, null, robotLocation, toRadians(sensorDirection));

        /*
        Then should return the expected properties
         */
        assertNotNull(result);
        assertEquals(result.obstacle, obstacle);
        assertThat(result.sampleObstacleDistance, equalTo(Double.MAX_VALUE));
        assertThat(result.robotObstacleDistance, closeTo(obstacleDistance, 5e-4));
        assertThat(result.obstacleSensorRad, closeTo(sensorRelativeRadDirection, toRadians(0.5)));
    }

    @ParameterizedTest
    @MethodSource("argsCreateSampleProps")
    void testCreateSampleProperties(double robotX, double robotY,
                                    int sensorDirection, double sampleDistance,
                                    double obstacleDistance, int sensorRelativeDirection) {
        /*
        Given a robot asset
        with a sensor direction
        and an sample distance
        and an obstacle distance
        and an obstacle direction relative the sensor
         */
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        long sampleTimestamp = System.currentTimeMillis();
        Point2D sampleLocation = new Point2D.Double(
                robotX + sampleDistance * cos(toRadians(sensorDirection)),
                robotY + sampleDistance * sin(toRadians(sensorDirection))
        );

        double obstacleDirection = normalizeAngle(toRadians(sensorDirection + sensorRelativeDirection));
        double sensorRelativeRadDirection = normalizeAngle(toRadians(sensorRelativeDirection));
        Point2D.Double obstacleLocation = new Point2D.Double(
                robotX + obstacleDistance * cos(obstacleDirection),
                robotY + obstacleDistance * sin(obstacleDirection));
        Obstacle obstacle = Obstacle.create(obstacleLocation, sampleTimestamp, 1);

        /*
        When create the obstacle, sample properties
         */
        ObstacleSampleProperties result = GridScannerMap.createSampleProperties(obstacle, sampleLocation, robotLocation, toRadians(sensorDirection));

        /*
        Then should return the expected properties
         */
        assertNotNull(result);
        assertEquals(result.obstacle, obstacle);
        assertThat(result.sampleObstacleDistance, closeTo(obstacleLocation.distance(sampleLocation), 5e-4));
        assertThat(result.robotObstacleDistance, closeTo(obstacleDistance, 5e-4));
        assertThat(result.obstacleSensorRad, closeTo(sensorRelativeRadDirection, toRadians(0.5)));
    }

    @ParameterizedTest
    @MethodSource("argsCreateSampleProps1")
    void testGetSampleProperties(double robotX, double robotY, int robotDegDirection,
                                 int sensorRelativeDegDirection, double sampleDistance,
                                 double obstacleDistance, int obstacleRelativeDegDirection) {
        /*
        Given a robot asset
        with a sensor direction
        and a sensor direction relative to robot
        and a sample distance
        and an obstacle distance
        and an obstacle direction relative the sensor
         */
        Point2D robotLocation = new Point2D.Double(robotX, robotY);
        long sampleTimestamp = System.currentTimeMillis();
        RobotAsset robotAsset = RobotAsset.create(robotLocation, robotDegDirection);

        double sensorRadDirection = toNormalRadians(robotDegDirection + sensorRelativeDegDirection);
        Point2D sampleLocation = new Point2D.Double(
                robotX + sampleDistance * cos(sensorRadDirection),
                robotY + sampleDistance * sin(sensorRadDirection)
        );
        Timed<ProxySample> sample = new Timed<>(ProxySample.create(sensorRelativeDegDirection, (float) sampleDistance, robotAsset), sampleTimestamp, TimeUnit.MILLISECONDS);

        double obstacleRadDirection = toNormalRadians(robotDegDirection + sensorRelativeDegDirection + obstacleRelativeDegDirection);
        Point2D.Double obstacleLocation = new Point2D.Double(
                robotX + obstacleDistance * cos(obstacleRadDirection),
                robotY + obstacleDistance * sin(obstacleRadDirection));
        Obstacle obstacle = Obstacle.create(obstacleLocation, sampleTimestamp, 1);
        GridScannerMap map = GridScannerMap.create(List.of(obstacle), AbstractScannerMap.THRESHOLD_DISTANCE);

        /*
        When create the obstacle, sample properties
         */
        List<ObstacleSampleProperties> result = map.obstacleSampleProperties(sample).collect(Collectors.toList());

        /*
        Then should return the expected properties
         */
        assertNotNull(result);
        assertThat(result, hasSize(1));

        ObstacleSampleProperties result1 = result.get(0);
        assertEquals(result1.obstacle, obstacle);
        assertThat(result1.sampleObstacleDistance, closeTo(obstacleLocation.distance(sampleLocation), 1e-3));
        assertThat(result1.robotObstacleDistance, closeTo(obstacleDistance, 5e-4));
        double diffDeg = normalizeDegAngle(toDegrees(result1.obstacleSensorRad) - obstacleRelativeDegDirection);
        assertThat(diffDeg, closeTo(0.0, 0.5));
    }
}