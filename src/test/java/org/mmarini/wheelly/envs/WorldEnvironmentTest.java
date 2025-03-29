/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.envs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.lang.Math.PI;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.apis.MockRobot.MAX_RADAR_DISTANCE;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorldEnvironmentTest {

    public static final int NUM_RADAR_SECTORS = 4;
    public static final double DISTANCE = 2;
    public static final int NUM_DIRECTION_VALUES = 4;
    public static final int NUM_SPEED_VALUES = 3;
    public static final int NUM_SENSOR_VALUES = 4;
    public static final int GRID_SIZE = 5;
    private static final int GRID_MAP_SIZE = 5;
    public static final double MARKER_SIZE = 0.3;
    public static final WorldModelSpec WORLD_SPEC = new WorldModelSpec(ROBOT_SPEC, NUM_RADAR_SECTORS, GRID_MAP_SIZE, MARKER_SIZE);

    private PolarMap polarMap;
    private WorldEnvironment env;
    private WorldModel worldModel;

    @Test
    void getActionsTest() {
        Map<String, SignalSpec> actions = env.actionSpec();
        assertThat(actions, hasKey("move"));
        assertThat(actions, hasKey("sensorAction"));

        assertEquals(new IntSignalSpec(new long[]{1}, NUM_DIRECTION_VALUES * NUM_SPEED_VALUES), actions.get("move"));
        assertEquals(new IntSignalSpec(new long[]{1}, NUM_SENSOR_VALUES), actions.get("sensorAction"));
    }

    @BeforeEach
    void setUp() {
        RobotStatus robotStatus = RobotStatus.create(ROBOT_SPEC, x -> 12d);

        RadarMap radarMap = RadarMap.empty(new GridTopology(new Point2D.Float(), 11, 11, 0.2));

        CircularSector[] sectors = IntStream.range(0, NUM_RADAR_SECTORS)
                .mapToObj(i -> CircularSector.unknownSector())
                .toArray(CircularSector[]::new);
        this.polarMap = new PolarMap(sectors, new Point2D.Double(), Complex.DEG0);

        GridMap gridMap = GridMap.create(radarMap, new Point2D.Double(), Complex.DEG0, GRID_MAP_SIZE);

        this.worldModel = new WorldModel(WORLD_SPEC, robotStatus, radarMap, Map.of(), polarMap, gridMap, null);

        WorldModellerConnector controller = mock();
        when(controller.worldModelSpec()).thenReturn(WORLD_SPEC);

        this.env = new WorldEnvironment(NUM_SPEED_VALUES, NUM_DIRECTION_VALUES, NUM_SENSOR_VALUES, List.of("A"));
        this.env.connect(controller);
    }

    @Test
    void isHaltTest() {
        Map<String, Signal> actions = Map.of(
                "move", IntSignal.create(7),
                "sensorAction", IntSignal.create(0)
        );
        assertTrue(env.isHalt(actions));
    }

    @ParameterizedTest
    @CsvSource({
            "0, -180",
            "1, -180",
            "2, -180",
            "3, -90",
            "4, -90",
            "5, -90",
            "6, 0",
            "7, 0",
            "8, 0",
            "9, 90",
            "10, 90",
            "11, 90",
    })
    void moveDirectionTest(int action, int expected) {
        Map<String, Signal> actions = Map.of("move", IntSignal.create(action));
        int dir = env.deltaDir(actions).toIntDeg();
        assertEquals(expected, dir);
    }

    @ParameterizedTest
    @CsvSource({
            "0, -60",
            "1, 0",
            "2, 60",
            "3, -60",
            "4, 0",
            "5, 60",
            "6, -60",
            "7, 0",
            "8, 60",
            "9, -60",
            "10, 0",
            "11, 60",
    })
    void moveSpeedTest(int action, int expected) {
        Map<String, Signal> actions = Map.of("move", IntSignal.create(action));
        int speed = env.speed(actions);
        assertEquals(expected, speed);
    }

    void setEmpty(int i, double distance) {
        Point2D location = Complex.fromRad(i * 2 * PI / NUM_RADAR_SECTORS).at(new Point2D.Double(), distance);
        polarMap.sectors()[i] = CircularSector.empty(System.currentTimeMillis(), location);
    }

    void setHindered(int i, double distance) {
        Point2D location = Complex.fromRad(i * 2 * PI / NUM_RADAR_SECTORS).at(new Point2D.Double(), distance);
        polarMap.sectors()[i] = CircularSector.hindered(System.currentTimeMillis(), location);
    }

    @Test
    void signalsTest() {
        // Given a polar robot environment
        // And a current status with a polar map
        setEmpty(1, DISTANCE);
        setHindered(2, DISTANCE);

        // When get the states
        State state = env.state(worldModel);

        // Then signal ...
        assertNotNull(state);
        Map<String, Signal> signals = state.signals();

        assertThat(signals, hasKey("sectorStates"));
        assertThat(signals, hasKey("sectorDistances"));
        // And states ...
        assertEquals(ArraySignal.create(new long[]{4}, 0, 1, 2, 0),
                signals.get("sectorStates"));
        // And distance
        assertEquals(ArraySignal.create(new long[]{4}, 0f, 0f, (float) DISTANCE, 0),
                signals.get("sectorDistances"));
    }

    @Test
    void stateSpecTest() {
        // Given a world environment
        // And a mock world modeller
        WorldModellerConnector connector = mock();

        WorldModelSpec worldModelSpec = WORLD_SPEC;
        when(connector.worldModelSpec()).thenReturn(worldModelSpec);

        // When connect the connector
        env.connect(connector);
        // And get the state spec
        Map<String, SignalSpec> states = env.stateSpec();

        // Then the state should have sectorState and sectorDistances entries
        assertEquals(new IntSignalSpec(new long[]{NUM_RADAR_SECTORS}, 3),
                states.get("sectorStates"));
        assertEquals(new FloatSignalSpec(new long[]{NUM_RADAR_SECTORS}, 0f, (float) MAX_RADAR_DISTANCE),
                states.get("sectorDistances"));
    }
}