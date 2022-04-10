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
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.model.ScannerMap.*;

/**
 * See tests enumeration figure
 */
public class ScannerMapEchoTest {

    static final int MAX_DT = 1000;
    static final double MIN_OBSTACLE_DISTANCE = 0.2;
    static final double MIN_ECHO_DISTANCE = MIN_OBSTACLE_DISTANCE + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE + 0.1;
    static final double MAX_DISTANCE = 3d;
    static final double D_EPSILON = 1e-3;
    static final int MAX_DEG_DIRECTION = 359;
    static final double LOCATION_RANGE = 3;
    static final int MAX_SENSOR_DEG = 90;
    static final int MAX_SENSITIVITY_DEG = (int) round(toDegrees(MAX_SENSITIVITY_ANGLE));
    static final int NO_SENSITIVITY_DEG = (int) round(toDegrees(NO_SENSITIVITY_ANGLE));
    static final int LATERAL_DEG = NO_SENSITIVITY_DEG - MAX_SENSITIVITY_DEG;

    static Stream<Arguments> centralArgSet() {
        return createStream(1234,
                sampleGen(),
                uniform(0.0, 1.0),  // obstacleRelDistance
                centralDegAngleGen(), // obstacleRelDeg

                uniform(0, MAX_DT),     // dt
                exponential(0.5, 1d)   // likelihood
        );
    }

    static ArgumentsGenerator<Integer> centralDegAngleGen() {
        return uniform(-MAX_SENSITIVITY_DEG + 1, MAX_SENSITIVITY_DEG - 1);
    }

    static Obstacle createObstalce(Timed<ProxySample> timedSample, int obstacleRelDeg, double obstacleDistance,
                                   long timestamp, double likelihood) {
        double obstacleRadDirection = toNormalRadians(timedSample.value().asset.direction + timedSample.value().relativeDirection + obstacleRelDeg);

        Point2D.Double obstacleLocation = new Point2D.Double(
                timedSample.value().asset.location.getX() + obstacleDistance * cos(obstacleRadDirection),
                timedSample.value().asset.location.getY() + obstacleDistance * sin(obstacleRadDirection)
        );
        return Obstacle.create(obstacleLocation, timestamp, (float) likelihood);
    }

