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
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mmarini.ArgumentsGenerator.createStream;
import static org.mmarini.ArgumentsGenerator.uniform;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.CameraEvent.UNKNOWN_QR_CODE;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.WheellyProxyMessage.DISTANCE_SCALE;

class MarkerLocatorTest {
    public static final int NUM_TESTS = 10;

    public static final double MM_1 = 1e-3;
    public static final long CORRELATION_INTERVAL = 500;
    public static final double DECAY_TIME = 600;
    public static final double MAX_DISTANCE = 3;
    public static final int RECEPTIVE_ANGLE_DEG = 15;

    public static final long T0 = 100;
    public static final long T1 = T0 + CORRELATION_INTERVAL;
    public static final long T2 = T0 + CORRELATION_INTERVAL + 1;
    public static final long DT = 600;
    public static final long T3 = T0 + DT - CORRELATION_INTERVAL;
    public static final long T4 = T0 + DT;
    public static final double ECHO_DISTANCE = 1.2;
    public static final long ECHO_DELAY = Math.round(ECHO_DISTANCE / DISTANCE_SCALE);
    public static final long LONG_ECHO = Math.round(MAX_DISTANCE / DISTANCE_SCALE) + 1;

    public static final double GAMMA = Math.exp(-(double) DT / DECAY_TIME);
    public static final double NOT_GAMMA = 1 - GAMMA;
    public static final Point2D ROBOT_LOCATION = new Point2D.Double(1, 1);
    public static final Point2D POINT0 = new Point2D.Double(2, 2);
    public static final long SEED = 1234;
    public static final double MARKER_SIZE = 0.2;
    public static final String LABEL_A = "A";
    public static final String LABEL_B = "B";

    static CameraEvent createCamera(long time, String label) {
        return new CameraEvent(time, label, 0, 0, new Point2D[0]);
    }

    static LabelMarker createMarkerAt(int echoDeg, double distance, int markerDeg) {
        Point2D location = locateMarker(Complex.fromDeg(markerDeg).add(Complex.fromDeg(echoDeg)), distance);
        return new LabelMarker(LABEL_A, location, 1, T0, T0);
    }

    static WheellyProxyMessage createProxy(long time, long delay, int direction) {
        return new WheellyProxyMessage(time, time, time, 0, delay,
                Math.round(ROBOT_LOCATION.getX() / DISTANCE_PER_PULSE),
                Math.round(ROBOT_LOCATION.getX() / DISTANCE_PER_PULSE),
                direction);
    }

