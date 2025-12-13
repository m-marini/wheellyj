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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.rl.envs.FloatSignalSpec;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;
import org.mmarini.wheelly.apis.WorldModelSpec;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_RADAR_DISTANCE;

class RLStateFunctionTest {

    public static final int GRID_MAP_SIZE = 5;
    public static final double GRID_SIZE = 0.5;
    public static final int SECTOR_NUMBERS = 4;
    public static final int MAP_DEG = 90;
    public static final List<String> MARKERS = List.of("A", "B");
    public static final WorldModelSpec WORLD_SPEC = new WorldModelSpec(DEFAULT_ROBOT_SPEC, SECTOR_NUMBERS, GRID_MAP_SIZE);
    private static final int RADAR_SIZE = 11;
    private static final long NUM_MARKERS = MARKERS.size();

    static WorldModelBuilder modelBuilder() {
        return new WorldModelBuilder()
                .radarSize(RADAR_SIZE, RADAR_SIZE)
                .gridMapSize(GRID_MAP_SIZE)
                .numSectors(SECTOR_NUMBERS)
                .addEchoCell(new Point2D.Double());
    }

    @ParameterizedTest(name = "[{index}] R{0} {1}@{2},{3} {4}@{5},{6}")
    @CsvSource({
            // robotdir, label,x,y, label,x,y, expState,expState, expDist,expDist, expDir,expDir
            "0, ?,0,1, ?,1,0, 0,0, 0,0, 0,0",
            "0, A,0,-1, ?,1,0, 1,0, 1,0, -180,0",
            "0, ?,0,1, B,1.5,0, 0,1, 0,1.5, 0,90",
            "0, ?,0,1, B,-1.5,0, 0,1, 0,1.5, 0,-90",
            "0, ?,0,1, B,1,1, 0,1, 0,1.414, 0,45",
            "0, A,0,1, B,1,0, 1,1, 1,1, 0,90",
            "0, A,0,3.001, B,3.001,0, 0,0, 0,0, 0,0",
    })
    void testMarkerSignals(int robotDeg, String labelA, double xa, double ya, String labelB, double xb, double yb,
                           float expStateA, float expStateB,
                           float expDistA, float expDistB,
                           int expDirA, int expDirB
    ) {
        // Given ...
        WorldModel model = modelBuilder()
                .robotDir(robotDeg)
                .addLabel(labelA, new Point2D.Double(xa, ya))
                .addLabel(labelB, new Point2D.Double(xb, yb))
                .build();
        RLStateFunction func = RLStateFunction.create(model.worldSpec(), MARKERS);

        // When ...
        Map<String, Signal> signals = func.signals(model);

        // Then
        Signal markerStates = signals.get("markerStates");
        assertNotNull(markerStates);
        assertThat(markerStates.toINDArray(), matrixCloseTo(new long[]{NUM_MARKERS}, 1e-3,
                expStateA, expStateB));

        // And
        Signal markerDistances = signals.get("markerDistances");
        assertNotNull(markerDistances);
        assertThat(markerDistances.toINDArray(), matrixCloseTo(new long[]{NUM_MARKERS}, 1e-3,
                expDistA, expDistB));
        // And
        Signal markerDirections = signals.get("markerDirections");
        assertNotNull(markerDirections);
        assertThat(markerDirections.toINDArray(), matrixCloseTo(new long[]{NUM_MARKERS}, 1e-3,
                (float) Math.toRadians(expDirA), (float) Math.toRadians(expDirB)));
    }

    @Test
    void testMarkersSpec() {
        // Given ...
        RLStateFunction func = RLStateFunction.create(WORLD_SPEC, MARKERS);

        // When ...
        Map<String, SignalSpec> spec = func.spec();

        // Then marker states spec should be int spec
        SignalSpec makerState = spec.get("markerStates");
        assertThat(makerState, instanceOf(IntSignalSpec.class));
        // And should have NUM_MARKERS dimension
        assertArrayEquals(new long[]{NUM_MARKERS}, makerState.shape());
        // And should have 2 possible values (0, 1)
        assertEquals(2, ((IntSignalSpec) makerState).numValues());

        // And marker distances spec should be int spec
        SignalSpec makerDistances = spec.get("markerDistances");
        assertThat(makerDistances, instanceOf(FloatSignalSpec.class));
        // And should have NUM_MARKERS dimension
        assertArrayEquals(new long[]{NUM_MARKERS}, makerDistances.shape());
        // And should have 0, MAX_DISTANCE value
        assertEquals(0, ((FloatSignalSpec) makerDistances).minValue());
        assertEquals(MAX_RADAR_DISTANCE, ((FloatSignalSpec) makerDistances).maxValue());

        // And marker distances spec should be int spec
        SignalSpec makerDirections = spec.get("markerDirections");
        assertThat(makerDirections, instanceOf(FloatSignalSpec.class));
        // And should have NUM_MARKERS dimension
        assertArrayEquals(new long[]{NUM_MARKERS}, makerDirections.shape());
        // And should have 0, MAX_DISTANCE value
        assertEquals((float) -Math.PI, ((FloatSignalSpec) makerDirections).minValue());
        assertEquals((float) Math.PI, ((FloatSignalSpec) makerDirections).maxValue());
    }

    @ParameterizedTest
    @ValueSource(ints = {
            46, 90, 133
    })
    void testSignals(int robotDeg) {
        // Given ...
        //RobotStatus status = createRobotStatus(0, -0.5, robotDeg);
        WorldModel model = new WorldModelBuilder()
                .radarSize(RADAR_SIZE, RADAR_SIZE)
                .gridMapSize(GRID_MAP_SIZE)
                .numSectors(SECTOR_NUMBERS)
                .robotLocation(new Point2D.Double(0, -0.5))
                .robotDir(robotDeg)
                .addEchoCell(new Point2D.Double())
                .build();
        RLStateFunction func = RLStateFunction.create(model.worldSpec(), MARKERS);

        // When ...
        Map<String, Signal> signals = func.signals(model);

        // Then
        assertThat(signals, hasKey("cellStates"));
        INDArray cellStates = signals.get("cellStates").toINDArray();
        assertThat(cellStates, matrixCloseTo(new long[]{25}, 1e-3,
                0f, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                2, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
                0, 0, 0, 0, 0
        ));

        // And
        assertThat(signals, hasKey("robotMapDir"));
        int dir = signals.get("robotMapDir").getInt(0);
        assertEquals(robotDeg - MAP_DEG, dir);
    }

    @Test
    void testSpecCellTest() {
        // Given ...
        RLStateFunction func = RLStateFunction.create(WORLD_SPEC, MARKERS);

        // When ...
        Map<String, SignalSpec> spec = func.spec();

        // Then ...
        SignalSpec cellStates = spec.get("cellStates");
        assertThat(cellStates, instanceOf(IntSignalSpec.class));
        assertArrayEquals(new long[]{GRID_MAP_SIZE * GRID_MAP_SIZE}, cellStates.shape());
        assertEquals(4, ((IntSignalSpec) cellStates).numValues());
    }
}