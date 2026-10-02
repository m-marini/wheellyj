/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.fsm.AgentAction;
import org.mmarini.wheelly.fsm.HeadActionId;
import org.mmarini.wheelly.fsm.MoveActionId;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mmarini.wheelly.apis.WorldModelBuilder.GRID_MAP_SIZE;
import static org.mmarini.wheelly.envs.DLMacroActionStateFunction.*;
import static org.mmarini.wheelly.fsm.HeadActionId.LOOK_STRIGHT_ACTION;
import static org.mmarini.wheelly.fsm.MoveActionId.HALT_ACTION;

class DLMacroActionStateFunctionTest {
    public static final double EPSILON = 1e-5;
    public static final int LABEL_CHANNEL = 4;
    public static final long SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataFsmSignals() {
        return Arrays.stream(MoveActionId.values())
                .flatMap(moveActionId ->
                        Arrays.stream(HeadActionId.values()).map(
                                headActionId ->
                                        Arguments.of(new AgentAction(moveActionId, headActionId))
                        )
                );
    }

    public static Stream<Arguments> dataHeadSignal() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-90, 90)
                .build(NUM_RANDOM_TEST_CASES);
    }

    static Stream<Arguments> dataMap() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359, 5) // directionDeg
                .uniform(-0.4, 0.4, 5) // xRobot
                .uniform(-0.4, 0.4, 5) // yRobot
                .uniform(-0.4, 0.4, 5) // xCell
                .uniform(-0.4, 0.4, 5) // yCell
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataProxySignal() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, DEFAULT_ROBOT_SPEC.maxRadarDistance() + 0.1)
                .uniform(0, DEFAULT_ROBOT_SPEC.maxRadarDistance() + 0.1)
                .booleans()
                .booleans()
                .booleans()
                .booleans()
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRobotSignal() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-DEFAULT_ROBOT_SPEC.maxSpeed(), (double) DEFAULT_ROBOT_SPEC.maxSpeed())
                .uniform(-DEFAULT_ROBOT_SPEC.maxSpeed(), (double) DEFAULT_ROBOT_SPEC.maxSpeed())
                .uniform(-180, 179)
                .booleans()
                .build(NUM_RANDOM_TEST_CASES);
    }

    private DLMacroActionStateFunction stateFunc;
    private WorldModelBuilder builder;

    Stream<int[]> mapChannelIndices(GridTopology topology, int i, int ch) {
        return topology.indices()
                .mapToObj(idx -> new int[]{i, ch, idx / topology.width(), idx % topology.width()});
    }

    @BeforeEach
    void setUp() {
        this.stateFunc = DLMacroActionStateFunction.create(new WorldModelBuilder().build().worldSpec(), List.of("A"));
        this.builder = new WorldModelBuilder();
    }

    @ParameterizedTest
    @CsvSource({
            "0",
            "1"
    })
    void testCameraSignal(int marker) {
        WorldModel model = builder
                .cameraMarker(marker != 0 ? "A" : "?")
                .updateLidarTime()
                .addTime(1)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(CAMERA_SIGNAL_ID));
        // And
        Signal signal = signals.get(CAMERA_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        INDArray expected = Nd4j.zeros(2, 5);

        expected.putScalar(0, 0, 0);
        expected.putScalar(0, 1, 1);
        expected.putScalar(0, 2, 1);
        expected.putScalar(0, 3, 1 - marker);
        expected.putScalar(0, 4, marker);

        expected.putScalar(1, 0, 0);
        expected.putScalar(1, 1, 1);
        expected.putScalar(1, 2, 1);
        expected.putScalar(1, 3, 1 - marker);
        expected.putScalar(1, 4, marker);

        assertThat(signal.toINDArray(), matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @MethodSource("dataFsmSignals")
    void testFsmSignal(AgentAction agentAction) {
        // Given
        WorldModel model = builder.build();
        MoveActionId moveActionId = agentAction.moveId();
        HeadActionId headActionId = agentAction.headId();
        MacroEnvState state = new MacroEnvState(model, moveActionId, headActionId);

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

        // Then signal shuld contains fsm signal
        assertThat(signals, hasKey(FSM_SIGNAL_ID));
        // And should be array
        Signal signal = signals.get(FSM_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And should contain the one hot action signals
        INDArray expected = Nd4j.zeros(2, 22);
        expected.putScalar(0, moveActionId.ordinal(), 1.0);
        expected.putScalar(0, headActionId.ordinal() + MoveActionId.values().length, 1.0);
        expected.putScalar(1, moveActionId.ordinal(), 1.0);
        expected.putScalar(1, headActionId.ordinal() + MoveActionId.values().length, 1.0);
        assertThat(signal.toINDArray(), matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0"
    })
    @MethodSource("dataHeadSignal")
    void testHeadSignal(int headDeg) {
        WorldModel model = builder
                .headAngle(headDeg)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(HEAD_SIGNAL_ID));
        // And
        Signal signal = signals.get(HEAD_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        INDArray expected = Nd4j.zeros(2, 7);
        double sinHead = sin(toRadians(headDeg));
        double cosHead = cos(toRadians(headDeg));

        expected.putScalar(0, 0, sinHead);
        expected.putScalar(0, 1, cosHead);
        expected.putScalar(0, 2, 0);
        expected.putScalar(0, 3, 0);
        expected.putScalar(0, 4, 0);
        expected.putScalar(0, 5, 1);
        expected.putScalar(0, 6, 0);
        expected.putScalar(1, 0, sinHead);
        expected.putScalar(1, 1, cosHead);
        expected.putScalar(1, 2, 0);
        expected.putScalar(1, 3, 0);
        expected.putScalar(1, 4, 0);
        expected.putScalar(1, 5, 1);
        expected.putScalar(1, 6, 0);
        assertThat(signal.toINDArray(), matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest(name = "[{index}] robot@{1},{2} R{0} cell@{3},{4}")
    @MethodSource("dataMap")
    void testMapContactsCell(int directionDeg, double xRobot, double yRobot, double xCell, double yCell) {
        // Given
        Point2D robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D cellLocation = new Point2D.Double(xCell, yCell);
        WorldModel model = builder.robotDir(directionDeg)
                .addContactsCell(cellLocation)
                .robotLocation(robotLocation)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().hasContact());

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

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
        WorldModel model = builder.robotLocation(robotLocation)
                .robotDir(directionDeg)
                .addEchoCell(cellLocation)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().echogenic());

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

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
        WorldModel model = builder.robotLocation(robotLocation)
                .robotDir(directionDeg)
                .addEmptyCell(cellLocation)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().empty());

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

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
        WorldModel model = builder.robotLocation(robotLocation)
                .robotDir(directionDeg)
                .addMarker("A", cellLocation)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);
        //        WorldModel model = createModeller().updateForInference(createModelLabelMap(directionDeg, robotLocation, cellLocation));

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().echogenic());

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

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

    @ParameterizedTest
    @CsvSource({
            "0,0, false,false, false,false",
            "3,3, false,false, false,false",
    })
    @MethodSource("dataProxySignal")
    void testProxySignal(double frontDistance, double rearDistance,
                         boolean canMoveForward, boolean canMoveBackward,
                         boolean frontSensor, boolean rearSensor) {
        WorldModel model = builder
                .frontDistance(frontDistance)
                .rearDistance(rearDistance)
                .canMoveForward(canMoveForward)
                .canMoveBackward(canMoveBackward)
                .frontSensor(frontSensor)
                .rearSensor(rearSensor)
                .build();
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(PROXY_SIGNAL_ID));
        // And
        Signal signal = signals.get(PROXY_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        INDArray expected = Nd4j.zeros(2, 6);
        double frontSignal = min(frontDistance / DEFAULT_ROBOT_SPEC.maxRadarDistance(), 1);
        double rearSignal = min(rearDistance / DEFAULT_ROBOT_SPEC.maxRadarDistance(), 1);
        for (int i = 0; i < 2; i++) {
            expected.putScalar(i, 0, frontSignal);
            expected.putScalar(i, 1, rearSignal);
            expected.putScalar(i, 2, frontSensor ? 1 : 0);
            expected.putScalar(i, 2, frontSensor ? 1 : 0);
            expected.putScalar(i, 3, rearSensor ? 1 : 0);
            expected.putScalar(i, 4, canMoveForward ? 1 : 0);
            expected.putScalar(i, 5, canMoveBackward ? 1 : 0);
        }
        assertThat(signal.toINDArray(), matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0,0"
    })
    @MethodSource("dataRobotSignal")
    void testRobotSignal(double leftPps, double rightPps, int robotDeg, boolean halt) {
        // Given
        WorldModel model = builder
                .robotSpeed(leftPps, rightPps)
                .robotDir(robotDeg)
                .robotHalt(halt)
                .build();
        // And a env state
        MacroEnvState state = new MacroEnvState(model, HALT_ACTION, LOOK_STRIGHT_ACTION);
        // And expected signal
        INDArray expected = Nd4j.zeros(2, 11);
        double linSpeed = (leftPps + rightPps) / 2 / DEFAULT_ROBOT_SPEC.maxSpeed();
        double rotSpeed = (leftPps - rightPps) / 2 / DEFAULT_ROBOT_SPEC.maxSpeed();
        Complex mapRelDir = model.gridMap().direction().sub(model.robotStatus().direction());
        double sinMap = mapRelDir.sin();
        double cosMap = mapRelDir.cos();
        double haltSignal = halt ? 1 : 0;
        for (int i = 0; i < 2; i++) {
            expected.putScalar(i, 0, linSpeed);
            expected.putScalar(i, 1, rotSpeed);
            expected.putScalar(i, 2, sinMap);
            expected.putScalar(i, 3, cosMap);
            expected.putScalar(i, 4, haltSignal);
            expected.putScalar(i, 5, 0);
            expected.putScalar(i, 6, 0);
            expected.putScalar(i, 7, 0);
            expected.putScalar(i, 8, 0);
            expected.putScalar(i, 9, 0);
            expected.putScalar(i, 10, 1);
        }

        // When ...
        Map<String, Signal> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(ROBOT_SIGNAL_ID));
        // And
        Signal signal = signals.get(ROBOT_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        assertThat(signal.toINDArray(), matrixCloseTo(expected, 1e-3));
    }
}