    public static Stream<Arguments> dataCleaningArea() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179), // echoDeg
                uniform(0.1, MAX_DISTANCE), // distance
                uniform(-RECEPTIVE_ANGLE_DEG + 1, RECEPTIVE_ANGLE_DEG - 1)// markerDeg
        );
    }

    public static Stream<Arguments> dataCleaningEchoArea() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179), // echoDeg
                uniform(0.1, ECHO_DISTANCE + MARKER_SIZE / 2 - MM_1), // marker distance
                uniform(-RECEPTIVE_ANGLE_DEG + 1, RECEPTIVE_ANGLE_DEG - 1)// markerDeg
        );
    }

    static Stream<Arguments> dataEchoDeg() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179) // echoDeg
        );
    }

    static Stream<Arguments> dataNoCleanArea() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179), // echoDeg
                uniform(MAX_DISTANCE + MM_1, 10d), // distance
                uniform(0, 359)// markerDeg
        );
    }

    static Stream<Arguments> dataNoCleanEchoArea() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179), // echoDeg
                uniform(ECHO_DISTANCE + MARKER_SIZE / 2 + MM_1, 10d), // distance
                uniform(0, 359)// markerDeg
        );
    }

    static Stream<Arguments> dataNoCleanEchoSector() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179), // echoDeg
                uniform(0.1, ECHO_DISTANCE), // distance
                uniform(RECEPTIVE_ANGLE_DEG + 1, 360 - RECEPTIVE_ANGLE_DEG - 1)// markerDeg
        );
    }

    static Stream<Arguments> dataNoCleanSector() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(NUM_TESTS, SEED,
                uniform(-180, 179), // echoDeg
                uniform(0.1, MAX_DISTANCE), // distance
                uniform(RECEPTIVE_ANGLE_DEG, 360 - RECEPTIVE_ANGLE_DEG)// markerDeg
        );
    }

    static Point2D locateMarker(Complex direction, double distance) {
        return direction.at(ROBOT_LOCATION, distance);
    }
    MarkerLocator locator;

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} D{1} marker R{2}")
    @MethodSource("dataCleaningEchoArea")
    void cleanEchoCameraTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T3, ECHO_DELAY, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T4, LABEL_B);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(echoDeg, distance, markerDeg)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, not(hasKey(LABEL_A)));
        assertThat(map, hasKey(LABEL_B));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} D{1} marker {2} R{3}")
    @MethodSource({"dataNoCleanEchoArea", "dataNoCleanEchoSector"})
    void cleanEchoCameraUnmatchedTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T4, ECHO_DELAY, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T3, LABEL_B);
        // And a map with existing marker located in the cleaning area
        LabelMarker marker = createMarkerAt(echoDeg, distance, markerDeg);
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, marker
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasEntry(
                equalTo(LABEL_A),
                sameInstance(marker)
        ));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} D{1} marker R{2}")
    @MethodSource("dataCleaningEchoArea")
    void cleanEchoUnknownTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a camera event
        CameraEvent event = createCamera(T4, UNKNOWN_QR_CODE);
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T3, ECHO_DELAY, echoDeg);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(echoDeg, distance, markerDeg)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, not(hasKey(LABEL_A)));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} D{1} marker {2} R{3}")
    @MethodSource({"dataNoCleanEchoArea", "dataNoCleanEchoSector"})
    void cleanEchoUnknownUnmatchedTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T4, ECHO_DELAY, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T3, UNKNOWN_QR_CODE);
        // And a map with existing marker located in the cleaning area
        LabelMarker marker = createMarkerAt(echoDeg, distance, markerDeg);
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, marker
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasEntry(
                equalTo(LABEL_A),
                sameInstance(marker)
        ));
    }

    /**
     * update the map by cleaning an existing label marker for long echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} marker D{1} R{2}")
    @MethodSource("dataCleaningArea")
    void cleanLongEchoTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T3, LONG_ECHO, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T4, LABEL_A);
        // And a map with existing marker in cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(echoDeg, distance, markerDeg)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, not(hasKey(LABEL_A)));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} marker D{1} R{2}")
    @MethodSource({"dataNoCleanArea", "dataNoCleanSector"})
    void cleanLongEchoUnmatchedTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a camera event
        CameraEvent event = createCamera(T0, LABEL_A);
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T4, LONG_ECHO, echoDeg);
        // And a map with existing marker located in the cleaning area
        Complex direction = Complex.fromDeg(markerDeg);
        Point2D location = locateMarker(direction, distance);
        LabelMarker marker = new LabelMarker(LABEL_A, location, 1, T0, T0);
        Map<String, LabelMarker> map0 = Map.of(LABEL_A, marker);

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasEntry(
                equalTo(LABEL_A),
                sameInstance(marker)
        ));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} marker D{1} R{2}")
    @MethodSource("dataCleaningArea")
    void cleanNoEchoTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T3, 0, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T4, LABEL_A);
        // And a map with existing marker located in the cleaning area
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, createMarkerAt(echoDeg, distance, markerDeg)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should not exist
        assertThat(map, not(hasKey(LABEL_A)));
    }

    /**
     * update the map by cleaning an existing label marker for no echo
     */
    @ParameterizedTest(name = "[{index} echo R{0} marker D{1} R{2}")
    @MethodSource({"dataNoCleanArea", "dataNoCleanSector"})
    void cleanNoEchoUnmatchedTest(int echoDeg, double distance, int markerDeg) {
        // Given a Marker locator
        // And a camera event
        CameraEvent event = createCamera(T0, LABEL_A);
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T4, 0, echoDeg);
        // And a map with existing marker located in the cleaning area
        Complex direction = Complex.fromDeg(markerDeg);
        Point2D location = locateMarker(direction, distance);
        LabelMarker marker = new LabelMarker(LABEL_A, location, 1, T0, T0);
        Map<String, LabelMarker> map0 = Map.of(LABEL_A, marker);

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasEntry(
                equalTo(LABEL_A),
                sameInstance(marker)
        ));
    }

    @BeforeEach
    void setUp() {
        locator = new MarkerLocator(DECAY_TIME, DECAY_TIME, CORRELATION_INTERVAL, MARKER_SIZE);
    }

    /**
     * update map with an existing label marker
     */
    @ParameterizedTest(name = "[{index}] echo R{0}")
    @MethodSource("dataEchoDeg")
    void updateExistingMarkerTest(int echoDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T3, ECHO_DELAY, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T4, LABEL_A);
        // And a map with existing marker
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, new LabelMarker(LABEL_A, POINT0, 1, T0, T0)
        );

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasKey(LABEL_A));

        // And the marker should be labelled "A"
        LabelMarker marker = map.get(LABEL_A);
        assertEquals(LABEL_A, marker.label());
        // And should have proxy message markerTime
        assertEquals(T4, marker.markerTime());

        // And should be located at echo location
        Point2D echoLocation = proxy.echoDirection().at(ROBOT_LOCATION, ECHO_DISTANCE + MARKER_SIZE / 2);
        Point2D expectedLocation = new Point2D.Double(
                POINT0.getX() * GAMMA + echoLocation.getX() * NOT_GAMMA,
                POINT0.getY() * GAMMA + echoLocation.getY() * NOT_GAMMA
        );
        assertThat(marker.location(), pointCloseTo(expectedLocation, MM_1));
    }

    /**
     * update map with a new label marker
     */
    @ParameterizedTest(name = "[{index}] echo R{0}")
    @MethodSource("dataEchoDeg")
    void updateNewMarkerTest(int echoDeg) {
        // Given a Marker locator
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T0, ECHO_DELAY, echoDeg);
        // And a camera event
        CameraEvent event = createCamera(T1, LABEL_A);

        // When update by event
        Map<String, LabelMarker> map = locator.update(Map.of(), event, proxy, ROBOT_SPEC);

        // Then the marker should exist
        assertThat(map, hasKey(LABEL_A));

        // And the marker should be labelled "A"
        LabelMarker marker = map.get(LABEL_A);
        assertEquals(LABEL_A, marker.label());
        // And should have proxy message markerTime
        assertEquals(T1, marker.markerTime());

        // And should be located at echo location
        Point2D expectedLocation = proxy.echoDirection().at(ROBOT_LOCATION, ECHO_DISTANCE + MARKER_SIZE / 2);
        assertThat(marker.location(), pointCloseTo(expectedLocation, MM_1));
    }

    /**
     * update map with uncorrelated signals
     */
    @ParameterizedTest(name = "[{index}] echo R{0}")
    @MethodSource("dataEchoDeg")
    void updateUncorrelatedTest(int echoDeg) {
        // Given a Marker locator
        // And a map with existing marker
        Map<String, LabelMarker> map0 = Map.of(
                LABEL_A, new LabelMarker(LABEL_A, POINT0, 1, T0, T0)
        );
        // And a camera event
        CameraEvent event = new CameraEvent(T0, LABEL_A, 0, 0, new Point2D[0]);
        // And a correlated proxy message
        WheellyProxyMessage proxy = createProxy(T2, ECHO_DELAY, echoDeg);

        // When update by event
        Map<String, LabelMarker> map = locator.update(map0, event, proxy, ROBOT_SPEC);

        // Then the map should not change
        assertSame(map0, map);
    }
}