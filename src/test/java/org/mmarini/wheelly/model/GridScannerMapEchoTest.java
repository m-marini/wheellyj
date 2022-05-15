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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mmarini.wheelly.model.GridScannerMap.*;
import static org.mmarini.wheelly.model.Utils.linear;

/**
 * See tests enumeration figure
 */
public class GridScannerMapEchoTest implements GridScannerTest {

    static Stream<Arguments> test10ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance10, GridScannerTest.extrnalDegAngleGen())
                .filter(TestSet::isExternalArea)
                .map(Arguments::of);
    }

    static Stream<Arguments> test1ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance1, GridScannerTest.centralDegAngleGen())
                .filter(TestSet::isCentralArea)
                .filter(TestSet::isDistance1)
                .map(Arguments::of);
    }

    static Stream<Arguments> test2ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance2, GridScannerTest.centralDegAngleGen())
                .filter(TestSet::isCentralArea)
                .filter(TestSet::isDistance2)
                .map(Arguments::of);
    }

    static Stream<Arguments> test3ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance3, GridScannerTest.centralDegAngleGen())
                .filter(TestSet::isCentralArea)
                .filter(TestSet::isDistance3)
                .map(Arguments::of);
    }

    static Stream<Arguments> test4ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance4, GridScannerTest.centralDegAngleGen())
                .filter(TestSet::isCentralArea)
                .filter(TestSet::isDistance4)
                .map(Arguments::of);
    }

    static Stream<Arguments> test5ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance1, GridScannerTest.lateralDegAngleGen())
                .filter(TestSet::isLateralArea)
                .filter(TestSet::isDistance1)
                .map(Arguments::of);
    }

    static Stream<Arguments> test6ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance2, GridScannerTest.lateralDegAngleGen())
                .filter(TestSet::isLateralArea)
                .filter(TestSet::isDistance2)
                .map(Arguments::of);
    }

    static Stream<Arguments> test7ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance3, GridScannerTest.lateralDegAngleGen())
                .filter(TestSet::isLateralArea)
                .filter(TestSet::isDistance3)
                .map(Arguments::of);
    }

    static Stream<Arguments> test8ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance4, GridScannerTest.lateralDegAngleGen())
                .filter(TestSet::isLateralArea)
                .filter(TestSet::isDistance4)
                .map(Arguments::of);
    }

    static Stream<Arguments> test9ArgSet() {
        return GridScannerTest.echoTestSet(GridScannerTest::distance9, GridScannerTest.innerDegAngle())
                .filter(TestSet::isInnerArea)
                .filter(TestSet::isDistance9)
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("test1ArgSet")
    void test1(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(0, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("likelihood", closeTo(1, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
    }

    @ParameterizedTest
    @MethodSource("test10ArgSet")
    void test10(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        assertThat(result, hasSize(2));
        assertThat(result, hasItem(sameInstance(args.obstacle)));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));

    }

    @ParameterizedTest
    @MethodSource("test2ArgSet")
    void test2(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double echoDistance = args.proxySample.value().getSampleDistance();
        double reinforce = min(max(
                linear(args.getObstacleSensorDistance(),
                        echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE, echoDistance - THRESHOLD_DISTANCE,
                        0, 1),
                0), 1);
        double clear = 1 - reinforce;
        long dt = timestamp - args.obstacle.timestamp;
        double obsTimeLike = args.obstacle.likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double hold = 1 - max(clear, reinforce);
        double updatedLike = (obsTimeLike * hold + reinforce) / (reinforce + hold + clear);


        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("likelihood", closeTo(1, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
    }

    @ParameterizedTest
    @MethodSource("test3ArgSet")
    void test3(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        boolean isTarget = echoSnapLocation.equals(obstacleLocation);
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */

        assertThat(result, hasSize(isTarget ? 1 : 2));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(1, 1e-4)),
                        hasProperty("timestamp", equalTo(timestamp))
                )
        ));
        if (!isTarget) {
            assertThat(result, hasItem(
                    allOf(
                            hasProperty("location", equalTo(echoSnapLocation)),
                            hasProperty("likelihood", closeTo(1, 1e-4)),
                            hasProperty("timestamp", equalTo(timestamp))
                    )
            ));
        }
    }

    @ParameterizedTest
    @MethodSource("test4ArgSet")
    void test4(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        boolean isTarget = echoSnapLocation.equals(obstacleLocation);
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        double echoDistance = args.proxySample.value().getSampleDistance();
        double reinforce = linear(args.getObstacleSensorDistance(), echoDistance + THRESHOLD_DISTANCE, echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE, 1, 0);
        long dt = timestamp - args.obstacle.timestamp;
        double obsTimeLike = args.obstacle.likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - reinforce) + reinforce;

        assertThat(result, hasSize(isTarget ? 1 : 2));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-4)),
                        hasProperty("timestamp", equalTo(timestamp))
                )
        ));
        if (!isTarget) {
            assertThat(result, hasItem(
                    allOf(
                            hasProperty("location", equalTo(echoSnapLocation)),
                            hasProperty("likelihood", closeTo(1, 1e-4)),
                            hasProperty("timestamp", equalTo(timestamp))
                    )
            ));
        }
    }

    @ParameterizedTest
    @MethodSource("test5ArgSet")
    void test5(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        double clear = min(max(linear(abs(args.getObstacleSensorDeg()), 45, 30, 0, 1), 0), 1);
        long dt = timestamp - args.obstacle.timestamp;
        double obsTimeLike = args.obstacle.likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - clear);

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }

    @ParameterizedTest
    @MethodSource("test6ArgSet")
    void test6(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        double echoDistance = args.proxySample.value().getSampleDistance();
        double isOnDirection = min(max(
                linear(abs(args.getObstacleSensorDeg()), 45, 30, 0, 1),
                0), 1);
        double isNearEcho = min(max(
                linear(args.getObstacleSensorDistance(),
                        echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE, echoDistance - THRESHOLD_DISTANCE,
                        0, 1),
                0), 1);

        double reinforce = min(isOnDirection, isNearEcho);
        double clear = min(isOnDirection, 1 - isNearEcho);
        long dt = timestamp - args.obstacle.timestamp;
        double obsTimeLike = args.obstacle.likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double hold = 1 - max(clear, reinforce);
        double updatedLike = (obsTimeLike * hold + reinforce) / (reinforce + hold + clear);

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }

    @ParameterizedTest
    @MethodSource("test7ArgSet")
    void test7(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        double reinforce = min(max(linear(abs(args.getObstacleSensorDeg()), NO_SENSITIVITY_DEG, MAX_SENSITIVITY_DEG, 0, 1), 0), 1);
        long dt = timestamp - args.obstacle.timestamp;
        double obsTimeLike = args.obstacle.likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - reinforce) + reinforce;

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }

    @ParameterizedTest
    @MethodSource("test8ArgSet")
    void test8(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        double echoDistance = args.proxySample.value().getSampleDistance();
        double distanceReinforce = linear(args.getObstacleSensorDistance(), echoDistance + THRESHOLD_DISTANCE, echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE, 1, 0);
        double directionReinforce = min(max(linear(abs(args.getObstacleSensorDeg()), NO_SENSITIVITY_DEG, MAX_SENSITIVITY_DEG, 0, 1), 0), 1);
        double reinforce = min(distanceReinforce, directionReinforce);
        long dt = timestamp - args.obstacle.timestamp;
        double obsTimeLike = args.obstacle.likelihood * exp(-dt / 1000d / LIKELIHOOD_TAU);
        double updatedLike = obsTimeLike * (1 - reinforce) + reinforce;

        assertThat(result, hasSize(2));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", closeTo(updatedLike, 1e-3f))
                )));
    }

    @ParameterizedTest
    @MethodSource("test9ArgSet")
    void test9(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0);

        /*
        When create the new map from sample
         */
        Optional<Point2D> echoLocation = args.proxySample.value().getSampleLocation();
        Point2D echoSnapLocation = echoLocation.map(l -> snapToGrid(l, THRESHOLD_DISTANCE)).orElseThrow();
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        /*
        Then should return an empty map (the obstacle should be removed
         */
        assertThat(result, hasSize(2));
        assertThat(result, hasItem(sameInstance(args.obstacle)));
        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(echoSnapLocation)),
                        hasProperty("timestamp", equalTo(timestamp)),
                        hasProperty("likelihood", equalTo(1d))
                )));

    }
}