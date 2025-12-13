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

package org.mmarini.wheelly.envs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.*;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.apis.WorldModelBuilder.GRID_MAP_SIZE;
import static org.mmarini.wheelly.envs.DLStateFunction.*;

class DLStateFunctionTest {
    public static final double EPSILON = 1e-5;
    public static final int LABEL_CHANNEL = 4;
    public static final long SEED = 1234;

    static Stream<Arguments> dataMap() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359, 5) // directionDeg
                .uniform(-0.4, 0.4, 5) // xRobot
                .uniform(-0.4, 0.4, 5) // yRobot
                .uniform(-0.4, 0.4, 5) // xCell
                .uniform(-0.4, 0.4, 5) // yCell
                .build(100);
    }

    private DLStateFunction dataGen;

    Stream<int[]> mapChannelIndices(GridTopology topology, int i, int ch) {
        return topology.indices()
                .mapToObj(idx -> new int[]{i, ch, idx / topology.width(), idx % topology.width()});
    }

    @BeforeEach
    void setUp() {
        this.dataGen = DLStateFunction.create(new WorldModelBuilder().build().worldSpec(), List.of("A"));
    }

    @ParameterizedTest
    @CsvSource({
            "false, false, false, false, 0",
            "false, false, false, true, 0", // inconsistent

            "false, true, false, false, 1", // inconsistent
            "false, true, false, true, 1",

            "true, false, false, false, 2",
            "true, false, false, true, 2", // inconsistent
            "true, false, true, false, 2",
            "true, false, true, true, 2", // inconsistent

            "true, true, false, false, 3", // inconsistent
            "true, true, false, true, 3",
            "true, true, true, false, 3", // inconsistent
            "true, true, true, true, 3",

            "false, false, true, false, 4",
            "false, false, true, true, 4", // inconsistent

            "false, true, true, false, 5", // inconsistent
            "false, true, true, true, 5"
    })
    void testCanMoveStates(boolean canMoveForward, boolean canMoveBackward, boolean frontSensor, boolean rearSensor, int expectedCode) {
        // Given ...
        WorldModel model = new WorldModelBuilder()
                .canMoveForward(canMoveForward)
                .canMoveBackward(canMoveBackward)
                .frontSensor(frontSensor)
                .rearSensor(rearSensor)
                .build();

        // When ...
        Map<String, Signal> signals = dataGen.signals(model, model);

        // Then ...
        assertThat(signals, hasKey("canMoveStates"));
        assertThat(signals.get("canMoveStates"), isA(ArraySignal.class));
        ArraySignal signal = (ArraySignal) signals.get("canMoveStates");
        assertThat(signal.toINDArray(), matrixCloseTo(new long[]{2, 1}, EPSILON,
                expectedCode, expectedCode));
    }

    @ParameterizedTest(name = "[{index}] robot@{1},{2} R{0} cell@{3},{4}")
    @MethodSource("dataMap")
    void testMapContactsCell(int directionDeg, double xRobot, double yRobot, double xCell, double yCell) {
        // Given
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        WorldModel model = new WorldModelBuilder()
                .robotDir(directionDeg)
                .addContactsCell(cellLocation)
                .robotLocation(robotLocation)
                .build();

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().hasContact());

        // When ...
        Map<String, Signal> signals = dataGen.signals(model, model);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map").toINDArray();
        assertArrayEquals(new long[]{2, DLStateFunction.NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Complex cellDir = Complex.direction(robotLocation, cellLocation);
        Complex cellDirRobotRelative = cellDir.sub(Complex.fromDeg(directionDeg));
        Point2D mapCellLocation = cellDirRobotRelative.at(new Point2D.Double(), cellLocation.distance(robotLocation));
        GridMap gridMap = model.gridMap();
        GridTopology topology = gridMap.topology();
        int idx = topology.indexOf(mapCellLocation);
        assertThat(idx, greaterThanOrEqualTo(0));
        int x = idx % topology.width();
        int y = idx / topology.width();

        for (int i = 0; i < 2; i++) {
            int[] echoIdx = mapChannelIndices(topology, i, ECHO_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(echoIdx);

            int[] emptyIdx = mapChannelIndices(topology, i, EMPTY_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(emptyIdx);

            int[] labelIdx = mapChannelIndices(topology, i, LABEL_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(labelIdx);

            int[] contactIdx = mapChannelIndices(topology, i, CONTACT_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, CONTACT_CHANNEL, y, x}, contactIdx);

            int[] knownIdx = mapChannelIndices(topology, i, UNKNOWN_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 1F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, UNKNOWN_CHANNEL, y, x}, knownIdx);
        }
    }

    @ParameterizedTest(name = "[{index}] robot@{1},{2} R{0} cell@{3},{4}")
    @MethodSource("dataMap")
    void testMapEchoCell(int directionDeg, double xRobot, double yRobot, double xCell, double yCell) {
        // Given
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        WorldModel model = new WorldModelBuilder()
                .robotLocation(robotLocation)
                .robotDir(directionDeg)
                .addEchoCell(cellLocation)
                .build();

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().echogenic());

        // When ...
        Map<String, Signal> signals = dataGen.signals(model, model);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map").toINDArray();
        assertArrayEquals(new long[]{2, NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Complex cellDir = Complex.direction(robotLocation, cellLocation);
        Complex cellDirRobotRelative = cellDir.sub(Complex.fromDeg(directionDeg));
        Point2D mapCellLocation = cellDirRobotRelative.at(new Point2D.Double(), cellLocation.distance(robotLocation));
        GridMap gridMap = model.gridMap();
        GridTopology topology = gridMap.topology();
        int idx = topology.indexOf(mapCellLocation);
        assertThat(idx, greaterThanOrEqualTo(0));
        int x = idx % topology.width();
        int y = idx / topology.width();

        for (int i = 0; i < 2; i++) {
            int[] contactIdx = mapChannelIndices(topology, i, CONTACT_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(contactIdx);

            int[] emptyIdx = mapChannelIndices(topology, i, EMPTY_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(emptyIdx);

            int[] labelIdx = mapChannelIndices(topology, i, LABEL_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(labelIdx);

            int[] echoIdx = mapChannelIndices(topology, i, ECHO_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, ECHO_CHANNEL, y, x}, echoIdx);

            int[] knownIdx = mapChannelIndices(topology, i, UNKNOWN_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 1F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, UNKNOWN_CHANNEL, y, x}, knownIdx);
        }
    }

    @ParameterizedTest(name = "[{index}] robot@{1},{2} R{0} cell@{3},{4}")
    @MethodSource("dataMap")
    void testMapEmptyCell(int directionDeg, double xRobot, double yRobot, double xCell, double yCell) {
        // Given
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        WorldModel model = new WorldModelBuilder()
                .robotLocation(robotLocation)
                .robotDir(directionDeg)
                .addEmptyCell(cellLocation)
                .build();

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().empty());

        // When ...
        Map<String, Signal> signals = dataGen.signals(model, model);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map").toINDArray();
        assertArrayEquals(new long[]{2, NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Complex cellDir = Complex.direction(robotLocation, cellLocation);
        Complex cellDirRobotRelative = cellDir.sub(Complex.fromDeg(directionDeg));
        Point2D mapCellLocation = cellDirRobotRelative.at(new Point2D.Double(), cellLocation.distance(robotLocation));
        GridMap gridMap = model.gridMap();
        GridTopology topology = gridMap.topology();
        int idx = topology.indexOf(mapCellLocation);
        assertThat(idx, greaterThanOrEqualTo(0));
        int x = idx % topology.width();
        int y = idx / topology.width();

        for (int i = 0; i < 2; i++) {
            int[] contactIdx = mapChannelIndices(topology, 0, CONTACT_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(contactIdx);

            int[] echoIdx = mapChannelIndices(topology, 0, ECHO_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(echoIdx);

            int[] labelIdx = mapChannelIndices(topology, 0, LABEL_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(labelIdx);

            int[] emptyIdx = mapChannelIndices(topology, 0, EMPTY_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{0, EMPTY_CHANNEL, y, x}, emptyIdx);

            int[] knownIdx = mapChannelIndices(topology, 0, UNKNOWN_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 1F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{0, UNKNOWN_CHANNEL, y, x}, knownIdx);
        }
    }

    @ParameterizedTest(name = "[{index}] robot@{1},{2} R{0} cell@{3},{4}")
    @MethodSource("dataMap")
    void testMapLabelCell(int directionDeg, double xRobot, double yRobot, double xCell, double yCell) {
        // Given
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        WorldModel model = new WorldModelBuilder()
                .robotLocation(robotLocation)
                .robotDir(directionDeg)
                .addLabel("A", cellLocation)
                .build();
//        WorldModel model = createModeller().updateForInference(createModelLabelMap(directionDeg, robotLocation, cellLocation));

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().echogenic());

        // When ...
        Map<String, Signal> signals = dataGen.signals(model, model);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map").toINDArray();
        assertArrayEquals(new long[]{2, NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Complex cellDir = Complex.direction(robotLocation, cellLocation);
        Complex cellDirRobotRelative = cellDir.sub(Complex.fromDeg(directionDeg));
        Point2D mapCellLocation = cellDirRobotRelative.at(new Point2D.Double(), cellLocation.distance(robotLocation));
        GridMap gridMap = model.gridMap();
        GridTopology topology = gridMap.topology();
        int idx = topology.indexOf(mapCellLocation);
        assertThat(idx, greaterThanOrEqualTo(0));
        int x = idx % topology.width();
        int y = idx / topology.width();

        for (int i = 0; i < 2; i++) {
            int[] contactIdx = mapChannelIndices(topology, i, CONTACT_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(contactIdx);

            int[] emptyIdx = mapChannelIndices(topology, i, EMPTY_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertNull(emptyIdx);

            int[] labelIdx = mapChannelIndices(topology, i, LABEL_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, LABEL_CHANNEL, y, x}, labelIdx);

            int[] echoIdx = mapChannelIndices(topology, i, ECHO_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 0F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, ECHO_CHANNEL, y, x}, echoIdx);

            int[] knownIdx = mapChannelIndices(topology, i, UNKNOWN_CHANNEL)
                    .filter(idx1 -> signal.getInt(idx1) != 1F)
                    .findAny()
                    .orElse(null);
            assertArrayEquals(new int[]{i, UNKNOWN_CHANNEL, y, x}, knownIdx);
        }
    }

}