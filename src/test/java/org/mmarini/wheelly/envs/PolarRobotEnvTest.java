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
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;
import java.util.Map;
import java.util.stream.IntStream;

import static java.lang.Math.PI;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PolarRobotEnvTest {

    public static final int NUM_RADAR_SECTORS = 4;
    public static final double DISTANCE = 2;

    static PolarRobotEnv create() {
        RobotApi robot = mock();
        RobotControllerApi controller = new RobotController(robot, 1000, 1000, 1000, 1000, 1000, 1, i -> 0);

        RadarMap radarMap = mock();
        return PolarRobotEnv.create(controller,
                (s0, a, s1) -> 0, 4, 4, 4, NUM_RADAR_SECTORS,
                0.2, 3, radarMap);
    }

    private PolarMap polarMap;
    private PolarRobotEnv env;

    @Test
    void getSignalsTest() {
        // Given a polar robot environment
        // And a current status with polar map
        setEmpty(1, DISTANCE);
        setHindered(2, DISTANCE);
        setLabeled(3, DISTANCE);
        RobotStatus status = RobotStatus.create(x -> 0);

        PolarRobotState currentState = PolarRobotState.create(status, mock(), polarMap, 3);
        env.setCurrentState(currentState);

        // When get the states
        Map<String, Signal> signals = env.getCurrentState().signals();

        // Then signal ...
        assertThat(signals, hasKey("sectorStates"));
        assertThat(signals, hasKey("sectorDistances"));
        // And states ...
        assertEquals(ArraySignal.create(new long[]{4}, 0, 1, 2, 3),
                signals.get("sectorStates"));
        // And distance
        assertEquals(ArraySignal.create(new long[]{4}, 0f, 0f, (float) DISTANCE, (float) DISTANCE),
                signals.get("sectorDistances"));
    }

    @Test
    void getStateTest() {
        // Given a polar robot environment
        // When get the states
        Map<String, SignalSpec> states = env.getState();

        // Then the state should have sectorState and sectorDistances entries
        assertEquals(new IntSignalSpec(new long[]{NUM_RADAR_SECTORS}, 4),
                states.get("sectorStates"));
        assertEquals(new FloatSignalSpec(new long[]{NUM_RADAR_SECTORS}, 0, 3),
                states.get("sectorDistances"));
    }

    void setEmpty(int i, double distance) {
        Point2D location = Complex.fromRad(i * 2 * PI / NUM_RADAR_SECTORS).at(new Point2D.Double(), distance);
        polarMap.sectors()[i] = CircularSector.empty(System.currentTimeMillis(), location);
    }

    void setHindered(int i, double distance) {
        Point2D location = Complex.fromRad(i * 2 * PI / NUM_RADAR_SECTORS).at(new Point2D.Double(), distance);
        polarMap.sectors()[i] = CircularSector.hindered(System.currentTimeMillis(), location);
    }

    void setLabeled(int i, double distance) {
        Point2D location = Complex.fromRad(i * 2 * PI / NUM_RADAR_SECTORS).at(new Point2D.Double(), distance);
        polarMap.sectors()[i] = CircularSector.labeled(System.currentTimeMillis(), location);
    }

    @BeforeEach
    void setUp() {
        CircularSector[] sectors = IntStream.range(0, NUM_RADAR_SECTORS)
                .mapToObj(i -> CircularSector.unknownSector())
                .toArray(CircularSector[]::new);
        polarMap = new PolarMap(sectors, new Point2D.Double(), Complex.DEG0);
        env = create();
    }
}