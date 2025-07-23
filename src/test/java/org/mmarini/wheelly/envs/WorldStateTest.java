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
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.rl.envs.FloatSignalSpec;
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.wheelly.apis.*;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public static final List<String> MARKERS = List.of("A", "B");
    private static final int RADAR_SIZE = 11;
    public static final double MARKER_SIZE = 0.3;
    public static final WorldModelSpec WORLD_SPEC = new WorldModelSpec(ROBOT_SPEC, SECTOR_NUMBERS, GRID_MAP_SIZE, MARKER_SIZE);
    public static final Map<String, SignalSpec> SIGNAL_SPEC = WorldEnvironment.createStateSpec(WORLD_SPEC, MARKERS.size());
    private static final float MAX_RADAR_DISTANCE = 3f;
    private static final long NUM_MARKERS = MARKERS.size();

    private static LabelMarker createLabel(String labelA, double xa, double ya) {
        return new LabelMarker(labelA, new Point2D.Double(xa, ya), 1, 0, 0);
    }

    @NotNull
    private static RadarMap createRadarMap() {
        return RadarMap.empty(GridTopology.create(new Point2D.Float(), RADAR_SIZE, RADAR_SIZE, GRID_SIZE))
                .updateCellAt(0, 0, cell -> cell.addEchogenic(100, 0));
    }

    private static RobotStatus createRobotStatus(double robotX, double robotY, int robotDeg) {
        return RobotStatus.create(ROBOT_SPEC, x -> 12)
                .setLocation(new Point2D.Double(robotX, robotY))
                .setDirection(Complex.fromDeg(robotDeg));
    }

    private static PolarMap createPolarMap() {
        return PolarMap.create(SECTOR_NUMBERS);
    }

    static WorldModel createWorldModel(RobotStatus status, LabelMarker... markers) {
        RadarMap radarMap = createRadarMap();
        PolarMap polarMap = createPolarMap();
        GridMap gridMap = GridMap.create(radarMap, status.location(), status.direction(), GRID_MAP_SIZE);
        Map<String, LabelMarker> markerMap = Arrays.stream(markers)
                .filter(m -> !CameraEvent.UNKNOWN_QR_CODE.equals(m.label()))
                .collect(Collectors.toMap(
                        LabelMarker::label,
                        x -> x
                ));
        return new WorldModel(WORLD_SPEC, status, radarMap, markerMap, polarMap, gridMap, null);
    }

    @Test
    void createTest() {
        // Given a mock status
        WorldModel model = createWorldModel(createRobotStatus(0, 0, 0));

        // When create a state from world state
        WorldState state = WorldState.create(model, SIGNAL_SPEC, MARKERS);

        // Then ...
        assertSame(model, state.model());
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
    void markerSignalsTest(int robotDeg, String labelA, double xa, double ya, String labelB, double xb, double yb,
                           float expStateA, float expStateB,
                           float expDistA, float expDistB,
                           int expDirA, int expDirB
    ) {
        // Given ...
        RobotStatus status = createRobotStatus(0, 0, robotDeg);
        WorldModel model = createWorldModel(status, createLabel(labelA, xa, ya), createLabel(labelB, xb, yb));
        WorldState state = WorldState.create(model, SIGNAL_SPEC, MARKERS);

        // When ...
        Map<String, Signal> signals = state.signals();

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
    void markersSpecTest() {
        // Given ...
        WorldModel model = createWorldModel(createRobotStatus(0, 0, 0));
        WorldState state = WorldState.create(model, SIGNAL_SPEC, MARKERS);

        // When ...
        Map<String, SignalSpec> spec = state.spec();

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
    void signalsTest(int robotDeg) {
        // Given ...
        RobotStatus status = createRobotStatus(0, -0.5, robotDeg);
        WorldModel model = createWorldModel(status);
        WorldState state = WorldState.create(model, SIGNAL_SPEC, MARKERS);

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
    void specCellTest() {
        // Given ...
        WorldModel model = createWorldModel(createRobotStatus(0, 0, 0));
        WorldState state = WorldState.create(model, SIGNAL_SPEC, MARKERS);

        // When ...
        Map<String, SignalSpec> spec = state.spec();

        // Then ...
        SignalSpec cellStates = spec.get("cellStates");
        assertThat(cellStates, instanceOf(IntSignalSpec.class));
        assertArrayEquals(new long[]{GRID_MAP_SIZE * GRID_MAP_SIZE}, cellStates.shape());
        assertEquals(4, ((IntSignalSpec) cellStates).numValues());
    }
}