    static double distance1(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                MIN_OBSTACLE_DISTANCE,
                echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE - D_EPSILON);
    }

    static double distance2(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE + D_EPSILON,
                echoDistance - THRESHOLD_DISTANCE - D_EPSILON);
    }

    static double distance3(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance - THRESHOLD_DISTANCE,
                echoDistance + THRESHOLD_DISTANCE);
    }

    static double distance4(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance + THRESHOLD_DISTANCE + D_EPSILON,
                echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE - D_EPSILON);
    }

    static double distance9(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE,
                echoDistance + MAX_DISTANCE);
    }

    static Stream<Arguments> externalArgSet() {
        return createStream(1234,
                sampleGen(),
                exponential(THRESHOLD_DISTANCE, MAX_DISTANCE),
                uniform(-NO_SENSITIVITY_DEG, NO_SENSITIVITY_DEG + 1)
                        .map(x -> (int) normalizeDegAngle(180 - x)),
                uniform(0, MAX_DT),     // dt
                exponential(0.5, 1d)
        );
    }

    static Stream<Arguments> lateralArgSet() {
        return createStream(1234,
                sampleGen(),
                uniform(0.0, 1.0),                                  // obstacleRelDistance
                lateralDegAngleGen(), // obstacleRelDeg

                uniform(0, MAX_DT),     // dt
                exponential(0.5, 1d)   // likelihood
        );
    }

    private static ArgumentsGenerator<Integer> lateralDegAngleGen() {
        return uniform(-LATERAL_DEG + 1, LATERAL_DEG - 2) // (-14, ..., -1, 0, ..., 13
                .map(i -> i < 0
                        ? i - MAX_SENSITIVITY_DEG
                        : i + MAX_SENSITIVITY_DEG + 1);
        // (-44, ..., -31, 31, ..., 44)
    }

    static double linear(double x, double xmin, double xmax, double ymin, double ymax) {
        return (x - xmin) * (ymax - ymin) / (xmax - xmin) + ymin;
    }

    static ArgumentsGenerator<Timed<ProxySample>> sampleGen() {
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
                exponential(MIN_ECHO_DISTANCE, MAX_DISTANCE - D_EPSILON),  // echoDistance
                robotAsset,
                (sensorDegDirection, echoDistance, ra) ->
                        new Timed<>(
                                ProxySample.create(sensorDegDirection, echoDistance.floatValue(), ra),
                                sampleTimestamp, TimeUnit.MILLISECONDS)
        );
    }

    @ParameterizedTest
    @MethodSource("centralArgSet")
    void test1(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance1(obstacleRelDistance, timedSample.value().distance);
        long timestamp = timedSample.time(TimeUnit.MILLISECONDS);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timestamp - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());
        Point2D echoLocation = timedSample.value().getLocation().orElseThrow();
        Point2D obstacleLocation = obstacle.getLocation();

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(0, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoLocation)),
                        hasProperty("likelihood", closeTo(1, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
    }

    @ParameterizedTest
    @MethodSource("externalArgSet")
    void test10(Timed<ProxySample> timedSample,
                double obstacleDistance, int obstacleRelDeg,
                long dt, double likelihood) {
        long timestamp = timedSample.time(TimeUnit.MILLISECONDS);
        double echoDistance = timedSample.value().distance;
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg,
                obstacleDistance + echoDistance + FUZZY_THRESHOLD_DISTANCE + THRESHOLD_DISTANCE,
                timestamp - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(sameInstance(obstacle)));
        Point2D sampleLocation = timedSample.value().getLocation().orElseThrow();
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(sampleLocation)),
                        hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(sameInstance(obstacle)));
    }

    @ParameterizedTest
    @MethodSource("centralArgSet")
    void test2(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance2(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double echoDistance = timedSample.value().distance;
        double reinforce = min(max(
                linear(obstacleDistance,
                        echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE, echoDistance - THRESHOLD_DISTANCE,
                        0, 1),
                0), 1);
        double clear = 1 - reinforce;
        double obsTimeLike = likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double hold = 1 - max(clear, reinforce);
        double updatedLike = (obsTimeLike * hold + reinforce) / (reinforce + hold + clear);

        assertThat(result, hasSize(1));

        Obstacle obs = result.get(0);

        assertThat(obs.location, equalTo(obstacle.location));
        assertThat(obs.likelihood, closeTo(updatedLike, 1e-4));
        assertThat(obs.timestamp, equalTo(timedSample.time(TimeUnit.MILLISECONDS)));
    }

    @ParameterizedTest
    @MethodSource("centralArgSet")
    void test3(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance3(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        assertThat(result, hasSize(1));

        Obstacle obs = result.get(0);

        assertThat(obs.location, equalTo(obstacle.location));
        assertThat(obs.likelihood, closeTo(1, 1e-4));
        assertThat(obs.timestamp, equalTo(timedSample.time(TimeUnit.MILLISECONDS)));
    }

    @ParameterizedTest
    @MethodSource("centralArgSet")
    void test4(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance4(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double echoDistance = timedSample.value().distance;
        double reinforce = linear(obstacleDistance, echoDistance + THRESHOLD_DISTANCE, echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE, 1, 0);
        double obsTimeLike = likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - reinforce) + reinforce;
        assertThat(result, hasSize(1));

        Obstacle obs = result.get(0);

        assertThat(obs.location, equalTo(obstacle.location));
        assertThat(obs.likelihood, closeTo(updatedLike, 1e-4));
        assertThat(obs.timestamp, equalTo(timedSample.time(TimeUnit.MILLISECONDS)));
    }

    @ParameterizedTest
    @MethodSource("lateralArgSet")
    void test5(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance1(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double clear = min(max(linear(abs(obstacleRelDeg), 45, 30, 0, 1), 0), 1);
        double obsTimeLike = likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - clear);
        Point2D sampleLocation = timedSample.value().getLocation().orElseThrow();

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(sampleLocation)),
                        hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacle.location)),
                        hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }

    @ParameterizedTest
    @MethodSource("lateralArgSet")
    void test6(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance2(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double echoDistance = timedSample.value().distance;
        double isOnDirection = min(max(
                linear(abs(obstacleRelDeg), 45, 30, 0, 1),
                0), 1);
        double isNearEcho = min(max(
                linear(obstacleDistance,
                        echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE, echoDistance - THRESHOLD_DISTANCE,
                        0, 1),
                0), 1);

        double reinforce = min(isOnDirection, isNearEcho);
        double clear = min(isOnDirection, 1 - isNearEcho);
        double obsTimeLike = likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double hold = 1 - max(clear, reinforce);
        double updatedLike = (obsTimeLike * hold + reinforce) / (reinforce + hold + clear);

        Point2D echoLocation = timedSample.value().getLocation().orElseThrow();
        assertThat(result, hasSize(2));

        assertThat(result, hasItem(allOf(
                hasProperty("location", equalTo(obstacle.location)),
                hasProperty("likelihood", closeTo(updatedLike, 1e-4)),
                hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))
                ))));
        assertThat(result, hasItem(allOf(
                hasProperty("location", equalTo(echoLocation)),
                hasProperty("likelihood", closeTo(1, 1e-4)),
                hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))
                ))));
    }

    @ParameterizedTest
    @MethodSource("lateralArgSet")
    void test7(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance3(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double reinforce = min(max(linear(abs(obstacleRelDeg), NO_SENSITIVITY_DEG, MAX_SENSITIVITY_DEG, 0, 1), 0), 1);
        double obsTimeLike = likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - reinforce) + reinforce;
        Point2D sampleLocation = timedSample.value().getLocation().orElseThrow();

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(sampleLocation)),
                        hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacle.location)),
                        hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }


    @ParameterizedTest
    @MethodSource("lateralArgSet")
    void test8(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance4(obstacleRelDistance, timedSample.value().distance);
        long timestamp = timedSample.time(TimeUnit.MILLISECONDS);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timestamp - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double echoDistance = timedSample.value().distance;
        double distanceReinforce = linear(obstacleDistance, echoDistance + THRESHOLD_DISTANCE, echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE, 1, 0);
        double directionReinforce = min(max(linear(abs(obstacleRelDeg), NO_SENSITIVITY_DEG, MAX_SENSITIVITY_DEG, 0, 1), 0), 1);
        double reinforce = min(distanceReinforce, directionReinforce);
        double obsTimeLike = likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - reinforce) + reinforce;
        Point2D sampleLocation = timedSample.value().getLocation().orElseThrow();

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(sampleLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacle.location)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }

    @ParameterizedTest
    @MethodSource("centralArgSet")
    void test9(Timed<ProxySample> timedSample,
               double obstacleRelDistance, int obstacleRelDeg,
               long dt, double likelihood) {
        double obstacleDistance = distance9(obstacleRelDistance, timedSample.value().distance);
        Obstacle obstacle = createObstalce(timedSample, obstacleRelDeg, obstacleDistance, timedSample.time(TimeUnit.MILLISECONDS) - dt, likelihood);
        List<Obstacle> obstacles = List.of(obstacle);
        ScannerMap map = new ScannerMap(obstacles);

        /*
        When create the new map from sample
         */
        List<Obstacle> result = map.createObstacles(timedSample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        assertThat(result, hasSize(2));

        assertThat(result, hasItem(sameInstance(obstacle)));
        Point2D sampleLocation = timedSample.value().getLocation().orElseThrow();
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(sampleLocation)),
                        hasProperty("timestamp", equalTo(timedSample.time(TimeUnit.MILLISECONDS))),
                        hasProperty("likelihood", equalTo(1d))
                )));
    }
}