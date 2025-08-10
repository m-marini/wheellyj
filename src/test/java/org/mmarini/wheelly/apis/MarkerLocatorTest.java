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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotSpec.*;

class MarkerLocatorTest {
    public static final double MM_1 = 1e-3;
    public static final long CORRELATION_INTERVAL = 500;
    public static final double DECAY_TIME = 600;
    public static final double MAX_DISTANCE = 3;
    public static final int RECEPTIVE_ANGLE_DEG = 15;

    public static final long T0 = 100;
    public static final long T2 = T0 + CORRELATION_INTERVAL + 1;
    public static final long DT = 600;
    public static final long T3 = T0 + DT - CORRELATION_INTERVAL;
    public static final long T4 = T0 + DT;
    public static final double ECHO_DISTANCE = 1.2;
    public static final long ECHO_DELAY = Math.round(ECHO_DISTANCE / DISTANCE_SCALE);

    public static final Point2D ROBOT_LOCATION = new Point2D.Double(1, 1);
    public static final Point2D POINT0 = new Point2D.Double(2, 2);
    public static final long SEED = 1234;
    public static final double MARKER_SIZE = 0.2;
    public static final String LABEL_A = "A";
    public static final String LABEL_B = "B";

    private static CameraEvent createCameraEvent(long cameraTime, String label, int labelDeg) {
        return new CameraEvent(cameraTime, label, 0, 0, new Point2D[0], Complex.fromDeg(labelDeg));
    }

    /**
     * Returns a correlate event
     *
     * @param cameraTime   the camera event time (ms)
     * @param label        the label
     * @param labelDeg     the label position relative the camera direction (DEG)
     * @param proxyTime    the proxy time (ms)
     * @param echoDistance the echo distance (m)
     * @param echoDeg      the proxy direction relative to the robot direction (DEG)
     * @param robotYaw     the proxy direction radial relative to north environment (DEG)
     */
    private static CorrelatedCameraEvent createCorrelateEvent(long cameraTime, String label, int labelDeg, long proxyTime, double echoDistance, int echoDeg, int robotYaw) {
        return new CorrelatedCameraEvent(
                createCameraEvent(cameraTime, label, labelDeg),
                createProxy(proxyTime, distance2Delay(echoDistance), echoDeg, robotYaw)
        );
    }

    private static CorrelatedCameraEvent createCorrelateEvent(long t3, long t4, int echoDeg) {
        return createCorrelateEvent(t3, MarkerLocatorTest.LABEL_A, 0, t4, delay2Distance(MarkerLocatorTest.ECHO_DELAY), echoDeg, 0);
    }

    static LabelMarker createMarkerAt(int markerDeg, double markerDistance) {
        Point2D location = Complex.fromDeg(markerDeg).at(ROBOT_LOCATION, markerDistance);
        return new LabelMarker(LABEL_A, location, 1, T0, T0);
    }

    static WheellyProxyMessage createProxy(long time, long delay, int sensorDeg, int yawDeg) {
        return new WheellyProxyMessage(time, sensorDeg, delay,
                distance2Pulse(ROBOT_LOCATION.getX()),
                distance2Pulse(ROBOT_LOCATION.getY()),
                yawDeg);
    }

