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

package org.mmarini.wheelly.gyms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.rl.envs.ArraySignal;
import org.mmarini.rl.envs.DLStateFunction;
import org.mmarini.wheelly.behaviours.*;
import org.mmarini.wheelly.perception.GridMap;
import org.mmarini.wheelly.perception.GridTopology;
import org.mmarini.wheelly.perception.WorldModel;
import org.mmarini.wheelly.perception.WorldModelBuilder;
import org.mmarini.wheelly.robots.HeadStatusId;
import org.mmarini.wheelly.robots.MotionStatusId;
import org.mmarini.wheelly.utils.Angle;
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
import static org.mmarini.wheelly.behaviours.HeadActionId.LOOK_STRAIGHT_ACTION;
import static org.mmarini.wheelly.behaviours.MoveActionId.HALT_ACTION;
import static org.mmarini.wheelly.gyms.MacroActionsStateFunction.*;
import static org.mmarini.wheelly.perception.MarkerLocatorTest.MARKER0;
import static org.mmarini.wheelly.perception.WorldModelBuilder.GRID_MAP_SIZE;
import static org.mmarini.wheelly.robots.RobotSpec.DEFAULT_ROBOT_SPEC;

class MacroActionsStateFunctionTest {
    public static final double EPSILON = 1e-5;
    public static final int LABEL_CHANNEL = 4;
    public static final long SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataFsmSignals() {
        return Arrays.stream(MoveActionId.values())
                .flatMap(moveActionId ->
                        Arrays.stream(HeadActionId.values()).map(
                                headActionId ->
                                        Arguments.of(new MacroAction(moveActionId, headActionId))
                        )
                );
    }

