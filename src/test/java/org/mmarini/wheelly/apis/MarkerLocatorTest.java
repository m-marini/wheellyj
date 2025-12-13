/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.Utils.add;
import static org.mmarini.Utils.mul;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.Utils.mm2m;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MarkerLocatorTest {
    public static final double MM_1 = 1e-3;
    public static final long CORRELATION_INTERVAL = 500;
    public static final double DECAY_TIME = 600;
    public static final long CLEAN_INTERVAL = 600;

    public static final long T0 = 100;
    public static final long CORRELATED_CAMERA_TIME = T0 + CORRELATION_INTERVAL;
    public static final long UNCORRELATED_CAMERA_TIME = T0 + CORRELATION_INTERVAL + 1;

    public static final long SEED = 1234;
    public static final double MARKER_SIZE = 0.2;
    public static final String LABEL_A = "A";
    public static final String LABEL_B = "B";

    static CameraEvent createCameraEvent(long cameraTime, String label, int labelDeg) {
        return new CameraEvent(cameraTime, label, 0, 0, new Point2D[0], Complex.fromDeg(labelDeg));
    }

    static CorrelatedCameraEvent createCorrelateEvent(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, String label, int labelDeg) {
        WheellyLidarMessage lidar = createLidar(xRobot, yRobot, robotDeg, headDeg, distance);
        CameraEvent camera = createCameraEvent(CORRELATED_CAMERA_TIME, label, labelDeg);
        return new CorrelatedCameraEvent(camera, lidar);
    }

    static WheellyLidarMessage createLidar(double xRobot, double yRobot, int robotDeg, int headDeg, int distance) {
        Point2D pulses = location2Pulses(xRobot, yRobot);
        return new WheellyLidarMessage(MarkerLocatorTest.T0, distance, 0, pulses.getX(), pulses.getY(), robotDeg, headDeg);
    }

    static LabelMarker createMarkerAt(CorrelatedCameraEvent event, int markerDeg, double markerDistance) {
        return createMarkerAt(T0, event, markerDeg, markerDistance);
    }

    static LabelMarker createMarkerAt(long time, CorrelatedCameraEvent event, int markerDeg, double markerDistance) {
        return createMarkerAt(time, time - CLEAN_INTERVAL, event, markerDeg, markerDistance);
    }

    static LabelMarker createMarkerAt(long markerTime, long cleanTime, CorrelatedCameraEvent event, int markerDeg, double markerDistance) {
        Point2D lidarLocation = DEFAULT_ROBOT_SPEC.frontLidarLocation(event.robotLocation(), event.robotDirection(), event.headDirection());
        Point2D markerLocation = event.lidarYaw().add(Complex.fromDeg(markerDeg)).at(lidarLocation, markerDistance);
        return new LabelMarker(LABEL_A, markerLocation, 1, markerTime, cleanTime);
    }

    static CorrelatedCameraEvent createUncorrelatedEvent(double xRobot, double yRobot, int robotDeg, int headDeg, int distance,
                                                         int labelDeg) {
        WheellyLidarMessage lidar = createLidar(xRobot, yRobot, robotDeg, headDeg, distance);
        CameraEvent camera = createCameraEvent(UNCORRELATED_CAMERA_TIME, MarkerLocatorTest.LABEL_A, labelDeg);
        return new CorrelatedCameraEvent(camera, lidar);
    }

    static Stream<Arguments> dataNewBehindDifferentMarkerInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(1001, 2000, 5) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .uniform(DEFAULT_LIDAR_FOV_DEG / 2 + 1, 360 - DEFAULT_LIDAR_FOV_DEG / 2 - 1) // markerRange
                .exponential(0.3, 1, 5) // distance
                .build(100);
    }

    static Stream<Arguments> dataNewDifferentMarkerInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(1001, 2000, 5) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + 2, DEFAULT_LIDAR_FOV_DEG / 2 - 2) // markerRange
                .exponential(0.3, 1, 5) // distance
                .build(100);
    }

    static Stream<Arguments> dataNewFarDifferentMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(300, 1000, 17) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + 2, DEFAULT_LIDAR_FOV_DEG / 2 - 2) // markerRange
                .exponential(1.001, 2, 17) // distance
                .build(100);
    }

    static Stream<Arguments> dataNewMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(400, 1500, 5) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .build(100);
    }

    static Stream<Arguments> dataNoSignalBehindMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .uniform(DEFAULT_LIDAR_FOV_DEG / 2 + 1, 360 - DEFAULT_LIDAR_FOV_DEG / 2 - 1) // labelDeg
                .exponential(10e-3, 1.5, 5) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dataNoSignalFarMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .exponential(2.001, 3, 17) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dataNoSignalInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .exponential(10e-3, 1.5, 5) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dataNoSignalRecentMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .exponential(10e-3, 1.5, 5) // markerDistance
                .uniform(1, (int) (CLEAN_INTERVAL / 2 - 1))
                .build(100);
    }

    static Stream<Arguments> dataUnknownBehindMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(300, 1000, 5) // distance
                .uniform(DEFAULT_LIDAR_FOV_DEG / 2 + 1, 360 - DEFAULT_LIDAR_FOV_DEG / 2 + 1) // markerDeg
                .exponential(0.3, 2, 17) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dataUnknownFarMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(300, 1000, 5) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + 2, DEFAULT_LIDAR_FOV_DEG / 2 - 2) // markerDeg
                .exponential(1.001, 2, 5) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dataUnknownInRange() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .uniform(1000, 2000) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2 + 2, DEFAULT_LIDAR_FOV_DEG / 2 - 2) // markerDeg
                .exponential(0.3, 0.999) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dateExistingMarker() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // xRobot
                .uniform(-2.0, 2.0, 9) // yRobot
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // headDeg
                .exponential(400, 1500, 5) // distance
                .uniform(-DEFAULT_LIDAR_FOV_DEG / 2, DEFAULT_LIDAR_FOV_DEG / 2) // labelDeg
                .uniform(0, 3000, 17) //  dTimeMarker
                .uniform(0, 359) // markerDeg
                .exponential(0.4, 2, 17) // markerDistance
                .build(100);
    }

    MarkerLocator locator;

    @BeforeEach
    void setUp() {
        locator = new MarkerLocator(DECAY_TIME, DECAY_TIME, CORRELATION_INTERVAL, 1, MARKER_SIZE);
    }

    /**
     * update map with an existing label marker
     */
    @ParameterizedTest(name = "[{index}] robot @({0},{1}) R{2} head {3} DEG, label {4} mm, {5} DEG, marker {6} ms, {8} m, {7} DEG")
    @CsvSource({
            "0,0, 0,0, 1000,0, 0,0,0.3"
    })
    @MethodSource("dateExistingMarker")
    void testExistingMarker(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int labelDeg,
                            int dTimeMarker, int markerDeg, double markerDistance) {
        // Given a correlated event with a label
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, distance, LABEL_A, labelDeg);
        // And a marker
        LabelMarker marker = createMarkerAt(T0 - dTimeMarker, event, markerDeg, markerDistance);
        Map<String, LabelMarker> map = Map.of(LABEL_A, marker);

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the marker should exist
        LabelMarker newMarker = newMap.get(LABEL_A);
        assertNotNull(newMarker);

        // And the marker should have event time
        assertEquals(event.lidarTime(), newMarker.markerTime());

        // And the marker should be located between signal location and marker location
        double gamma = Math.expm1(-(double) dTimeMarker / DECAY_TIME) + 1;
        double notGamma = 1 - gamma;
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D lidarLocation = DEFAULT_ROBOT_SPEC.frontLidarLocation(robotLocation,
                robotDir, headDir);
        Complex labelDir = robotDir.add(headDir).add(Complex.fromDeg(labelDeg));
        Point2D labelLocation = labelDir.at(lidarLocation, mm2m(distance));

        Point2D expectedMarker = add(
                mul(marker.location(), gamma),
                mul(labelLocation, notGamma));
        assertThat(newMarker.location(), pointCloseTo(expectedMarker, MM_1));
    }

    /**
     * A new marker in an empty map
     */
    @ParameterizedTest(name = "[{index}] robot @({0},{1}) R{2} head {3} DEG, label {4} mm, {5} DEG, marker {6} DEG, D{7} m")
    @CsvSource({
            "0,0, 0,0, 1000,0, 0,0.3"
    })
    @MethodSource("dataNewDifferentMarkerInRange")
    void testNewDifferentMarkerInRange(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int labelDeg, int markerDeg, double markerDistance) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, distance, LABEL_B, labelDeg);
        // And a marker in lidar range
        Map<String, LabelMarker> map = Map.of(LABEL_A,
                createMarkerAt(event, markerDeg, markerDistance));

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the marker should exist
        assertThat(newMap, hasKey(LABEL_B));
        assertThat(newMap, not(hasKey(LABEL_A)));

        // And the marker should be labelled "B"
        LabelMarker marker = newMap.get(LABEL_B);
        assertEquals(LABEL_B, marker.label());

        // And should have lidar message markerTime
        assertEquals(T0, marker.markerTime());

        // And should be located at signal location
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D lidarLocation = DEFAULT_ROBOT_SPEC.frontLidarLocation(robotLocation,
                robotDir, headDir);
        Complex labelDir = robotDir.add(headDir).add(Complex.fromDeg(labelDeg));
        Point2D expected = labelDir.at(lidarLocation, mm2m(distance));
        assertThat(marker.location(), pointCloseTo(expected, MM_1));
    }

    /**
     * A new marker in an empty map
     */
    @ParameterizedTest(name = "[{index}] robot @({0},{1}) R{2} head {3} DEG, label {4} mm, {5} DEG, marker {6} DEG, D{7} m")
    @CsvSource({
            "0,0, 0,0, 300,0, 0,1"
    })
    @MethodSource({
            "dataNewBehindDifferentMarkerInRange",
            "dataNewFarDifferentMarker",
    })
    void testNewDifferentMarkerOutRange(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int labelDeg, int markerDeg, double markerDistance) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, distance, LABEL_B, labelDeg);
        // And a marker in lidar range
        LabelMarker marker = createMarkerAt(event, markerDeg, markerDistance);
        Map<String, LabelMarker> map = Map.of(LABEL_A,
                marker);

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the marker should exist
        assertThat(newMap, hasKey(LABEL_B));
        // and the old marker should still remain
        assertThat(newMap, hasEntry(LABEL_A, marker));

        // And the marker should be labelled "B"
        LabelMarker markerB = newMap.get(LABEL_B);
        assertEquals(LABEL_B, markerB.label());

        // And should have lidar message markerTime
        assertEquals(T0, markerB.markerTime());

        // And should be located at signal location
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D lidarLocation = DEFAULT_ROBOT_SPEC.frontLidarLocation(robotLocation,
                robotDir, headDir);
        Complex labelDir = robotDir.add(headDir).add(Complex.fromDeg(labelDeg));
        Point2D expected = labelDir.at(lidarLocation, mm2m(distance));
        assertThat(markerB.location(), pointCloseTo(expected, MM_1));
    }

    /**
     * A new marker in an empty map
     */
    @ParameterizedTest(name = "[{index}] robot @({0},{1}) R{2} head {3} DEG, label {4} mm, {5} DEG ")
    @CsvSource({
            "0,0, 0,0, 400, 0",
            "1,0, 0,0, 400, 0",
    })
    @MethodSource("dataNewMarker")
    void testNewMarker(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int labelDeg) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, distance, LABEL_A, labelDeg);
        // And an empty map
        Map<String, LabelMarker> map = Map.of();

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the marker should exist
        assertThat(newMap, hasKey(LABEL_A));

        // And the marker should be labelled "A"
        LabelMarker marker = newMap.get(LABEL_A);
        assertEquals(LABEL_A, marker.label());

        // And should have lidar message markerTime
        assertEquals(T0, marker.markerTime());

        // And should be located at signal location
        Complex robotDir = Complex.fromDeg(robotDeg);
        Complex headDir = Complex.fromDeg(headDeg);
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D cameraLocation = DEFAULT_ROBOT_SPEC.cameraLocation(robotLocation,
                robotDir, headDir);
        Complex labelDir = robotDir.add(headDir).add(Complex.fromDeg(labelDeg));
        Point2D expected = labelDir.at(cameraLocation, mm2m(distance));
        assertThat(marker.location(), pointCloseTo(expected, MM_1));
    }

    /**
     * No signal in a map with label marker in lidar range
     */
    @ParameterizedTest(name = "[{index} Robot @({0},{1}) R{2} head {3} DEG, marker {4} DEG, D{5} m")
    @MethodSource("dataNoSignalInRange")
    void testNoSignalInRange(double xRobot, double yRobot, int robotDeg, int headDeg, int markerDeg, double markerDistance) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, 0, UNKNOWN_QR_CODE, 0);
        // And a marker in lidar range
        Map<String, LabelMarker> map = Map.of(LABEL_A,
                createMarkerAt(event, markerDeg, markerDistance));

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the map should be empty
        assertTrue(newMap.isEmpty());
    }

    /**
     * No signal in a map with label marker out of lidar range
     */
    @ParameterizedTest(name = "[{index} Robot @({0},{1}) R{2} head {3} DEG, marker {4} DEG, D{5} m")
    @MethodSource({
            "dataNoSignalBehindMarker",
            "dataNoSignalFarMarker"
    })
    void testNoSignalOutRange(double xRobot, double yRobot, int robotDeg, int headDeg, int markerDeg, double markerDistance) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, 0, UNKNOWN_QR_CODE, 0);
        // And a marker in lidar range
        LabelMarker marker = createMarkerAt(event, markerDeg, markerDistance);
        Map<String, LabelMarker> map = Map.of(LABEL_A,
                marker);

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the map should be empty
        assertThat(newMap, hasEntry(LABEL_A, marker));
    }

    /**
     * No signal in a map with recent label marker in lidar range
     */
    @ParameterizedTest(name = "[{index} Robot @({0},{1}) R{2} head {3} DEG, marker {4} DEG, D{5} m, dt{6} ms")
    @MethodSource("dataNoSignalRecentMarker")
    void testNoSignalRecentMarker(double xRobot, double yRobot, int robotDeg, int headDeg, int markerDeg, double markerDistance, long dtCleanTime) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, 0, UNKNOWN_QR_CODE, 0);
        // And a marker in lidar range
        LabelMarker marker = createMarkerAt(T0, T0 - dtCleanTime, event, markerDeg, markerDistance);
        Map<String, LabelMarker> map = Map.of(LABEL_A, marker);

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the map should have the label
        LabelMarker newMarker = newMap.get(LABEL_A);
        assertNotNull(newMarker);
        assertThat(newMarker.weight(), greaterThan(0D));
        assertThat(newMarker.weight(), lessThan(marker.weight()));
    }

    /**
     * A new marker in an empty map
     */
    @ParameterizedTest(name = "[{index}] robot @({0},{1}) R{2} head {3} DEG, label {4} mm, {5} DEG ")
    @CsvSource({
            "0,0, 0,0, 400, 0",
    })
    @MethodSource("dataNewMarker")
    void testUncorrelatedSignals(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int labelDeg) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createUncorrelatedEvent(xRobot, yRobot, robotDeg, headDeg, distance, labelDeg);
        // And an empty map
        Map<String, LabelMarker> map = Map.of();

        // When update by marker locator
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the marker should exist
        assertTrue(newMap.isEmpty());
    }

    /**
     * A new unknown marker in a map with marker in range
     */
    @CsvSource({
            "0,0, 0,0, 1000, 0,0.4"
    })
    @ParameterizedTest(name = "[{index} Robot @({0},{1}) R{2} head {3} DEG, D{4} mm, marker {5} DEG, D{6} m")
    @MethodSource("dataUnknownInRange")
    void testUnknownInRange(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int markerDeg, double markerDistance) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, distance, UNKNOWN_QR_CODE, 0);
        // And a marker in lidar range
        Map<String, LabelMarker> map = Map.of(LABEL_A,
                createMarkerAt(event, markerDeg, markerDistance));

        // When update by event
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the map should be empty
        assertTrue(newMap.isEmpty());
    }

    /**
     * A new unknown marker in a map with marker in range
     */
    @CsvSource({
            "0,0, 0,0, 400, 0,1"
    })
    @ParameterizedTest(name = "[{index} Robot @({0},{1}) R{2} head {3} DEG, D{4} mm, marker {5} DEG, D{6} m")
    @MethodSource({
            "dataUnknownBehindMarker",
            "dataUnknownFarMarker",
    })
    void testUnknownOutRange(double xRobot, double yRobot, int robotDeg, int headDeg, int distance, int markerDeg, double markerDistance) {
        // Given a correlated event with a marker
        CorrelatedCameraEvent event = createCorrelateEvent(xRobot, yRobot, robotDeg, headDeg, distance, UNKNOWN_QR_CODE, 0);
        // And a marker in lidar range
        LabelMarker marker = createMarkerAt(event, markerDeg, markerDistance);
        Map<String, LabelMarker> map = Map.of(LABEL_A,
                marker);

        // When update by event
        Map<String, LabelMarker> newMap = locator.update(map, event, DEFAULT_ROBOT_SPEC);

        // Then the map should be empty
        assertThat(newMap, hasEntry(LABEL_A, marker));
    }

}