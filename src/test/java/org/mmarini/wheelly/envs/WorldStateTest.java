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
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;

class WorldStateTest {

    public static final int GRID_MAP_SIZE = 5;
    public static final double GRID_SIZE = 0.5;
    public static final int SECTOR_NUMBERS = 4;
    public static final int MAP_DEG = 90;
    private static final int RADAR_SIZE = 11;

    @NotNull
    private static RadarMap createRadarMap() {
        return RadarMap.empty(new GridTopology(new Point2D.Float(), RADAR_SIZE, RADAR_SIZE, GRID_SIZE))
                .updateCellAt(0, 0, cell -> cell.addEchogenic(100, 0));
    }

    private static PolarMap createPolarMap() {
        return PolarMap.create(SECTOR_NUMBERS);
    }

    private static RobotStatus createRobotStatus(double robotX, double robotY, int robotDeg) {
        return RobotStatus.create(ROBOT_SPEC, x -> 12)
                .setLocation(new Point2D.Double(robotX, robotY))
                .setDirection(Complex.fromDeg(robotDeg));
    }

    static WorldModel createWorldModel(RobotStatus status) {
        RadarMap radarMap = createRadarMap();
        PolarMap polarMap = createPolarMap();
        GridMap gridMap = GridMap.create(radarMap, status.location(), status.direction(), GRID_MAP_SIZE);
        return new WorldModel(status, radarMap, Map.of(), polarMap, gridMap, null, null, false);
    }

    @Test
    void createTest() {
        // Given a mock status
        WorldModel model = createWorldModel(createRobotStatus(0, 0, 0));

        // When create a state from world state
        WorldState state = WorldState.create(model);

        // Then ...
        assertSame(model, state.model());
    }

    @ParameterizedTest
    @ValueSource(ints = {
            46, 90, 133
    })
    void signalsTest(int robotDeg) {
        // Given ...
        RobotStatus status = createRobotStatus(0, -0.5, robotDeg);
        WorldModel model = createWorldModel(status);
        WorldState state = WorldState.create(model);

        // When ...
        Map<String, Signal> signals = state.signals();

        // Then
        assertThat(signals, hasKey("cellStates"));
        INDArray cellStates = signals.get("cellStates").toINDArray();
        assertThat(cellStates, matrixCloseTo(new long[]{25}, 1e-3,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 2, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        ));

        // And
        assertThat(signals, hasKey("robotMapDir"));
        int dir = signals.get("robotMapDir").getInt(0);
        assertEquals(robotDeg - MAP_DEG, dir);
    }

    @Test
    void specTest() {
        // Given ...
        WorldModel model = createWorldModel(createRobotStatus(0, 0, 0));
        WorldState state = WorldState.create(model);

        // When ...
        Map<String, SignalSpec> spec = state.spec();

        // Then ...
        assertThat(spec, hasKey("cellStates"));

        SignalSpec cellStates = spec.get("cellStates");
        assertThat(cellStates, instanceOf(IntSignalSpec.class));
        assertArrayEquals(new long[]{GRID_MAP_SIZE * GRID_MAP_SIZE}, cellStates.shape());
        assertEquals(4, ((IntSignalSpec) cellStates).numValues());
    }
}