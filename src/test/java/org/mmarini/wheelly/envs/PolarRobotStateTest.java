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

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.awt.geom.Point2D;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mockito.Mockito.mock;

class PolarRobotStateTest {

    public static final int RADAR_SIZE = 11;
    public static final double GRID_SIZE = 0.5;
    public static final int MAP_SIZE = 5;
    public static final double MAX_RADAR_DISTANCE = 3;
    public static final int SECTOR_NUMBERS = 4;

    @NotNull
    private static RadarMap createRadarMap() {
        return RadarMap.create(new Point2D.Float(), RADAR_SIZE, RADAR_SIZE, GRID_SIZE,
                        0, 0, 0, 0, 0, 0, Complex.DEG0)
                .updateCellAt(0, 0, cell -> cell.addEchogenic(100, 0));
    }

    private static RobotStatus createStatus(double robotX, double robotY, int robotDeg) {
        return RobotStatus.create(x -> 12)
                .setLocation(new Point2D.Double(robotX, robotY))
                .setDirection(Complex.fromDeg(robotDeg));
    }

    @ParameterizedTest
    @CsvSource({
            // robotX,robotY, robotDeg, expX, expY, directionDeg
            "0,0, 0, 0,0, 0",
            "1.3,1.8, 44, 1.5,2, 0",
            "1.3,1.8, 46, 1.5,2, 90",
            "1.3,1.8, 134, 1.5,2, 90",
            "1.3,1.8, 136, 1.5,2, -180",
            "1.3,1.8, 224, 1.5,2, -180",
            "1.3,1.8, 226, 1.5,2, -90",
            "1.3,1.8, 314, 1.5,2, -90",
            "0,0, 316, 0,0, 0",
    })
    void createGridMapTest(double robotX, double robotY, int robotDeg, double expX, double expY, int expDeg) {
        // Given ...
        RobotStatus status = createStatus(robotX, robotY, robotDeg);
        RadarMap radarMap = createRadarMap();
        PolarRobotState state0 = PolarRobotState.create(status, radarMap, PolarMap.create(SECTOR_NUMBERS), MAX_RADAR_DISTANCE, MAP_SIZE);

        // When ...
        PolarRobotState state = state0.createGridMap();

        // Then ...
        assertSame(radarMap, state.radarMap());
        assertSame(status, state.robotStatus());

        // And
        GridMap map = state.gridMap();
        assertNotNull(map);
        assertEquals(new Point2D.Double(expX, expY), map.center());
        assertEquals(expDeg, map.direction().toIntDeg());
    }

    @Test
    void createTest() {
        // Given ...
        RobotStatus status = mock();
        RadarMap radarMap = createRadarMap();

        // When ...
        PolarRobotState state = PolarRobotState.create(status, radarMap, PolarMap.create(SECTOR_NUMBERS), MAX_RADAR_DISTANCE, MAP_SIZE);

        // Then ...
        assertSame(radarMap, state.radarMap());
        assertSame(status, state.robotStatus());
    }

    @Test
    void signalsTest() {
        // Given ...
        RobotStatus status = createStatus(0, -0.5, 90);
        RadarMap radarMap = createRadarMap();
        PolarRobotState state = PolarRobotState.create(status, radarMap, PolarMap.create(SECTOR_NUMBERS), MAX_RADAR_DISTANCE, MAP_SIZE).createGridMap();

        // When ...
        Map<String, Signal> signals = state.signals();

        // Then
        assertThat(signals, hasKey("cellStates"));
        INDArray cellStates = signals.get("cellStates").toINDArray();
        assertThat(cellStates, matrixCloseTo(new long[]{25}, 1e-3,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 3, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        ));
    }

    @Test
    void specTest() {
        // Given ...
        RobotStatus status = mock();
        RadarMap radarMap = createRadarMap();
        PolarRobotState state = PolarRobotState.create(status, radarMap, PolarMap.create(SECTOR_NUMBERS), MAX_RADAR_DISTANCE, MAP_SIZE);

        // When ...
        Map<String, SignalSpec> spec = state.spec();

        // Then ...
        assertThat(spec, hasKey("cellStates"));

        SignalSpec cellStates = spec.get("cellStates");
        assertThat(cellStates, instanceOf(IntSignalSpec.class));
        assertArrayEquals(new long[]{25}, cellStates.shape());
        assertEquals(5, ((IntSignalSpec) cellStates).numValues());
    }
}