    public static Stream<Arguments> dataHeadSignal() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-3, 3, 17)
                .uniform(-3, 3, 17)
                .uniform(-180, 179)
                .uniform(-90, 90)
                .uniform(0, 300)
                .uniform(0, 300)
                .uniform(0, HeadStatusId.values().length - 1)
                .uniform(-180, 179)
                .uniform(-3, 3, 17)
                .uniform(-3, 3, 17)
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
                .uniform(0, MotionStatusId.values().length - 1)
                .uniform(-180, 179)
                .uniform(-DEFAULT_ROBOT_SPEC.maxRadarDistance(), DEFAULT_ROBOT_SPEC.maxRadarDistance(), 17)
                .uniform(-DEFAULT_ROBOT_SPEC.maxRadarDistance(), DEFAULT_ROBOT_SPEC.maxRadarDistance(), 17)
                .build(NUM_RANDOM_TEST_CASES);
    }

    private MacroActionsStateFunction stateFunc;
    private WorldModelBuilder builder;

    Stream<int[]> mapChannelIndices(GridTopology topology, int i, int ch) {
        return topology.indices()
                .mapToObj(idx -> new int[]{i, ch, idx / topology.width(), idx % topology.width()});
    }

    @BeforeEach
    void setUp() {
        this.stateFunc = MacroActionsStateFunction.create(new WorldModelBuilder().build().worldSpec(), List.of(MARKER0));
        this.builder = new WorldModelBuilder();
    }

    @ParameterizedTest
    @CsvSource({
            "-1",
            "0"
    })
    void testCameraSignal(int marker) {
        WorldModel model = builder
                .cameraMarker(marker)
                .updateLidarTime()
                .addTime(1)
                .build();
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);
        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(CAMERA_SIGNAL_ID));
        // And
        INDArray signal = signals.get(CAMERA_SIGNAL_ID);
        // And
        INDArray expected = Nd4j.zeros(2, 5);

        expected.putScalar(0, 0, 0);
        expected.putScalar(0, 1, 1);
        expected.putScalar(0, 2, 1);
        expected.putScalar(0, 3, marker < 0 ? 1 : 0);
        expected.putScalar(0, 4, marker >= 0 ? 1 : 0);

        expected.putScalar(1, 0, 0);
        expected.putScalar(1, 1, 1);
        expected.putScalar(1, 2, 1);
        expected.putScalar(1, 3, 1 - marker);
        expected.putScalar(1, 4, marker);

        assertThat(signal, matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @MethodSource("dataFsmSignals")
    void testFsmSignal(MacroAction agentAction) {
        // Given
        WorldModel model = builder.build();
        MoveActionId moveActionId = agentAction.moveActionId();
        HeadActionId headActionId = agentAction.headActionId();
        MacroActionsState state = new MacroActionsState(model, moveActionId, headActionId);

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then signal should contain fsm signal
        assertThat(signals, hasKey(FSM_SIGNAL_ID));
        // And should be arrayed
        INDArray signal = signals.get(FSM_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And should contain the one hot action signals
        INDArray expected = Nd4j.zeros(2, 25);
        expected.putScalar(0, moveActionId.ordinal(), 1.0);
        expected.putScalar(0, headActionId.ordinal() + MoveActionId.values().length, 1.0);
        expected.putScalar(1, moveActionId.ordinal(), 1.0);
        expected.putScalar(1, headActionId.ordinal() + MoveActionId.values().length, 1.0);
        assertThat(signal, matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0, 0,0, 0, 0, 0,0"
    })
    @MethodSource("dataHeadSignal")
    void testHeadSignal(double x, double y, int robotDeg, int headDeg, int frontDistance, int rearDistance,
                        int status, int targetDeg, double xTarget, double yTarget) {
        Point2D robotLocation = new Point2D.Double(x, y);
        Point2D target = new Point2D.Double(xTarget, yTarget);
        WorldModel model = builder
                .robotLocation(robotLocation)
                .robotDir(robotDeg)
                .headAngle(headDeg)
                .robotHeadStatus(HeadStatusId.values()[status])
                .robotHeadTargetDeg(targetDeg)
                .robotHeadTarget(target)
                .frontDistance(frontDistance * 0.01)
                .rearDistance(rearDistance * 0.01)
                .build();
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(HEAD_SIGNAL_ID));
        // And
        INDArray signal = signals.get(HEAD_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        INDArray expected = Nd4j.zeros(2, 10);
        double sinHead = sin(toRadians(headDeg));
        double maxDistance = DEFAULT_ROBOT_SPEC.maxRadarDistance();
        double front = clamp(frontDistance * 0.01 / maxDistance, 0, 1);
        double rear = clamp(rearDistance * 0.01 / maxDistance, 0, 1);
        Point2D egoTarget = model.robotStatus().egocentric(target);
        double xEgoTarget = clamp(egoTarget.getX() / maxDistance, -1, 1);
        double yEgoTarget = clamp(egoTarget.getY() / maxDistance, -1, 1);
        Angle egoTargetDir = Angle.fromDeg(targetDeg - robotDeg);
        double sinTarget = egoTargetDir.sin();
        double cosTarget = egoTargetDir.cos();
        for (int i = 0; i < 2; i++) {
            expected.putScalar(i, 0, sinHead);
            expected.putScalar(i, 1, front);
            expected.putScalar(i, 2, rear);
            expected.putScalar(i, 3, xEgoTarget);
            expected.putScalar(i, 4, yEgoTarget);
            expected.putScalar(i, 5, sinTarget);
            expected.putScalar(i, 6, cosTarget);
            expected.putScalar(i, 7 + status, 1);
        }
        assertThat(signal, matrixCloseTo(expected, 1e-3));
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
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().hasContact());

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map");
        assertArrayEquals(new long[]{2, DLStateFunction.NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Angle cellDir = Angle.direction(robotLocation, cellLocation);
        Angle cellDirRobotRelative = cellDir.sub(Angle.fromDeg(directionDeg));
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
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().echogenic());

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map");
        assertArrayEquals(new long[]{2, NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Angle cellDir = Angle.direction(robotLocation, cellLocation);
        Angle cellDirRobotRelative = cellDir.sub(Angle.fromDeg(directionDeg));
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
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().empty());

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map");
        assertArrayEquals(new long[]{2, NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Angle cellDir = Angle.direction(robotLocation, cellLocation);
        Angle cellDirRobotRelative = cellDir.sub(Angle.fromDeg(directionDeg));
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
                .addMarker(MARKER0, cellLocation)
                .build();
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);
        //        WorldModel model = createModeller().updateForInference(createModelLabelMap(directionDeg, robotLocation, cellLocation));

        assertTrue(model.radarMap().cell(cellLocation).orElseThrow().echogenic());

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then ...
        assertThat(signals, hasKey("map"));
        assertThat(signals.get("map"), isA(ArraySignal.class));
        INDArray signal = signals.get("map");
        assertArrayEquals(new long[]{2, NUM_CELL_STATES + 1, GRID_MAP_SIZE, GRID_MAP_SIZE}, signal.shape());

        Angle cellDir = Angle.direction(robotLocation, cellLocation);
        Angle cellDirRobotRelative = cellDir.sub(Angle.fromDeg(directionDeg));
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
            "4,4, false,false, false,false",
    })
    @MethodSource("dataProxySignal")
    void testProxySignal(boolean canMoveForward, boolean canMoveBackward,
                         boolean frontSensor, boolean rearSensor) {
        WorldModel model = builder
                .canMoveForward(canMoveForward)
                .canMoveBackward(canMoveBackward)
                .frontSensor(frontSensor)
                .rearSensor(rearSensor)
                .build();
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(PROXY_SIGNAL_ID));
        // And
        INDArray signal = signals.get(PROXY_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        INDArray expected = Nd4j.zeros(2, 4);
        for (int i = 0; i < 2; i++) {
            expected.putScalar(i, 0, frontSensor ? 1 : 0);
            expected.putScalar(i, 1, rearSensor ? 1 : 0);
            expected.putScalar(i, 2, canMoveForward ? 1 : 0);
            expected.putScalar(i, 3, canMoveBackward ? 1 : 0);
        }
        assertThat(signal, matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0, 0, 0,0"
    })
    @MethodSource("dataRobotSignal")
    void testRobotSignal(double leftPps, double rightPps, int robotDeg, int status, int targetDeg, double xTarget, double yTarget) {
        // And a world model
        switch (MotionStatusId.values()[status]) {
            case HALT -> builder.halt();
            case FORWARD -> builder.moving(true);
            case BACKWARD -> builder.moving(false);
            case ROTATE -> builder.rotating();
        }
        Point2D target = new Point2D.Double(xTarget, yTarget);
        builder.robotSpeed(leftPps, rightPps)
                .robotDir(robotDeg)
                .robotTargetDeg(targetDeg)
                .robotTarget(target);
        WorldModel model = builder.build();
        // And an env state
        BehaviourState state = new MacroActionsState(model, HALT_ACTION, LOOK_STRAIGHT_ACTION);
        // And expected signal
        INDArray expected = Nd4j.zeros(2, ROBOT_SIGNAL_SIZE);
        double linSpeed = (leftPps + rightPps) / 2 / DEFAULT_ROBOT_SPEC.maxSpeed();
        double rotSpeed = (leftPps - rightPps) / 2 / DEFAULT_ROBOT_SPEC.maxSpeed();
        Angle mapRelDir = model.gridMap().direction().sub(model.robotStatus().direction());
        double sinMap = mapRelDir.sin();
        double cosMap = mapRelDir.cos();
        Angle targetDir = Angle.fromDeg(targetDeg - robotDeg);
        double sinTarget = targetDir.sin();
        double cosTarget = targetDir.cos();
        Point2D egoTarget = model.robotStatus().egocentric(target);
        double egoTargetX = clamp(egoTarget.getX() / DEFAULT_ROBOT_SPEC.maxRadarDistance(), -1, 1);
        double egoTargetY = clamp(egoTarget.getY() / DEFAULT_ROBOT_SPEC.maxRadarDistance(), -1, 1);
        for (int i = 0; i < 2; i++) {
            expected.putScalar(i, 0, linSpeed);
            expected.putScalar(i, 1, rotSpeed);
            expected.putScalar(i, 2, sinMap);
            expected.putScalar(i, 3, cosMap);
            expected.putScalar(i, 4 + status, 1);
            expected.putScalar(i, 8, egoTargetX);
            expected.putScalar(i, 9, egoTargetY);
            expected.putScalar(i, 10, sinTarget);
            expected.putScalar(i, 11, cosTarget);
        }

        // When ...
        Map<String, INDArray> signals = stateFunc.signals(state, state);

        // Then
        assertThat(signals, hasKey(ROBOT_SIGNAL_ID));
        // And
        INDArray signal = signals.get(ROBOT_SIGNAL_ID);
        assertThat(signal, isA(ArraySignal.class));
        // And
        assertThat(signal, matrixCloseTo(expected, 1e-3));
    }
}