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
import org.mmarini.ArgumentsGenerator;

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
import static org.mmarini.wheelly.model.ScannerMap.FUZZY_THRESHOLD_DISTANCE;
import static org.mmarini.wheelly.model.ScannerMap.normalizeAngle;
import static org.mmarini.wheelly.model.ScannerMapEchoTest.*;

public class ScannerMapNoEchoTest {

    public static final int MAX_DT = 1000;
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

    static ArgumentsGenerator<Timed<ProxySample>> noEchoSampleGen() {
        ArgumentsGenerator<RobotAsset> robotAsset = combine(
                uniform(-LOCATION_RANGE, LOCATION_RANGE),   // robotX
                uniform(-LOCATION_RANGE, LOCATION_RANGE),   // robotY
                uniform(0, MAX_DEG_DIRECTION),              // robotDeg
                (robotX, robotY, robotDegDirection) ->
                        RobotAsset.create(robotX.floatValue(), robotY.floatValue(), robotDegDirection)
        );
        long sampleTimestamp = System.currentTimeMillis();
        return combine(
                uniform(-MAX_SENSOR_DEG, MAX_SENSOR_DEG),   // relSensorDeg
                robotAsset,
                (sensorDegDirection, ra) ->
                        new Timed<>(
                                ProxySample.create(sensorDegDirection, 0, ra),
                                sampleTimestamp, TimeUnit.MILLISECONDS)
        );
    }

    static Stream<Arguments> test1Arg() {
        return createStream(1234,
                noEchoSampleGen(),
                exponential(MIN_OBSTACLE_DISTANCE, MAX_DISTANCE - FUZZY_THRESHOLD_DISTANCE - D_EPSILON),  // obstacleDistance
                centralDegAngleGen(), // obstacleRelDeg

                uniform(0, MAX_DT),     // dt
                exponential(0.5, 1d)   // likelihood
        );
    }

    @ParameterizedTest
    @MethodSource("test1Arg")
    void test1(Timed<ProxySample> timedSample,
               double obstacleDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        long timestamp = timedSample.time(TimeUnit.MILLISECONDS);
        Obstacle obstacle = ScannerMapEchoTest.createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timestamp - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());
        Point2D obstacleLocation = obstacle.getLocation();

        assertThat(result, hasSize(1));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(0, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
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
        ObstacleSampleProperties result = ScannerMap.createSampleProperties(obstacle, null, robotLocation, toRadians(sensorDirection));

        /*
        Then should return the expected properties
         */
        assertNotNull(result);
        assertEquals(result.obstacle, obstacle);
        assertThat(result.sampleObstacleDistance, equalTo(Double.MAX_VALUE));
        assertThat(result.robotObstacleDistance, closeTo(obstacleDistance, 5e-4));
        assertThat(result.obstacleSensorDirection, closeTo(sensorRelativeRadDirection, toRadians(0.5)));
    }
}