    public static Stream<Arguments> dataEchoCameraClean() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // markerDeg
                .uniform(0.1, MAX_DISTANCE) // marker distance
                .uniform(-RECEPTIVE_ANGLE_DEG + 4, RECEPTIVE_ANGLE_DEG - 4) // Delta camera azimuth (receptive angle)
                .uniform(0D, 1D) // echo-marker relative distance
                .build(100);
    }

    public static Stream<Arguments> dataEchoCameraNoClean() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // markerDeg
                .uniform(0.1, MAX_DISTANCE) // marker distance
                .uniform(RECEPTIVE_ANGLE_DEG + 1, 360 - RECEPTIVE_ANGLE_DEG - 1) // Delta camera azimuth (receptive angle)
                .uniform(0D, 1D) // echo-marker relative distance
                .build(100);
    }

    public static Stream<Arguments> dataNoEchoUnknownClean() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // echoDeg
                .uniform(0.1, MAX_DISTANCE) // marker distance
                .uniform(-RECEPTIVE_ANGLE_DEG + 1, RECEPTIVE_ANGLE_DEG - 1) // delta camera azimuth
                .build(100);
    }

    public static Stream<Arguments> dataNoEchoUnknownNoClean() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // echoDeg
                .uniform(0.1, MAX_DISTANCE) // marker distance
                .uniform(RECEPTIVE_ANGLE_DEG + 1, 360 - RECEPTIVE_ANGLE_DEG - 1)// delta camera azimuth
                .build(100);
    }

    public static Stream<Arguments> dataUpdateNewMarker() {
        // void testUpdateNewMarker(int cameraDeg, int targetDeg, int echoDeg, double echoDistance) {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // cameraDeg
                .uniform(-15, 15) // targetDeg
                .uniform(-90, 90) // echoDeg
                .uniform(0.5, 2.5) // echoDistance
                .build(100);
    }

    static Stream<Arguments> dateNoUpdateExistingMarker() {
//        void testUpdateExistingMarker(int cameraDeg, int echoDeg, int markerDeg, double markerDistance) {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // sensorDeg
                .uniform(16, 344) // markerDeg
                .uniform(0.5, 3) // markerDistance
                .build(100);
    }

    static Stream<Arguments> dateUpdateExistingMarker() {
//        void testUpdateExistingMarker(int cameraDeg, int targetDeg, int echoDeg, double echoDistance, int markerDeg, double markerDistance) {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // cameraDeg
                .uniform(-15, 15) // targetDeg
                .uniform(-30, 30) // echoDeg
                .uniform(0.5, 3) // echoDistance
                .uniform(-30, 30) // markerDeg
                .uniform(0.5, 3) // markerDistance
                .build(100);
    }

    static double gamma(double dt, double decay) {
        return Math.expm1(-(double) dt / decay) + 1;
    }

    MarkerLocator locator;

    @BeforeEach
    void setUp() {
        locator = new MarkerLocator(DECAY_TIME, DECAY_TIME, CORRELATION_INTERVAL, 1, MARKER_SIZE);
    }

    /**
     * update the map by cleaning an existing label marker not equal to the recognised one
     * and echo beyond the marker
     * and the camera direction in receptive area
     */
    @ParameterizedTest(name = "[{index} marker R{0} D{1} camera {2} DEG ping {3}")
    @MethodSource("dataEchoCameraClean")
    void testEchoCameraClean(int markerDeg, double markerDistance, int deltaCameraAzimuth, double relativeEchoDistance) {
        // Given a Marker locator
        // And a correlated camera event
        double echoDistance = (MAX_DISTANCE - markerDistance) * relativeEchoDistance + markerDistance;
        int cameraAzimuth = markerDeg + deltaCameraAzimuth;
        CorrelatedCameraEvent event = createCorrelateEvent(T4, LABEL_B, 0, T3, echoDistance, 0, cameraAzimuth);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(markerDeg, markerDistance)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, not(hasKey(LABEL_A)));
        assertThat(map, hasKey(LABEL_B));
    }

    /**
     * update the map by cleaning an existing label marker not equal to the recognised one
     * and echo beyond the marker
     * and the camera direction in receptive area
     */
    @ParameterizedTest(name = "[{index} marker R{0} D{1} camera {2} DEG ping {3}")
    @MethodSource("dataEchoCameraNoClean")
    void testEchoCameraNoClean(int markerDeg, double markerDistance, int deltaCameraAzimuth, double relativeEchoDistance) {
        // Given a Marker locator
        // And a correlated camera event
        double echoDistance = (MAX_DISTANCE - markerDistance) * relativeEchoDistance + markerDistance;
        int cameraAzimuth = markerDeg + deltaCameraAzimuth;
        CorrelatedCameraEvent event = createCorrelateEvent(T4, LABEL_B, 0, T3, echoDistance, 0, cameraAzimuth);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(markerDeg, markerDistance)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, hasKey(LABEL_A));
        assertThat(map, hasKey(LABEL_B));
    }

    /**
     * update the map by cleaning an existing label marker
     * and unknown qrcode
     * and echo beyond the marker
     * and the camera direction in receptive area
     */
    @ParameterizedTest(name = "[{index} marker R{0} D{1} camera {2} DEG ping {3}")
    @MethodSource("dataEchoCameraClean")
    void testEchoUnknownCameraClean(int markerDeg, double markerDistance, int deltaCameraAzimuth, double relativeEchoDistance) {
        // Given a Marker locator
        // And a correlated camera event
        double echoDistance = (MAX_DISTANCE - markerDistance) * relativeEchoDistance + markerDistance;
        int cameraAzimuth = markerDeg + deltaCameraAzimuth;
        CorrelatedCameraEvent event = createCorrelateEvent(T4, UNKNOWN_QR_CODE, 0, T3, echoDistance, 0, cameraAzimuth);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(markerDeg, markerDistance)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should not exist
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    /**
     * update the map by cleaning an existing label marker
     * and unknown qrcode
     * and echo beyond the marker
     * and the camera direction in receptive area
     */
    @ParameterizedTest(name = "[{index} marker R{0} D{1} camera {2} DEG ping {3}")
    @MethodSource("dataEchoCameraNoClean")
    void testEchoUnknownCameraNoClean(int markerDeg, double markerDistance, int deltaCameraAzimuth, double relativeEchoDistance) {
        // Given a Marker locator
        // And a correlated camera event
        double echoDistance = (MAX_DISTANCE - markerDistance) * relativeEchoDistance + markerDistance;
        int cameraAzimuth = markerDeg + deltaCameraAzimuth;
        CorrelatedCameraEvent event = createCorrelateEvent(T4, UNKNOWN_QR_CODE, 0, T3, echoDistance, 0, cameraAzimuth);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(markerDeg, markerDistance)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, hasKey(LABEL_A));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} marker R{0} D{1} camera dAz {2} DEG")
    @MethodSource("dataNoEchoUnknownClean")
    void testNoEchoUnknownClean(int markerDeg, double distance, int deltaCameraAzimuth) {
        // Given a Marker locator
        // And a correlated camera event
        int cameraAzimuth = markerDeg + deltaCameraAzimuth;
        CorrelatedCameraEvent event = createCorrelateEvent(T4, UNKNOWN_QR_CODE, 0, T3, 0, 0, cameraAzimuth);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(markerDeg, distance)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, not(hasKey(LABEL_A)));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} marker R{0} D{1} camera dAz {2} DEG")
    @MethodSource("dataNoEchoUnknownNoClean")
    void testNoEchoUnknownNoClean(int markerDeg, double distance, int deltaCameraAzimuth) {
        // Given a Marker locator
        // And a correlated camera event
        int cameraAzimuth = markerDeg + deltaCameraAzimuth;
        CorrelatedCameraEvent event = createCorrelateEvent(T4, UNKNOWN_QR_CODE, 0, T3, 0, 0, cameraAzimuth);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(markerDeg, distance)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, hasKey(LABEL_A));
    }

    /**
     * update map with an existing label marker
     */
    @ParameterizedTest(name = "[{index}] Robot R{0}, sensor {1} DEG, marker {2} DEG D{3}")
    @MethodSource("dateNoUpdateExistingMarker")
    void testNoUpdateExistingMarker(int robotDeg, int sensorDeg, int markerDeg, double markerDistance) {
        // Given a correlated camera event with robot directed to robotDeg relative the ambient

        // and camera directed to sensorDeg relative robot

        // And existing marker at markerDistance directed to markerDeg relative the robot
        CorrelatedCameraEvent event = createCorrelateEvent(T4, UNKNOWN_QR_CODE, 0, T3, 0, sensorDeg, robotDeg);
        // And a map with existing marker located at markerDistance directed to markerDeg relative the camera
        Point2D markerLocation = Complex.fromDeg(robotDeg + markerDeg + sensorDeg).at(ROBOT_LOCATION, markerDistance);
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, new LabelMarker(LABEL_A, markerLocation, 1, T0, T0)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasKey(LABEL_A));

        // And the marker should be labelled "A"
        LabelMarker marker = map.get(LABEL_A);
        assertEquals(LABEL_A, marker.label());
        // And should have proxy message markerTime
        assertEquals(T0, marker.markerTime());

        // And should be located in the middle between old location and new location
        assertThat(marker.location(), pointCloseTo(markerLocation, MM_1));
    }

    /**
     * update map with an existing label marker
     */
    @ParameterizedTest(name = "[{index}] Robot R{0}, target {1} DEG, proxy {2} DEG, echo D{3}, marker {4} DEG D{5}")
    @MethodSource("dateUpdateExistingMarker")
    void testUpdateExistingMarker(int robotDeg, int targetDeg, int proxyDeg, double echoDistance,
                                  int markerDeg, double markerDistance) {
        // Given a correlated camera event with robot directed to robotDeg relative the ambient

        // and camera directed to proxyDeg relative to robot

        // and marker directed to targetDeg relative the camera
        CorrelatedCameraEvent event = createCorrelateEvent(T4, LABEL_A, targetDeg, T3, echoDistance, proxyDeg, robotDeg);
        // And a map with existing marker located at markerDistance directed to markerDeg relative the camera
        Point2D markerLocation = Complex.fromDeg(robotDeg + markerDeg + proxyDeg).at(ROBOT_LOCATION, markerDistance);
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, new LabelMarker(LABEL_A, markerLocation, 1, T0, T0)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasKey(LABEL_A));

        // And the marker should be labelled "A"
        LabelMarker marker = map.get(LABEL_A);
        assertEquals(LABEL_A, marker.label());
        // And should have proxy message markerTime
        assertEquals(T4, marker.markerTime());

        // And should be located in the middle between old location and new location
        Point2D newLocation = Complex.fromDeg(robotDeg + targetDeg + proxyDeg).at(ROBOT_LOCATION, echoDistance + MARKER_SIZE / 2);

        double gamma = gamma(T4 - T0, DECAY_TIME);
        double notGamma = 1 - gamma;
        Point2D expectedLocation = new Point2D.Double(
                markerLocation.getX() * gamma + newLocation.getX() * notGamma,
                markerLocation.getY() * gamma + newLocation.getY() * notGamma
        );
        assertThat(marker.location(), pointCloseTo(expectedLocation, MM_1));
    }

    /**
     * update map with a new label marker
     */
    @ParameterizedTest(name = "[{index}] robot R{0} target {1} DEG, camera {2} DEG, echo D{3}")
    @MethodSource("dataUpdateNewMarker")
    void testUpdateNewMarker(int robotDeg, int targetDeg, int echoDeg, double echoDistance) {
        // Given a correlated camera event with camera directed to cameraDeg relative the ambient

        // and camera directed to deltaCameraAzimuth relative to targetDeg relative the camera direction

        // and echo at echoDistance directed to echoDeg relative the robot direction

        // (the result should not depend on)
        CorrelatedCameraEvent event = createCorrelateEvent(T4, LABEL_A, targetDeg, T3, echoDistance, echoDeg, robotDeg);

        // When update by event
        Map<String, LabelMarker> map = locator.update(Map.of(), event, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasKey(LABEL_A));

        // And the marker should be labelled "A"
        LabelMarker marker = map.get(LABEL_A);
        assertEquals(LABEL_A, marker.label());
        // And should have proxy message markerTime
        assertEquals(T4, marker.markerTime());

        // And should be located at echo location
        Point2D expected = Complex.fromDeg(robotDeg + targetDeg + echoDeg).at(ROBOT_LOCATION, echoDistance + MARKER_SIZE / 2);
        assertThat(marker.location(), pointCloseTo(expected, MM_1));
    }

    /**
     * update map with uncorrelated signals
     */
    @ParameterizedTest(name = "[{index}] echo R{0}")
    @MethodSource("dateUpdateExistingMarker")
    void updateUncorrelatedTest(int echoDeg) {
        // Given a Marker locator
        // And a map with existing marker
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, new LabelMarker(LABEL_A, POINT0, 1, T0, T0)
        );
        // And a camera event
        CorrelatedCameraEvent event = createCorrelateEvent(0, T2, echoDeg);

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, ROBOT_SPEC);

        // Then the map should not change
        assertSame(map0, map);
    }
}