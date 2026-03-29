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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.clamp;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_HEAD_FOV_DEG;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.envs.DLActionFunction.*;

class DLActionFunctionTest {
    public static final double EPSILON = 1e-5;
    public static final int SEED = 1234;
    public static final int DEFAULT_NUM_MOVE_ACTIONS = DLActionFunction.DEFAULT_GRID_SIZE * DLActionFunction.DEFAULT_GRID_SIZE - 5;
    public static final int NUM_RANDOM_TEST_CASES = 100;
    public static final int FORWARD_SW = 37;
    public static final int FORWARD_SE = 67;
    public static final int FORWARD_NW = 962;
    public static final int FORWARD_NE = 992;
    public static final int BACKWARD_SW = 993;
    public static final int BACKWARD_SE = 1023;
    public static final int BACKWARD_NW = 1918;
    public static final int BACKWARD_NE = 1948;

    public static Stream<Arguments> dataDecodeCommandBackward() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // double robotX
                .uniform(-2.0, 2.0, 9) // double robotY
                .uniform(0, 359) // int robotDeg
                .uniform(0, DEFAULT_NUM_HEAD_ROTATIONS - 1) // int headCommand                .
                .uniform(DEFAULT_NUM_ROTATIONS + 1 + DEFAULT_NUM_MOVE_ACTIONS, DEFAULT_NUM_ROTATIONS + DEFAULT_NUM_MOVE_ACTIONS * 2) // int moveCommand                .
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataDecodeCommandForward() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // double robotX
                .uniform(-2.0, 2.0, 9) // double robotY
                .uniform(0, 359) // int robotDeg
                .uniform(0, DEFAULT_NUM_HEAD_ROTATIONS - 1) // int headCommand                .
                .uniform(DEFAULT_NUM_ROTATIONS + 1, DEFAULT_NUM_ROTATIONS + DEFAULT_NUM_MOVE_ACTIONS) // int moveCommand                .
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataDecodeCommandRotation() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // double robotX
                .uniform(-2.0, 2.0, 9) // double robotY
                .uniform(0, 359) // int robotDeg
                .uniform(0, DEFAULT_NUM_HEAD_ROTATIONS - 1) // int headCommand                .
                .uniform(1, DEFAULT_NUM_ROTATIONS) // int rotateCommand                .
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataE() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // robotX
                .uniform(-2.0, 2.0, 9) // robotY
                .uniform(46, 134)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataHeadAngle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // double robotX
                .uniform(-2.0, 2.0, 9) // double robotY
                .uniform(0, 359) // int robotDeg
                .uniform(0, DEFAULT_NUM_HEAD_ROTATIONS - 1) // int headCommand                .
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataNE() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // robotX
                .uniform(-2.0, 2.0, 9) // robotY
                .uniform(315, 359)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataNW() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // robotX
                .uniform(-2.0, 2.0, 9) // robotY
                .uniform(0, 44)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataRotation() {
        return IntStream.range(0, DEFAULT_NUM_ROTATIONS)
                .mapToObj(i ->
                        Arguments.of(
                                i + 1,
                                i * 360 / DEFAULT_NUM_ROTATIONS
                        )
                );
    }

    public static Stream<Arguments> dataS() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // robotX
                .uniform(-2.0, 2.0, 9) // robotY
                .uniform(136, 224)
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataW() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 9) // robotX
                .uniform(-2.0, 2.0, 9) // robotY
                .uniform(226, 314)
                .build(NUM_RANDOM_TEST_CASES);
    }

    private DLActionFunction function;
    private WorldModel model;

    void createWorldModel(double robotX, double robotY, int robotDeg) {
        this.model = new WorldModelBuilder()
                .robotLocation(new Point2D.Double(robotX, robotY))
                .robotDir(robotDeg)
                .build();
    }

    @BeforeEach
    void setUp() {
        function = create(DEFAULT_NUM_ROTATIONS, DEFAULT_NUM_HEAD_ROTATIONS, DEFAULT_GRID_SIZE, DEFAULT_GRID_STEP, DEFAULT_HIDE_RADIUS);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -3,-3, 993",
            "0,0, 0, 3,-3, 1023",
            "0,0, 0, -3,3, 1918",
            "0,0, 0, 3,3, 1948",

            "0,0, 90, -3,-3, 1023",
            "0,0, 90, 3,-3, 1948",
            "0,0, 90, -3,3, 993",
            "0,0, 90, 3,3, 1918",

            "0,0, -90, -3,-3, 1918",
            "0,0, -90, 3,-3, 993",
            "0,0, -90, -3,3, 1948",
            "0,0, -90, 3,3, 1023",

            "0,0, -180, -3,-3, 1948",
            "0,0, -180, 3,-3, 1918",
            "0,0, -180, -3,3, 1023",
            "0,0, -180, 3,3, 993",

            "1,1, 0, -2,-2, 993",
            "1,1, 0, 4,4, 1948",
            "1,1, 90, -2,-2, 1023",
            "1,1, 90, 4,4, 1918",
            "1,1, -90, -2,-2, 1918",
            "1,1, -90, 4,4, 1023",
            "1,1, -180, -2,-2, 1948",
            "1,1, -180, 4,4, 993",
    })
    void testBackwardIndex(double robotX, double robotY, int robotDeg, double targetX, double targetY, int expectedIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        Point2D target = new Point2D.Double(targetX, targetY);
        RobotCommands cmd = RobotCommands.backward(target);
        // When ...
        int idx = function.moveIndex(cmd, model);

        // Then ...
        assertEquals(expectedIndex, idx);
    }


    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -3,-3, 993",
            "0,0, 0, 3,-3, 1023",
            "0,0, 0, -3,3, 1918",
            "0,0, 0, 3,3, 1948",

            "0,0, 90, -3,-3, 1023",
            "0,0, 90, 3,-3, 1948",
            "0,0, 90, -3,3, 993",
            "0,0, 90, 3,3, 1918",

            "0,0, -90, -3,-3, 1918",
            "0,0, -90, 3,-3, 993",
            "0,0, -90, -3,3, 1948",
            "0,0, -90, 3,3, 1023",

            "0,0, -180, -3,-3, 1948",
            "0,0, -180, 3,-3, 1918",
            "0,0, -180, -3,3, 1023",
            "0,0, -180, 3,3, 993",

            "1,1, 0, -2,-2, 993",
            "1,1, 0, 4,4, 1948",
            "1,1, 90, -2,-2, 1023",
            "1,1, 90, 4,4, 1918",
            "1,1, -90, -2,-2, 1918",
            "1,1, -90, 4,4, 1023",
            "1,1, -180, -2,-2, 1948",
            "1,1, -180, 4,4, 993",
    })
    void testBackwardMasks(double robotX, double robotY, int robotDeg, double targetX, double targetY, int expectedIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        Point2D target = new Point2D.Double(targetX, targetY);
        RobotCommands cmd = RobotCommands.backward(target);
        // When ...
        Map<String, INDArray> masks = function.actionMasks(
                List.of(model, model),
                List.of(cmd, cmd));

        // Then ...
        assertNotNull(masks);
        assertThat(masks, hasKey(MOVE_ACTION_ID));
        assertThat(masks, hasKey(HEAD_ACTION_ID));
        INDArray mask = masks.get(MOVE_ACTION_ID);
        INDArray expected = Nd4j.zeros(2, 1 + DEFAULT_NUM_ROTATIONS + 2 * DEFAULT_NUM_MOVE_ACTIONS);
        expected.putScalar(0, expectedIndex, 1);
        expected.putScalar(1, expectedIndex, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));

        /*
        mask = masks.get(HEAD_ACTION_ID);
        expected = Nd4j.zeros(2, DEFAULT_NUM_HEAD_ROTATIONS);
        expected.putScalar(0, expectedIndex, 1);
        expected.putScalar(1, expectedIndex, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));

         */
    }

    @ParameterizedTest(name = "[{index}], Robot @({0},{1}) R{2}, headCmd={3}")
    @MethodSource("dataHeadAngle")
    void testCommandHalt(double robotX, double robotY, int robotDeg, int headCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        Map<String, Signal> signals = Map.of(
                HEAD_ACTION_ID, IntSignal.create(new long[]{2, 1}, headCommand, headCommand),
                MOVE_ACTION_ID, IntSignal.create(new long[]{2, 1}, 0, 0)
        );

        // When decode command
        List<RobotCommands> cmd = function.commands(signals, model, model);

        // Then ...
        assertNotNull(cmd);
        assertThat(cmd, hasSize(2));
        assertTrue(cmd.getFirst().isHalt());
        assertTrue(cmd.getLast().isHalt());
        assertEquals(expectedHead, cmd.getFirst().scanDirection());
        assertEquals(expectedHead, cmd.getLast().scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1}) R{2} headCmd={3} moveCmd={4}")
    @MethodSource("dataDecodeCommandRotation")
    void testCommandRotation(double robotX, double robotY, int robotDeg, int headCommand, int moveCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        int expectedDir = Complex.fromDeg((double) ((moveCommand - 1) * 360) / DEFAULT_NUM_ROTATIONS)
                .add(model.gridMap().direction())
                .toIntDeg();
        Map<String, Signal> signals = Map.of(
                HEAD_ACTION_ID, IntSignal.create(new long[]{2, 1}, headCommand, headCommand),
                MOVE_ACTION_ID, IntSignal.create(new long[]{2, 1}, moveCommand, moveCommand)
        );

        // When decode command
        List<RobotCommands> cmd = function.commands(signals, model, model);

        // Then ...
        assertNotNull(cmd);
        assertThat(cmd, hasSize(2));
        assertTrue(cmd.getFirst().isRotate());
        assertTrue(cmd.getLast().isRotate());
        assertEquals(expectedHead, cmd.getFirst().scanDirection());
        assertEquals(expectedHead, cmd.getLast().scanDirection());
        assertEquals(expectedDir, cmd.getFirst().rotationDirection());
        assertEquals(expectedDir, cmd.getLast().rotationDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1}) R{2} headCmd={3} moveCmd={4}")
    @MethodSource("dataDecodeCommandBackward")
    void testCommandsBackward(double robotX, double robotY, int robotDeg, int headCommand, int moveCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        Point2D target = function.target(moveCommand, model.gridMap());
        Map<String, Signal> signals = Map.of(
                HEAD_ACTION_ID, IntSignal.create(new long[]{2, 1}, headCommand, headCommand),
                MOVE_ACTION_ID, IntSignal.create(new long[]{2, 1}, moveCommand, moveCommand)
        );

        // When decode command
        List<RobotCommands> cmd = function.commands(signals, model, model);

        // Then ...
        assertNotNull(cmd);
        assertThat(cmd, hasSize(2));
        assertEquals(RobotStatusId.BACKWARD, cmd.getFirst().status());
        assertEquals(RobotStatusId.BACKWARD, cmd.getLast().status());
        assertEquals(expectedHead, cmd.getFirst().scanDirection());
        assertEquals(expectedHead, cmd.getLast().scanDirection());
        assertThat(cmd.getFirst().target(), pointCloseTo(target, MM));
        assertThat(cmd.getLast().target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1}) R{2} headCmd={3} moveCmd={4}")
    @MethodSource("dataDecodeCommandForward")
    void testCommandsForward(double robotX, double robotY, int robotDeg, int headCommand, int moveCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        Point2D target = function.target(moveCommand, model.gridMap());
        Map<String, Signal> signals = Map.of(
                HEAD_ACTION_ID, IntSignal.create(new long[]{2, 1}, headCommand, headCommand),
                MOVE_ACTION_ID, IntSignal.create(new long[]{2, 1}, moveCommand, moveCommand)
        );

        // When decode command
        List<RobotCommands> cmd = function.commands(signals, model, model);

        // Then ...
        assertNotNull(cmd);
        assertThat(cmd, hasSize(2));
        assertEquals(RobotStatusId.FORWARD, cmd.getFirst().status());
        assertEquals(RobotStatusId.FORWARD, cmd.getLast().status());
        assertEquals(expectedHead, cmd.getFirst().scanDirection());
        assertEquals(expectedHead, cmd.getLast().scanDirection());
        assertThat(cmd.getFirst().target(), pointCloseTo(target, MM));
        assertThat(cmd.getLast().target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "5,0.20,0.25,20",
            "5,0.20,0.30,16",
            "9,0.20,0.30,72"
    })
    void testCreate(int gridSize, double gridStep, double hideRadius, int numPoints) {
        DLActionFunction function = create(DEFAULT_NUM_ROTATIONS, DEFAULT_NUM_HEAD_ROTATIONS, gridSize, gridStep, hideRadius);

        // Then the map should contain n points
        assertEquals(DEFAULT_NUM_ROTATIONS, function.numRotations());
        assertEquals(DEFAULT_NUM_HEAD_ROTATIONS, function.numHeadRotations());
        assertThat(function.indicesMap(), hasSize(numPoints));
    }

    @ParameterizedTest
    @CsvSource({
            "5,0.20,0.25,20",
            "5,0.20,0.30,16",
            "9,0.20,0.30,72"
    })
    void testCreateIndices(int gridSize, double gridStep, double hideRadius, int numPoints) {
        List<Point2D> map = createIndicesMap(gridSize, gridStep, hideRadius);

        // Then the map should contain n points
        assertThat(map, hasSize(numPoints));
        assertThat(map, hasItem(
                pointCloseTo(
                        -(gridSize - 1) * gridStep / 2,
                        -(gridSize - 1) * gridStep / 2,
                        MM)));
        assertThat(map, hasItem(
                pointCloseTo(
                        (gridSize - 1) * gridStep / 2,
                        -(gridSize - 1) * gridStep / 2,
                        MM)));
        assertThat(map, hasItem(
                pointCloseTo(
                        -(gridSize - 1) * gridStep / 2,
                        (gridSize - 1) * gridStep / 2,
                        MM)));
        assertThat(map, hasItem(
                pointCloseTo(
                        (gridSize - 1) * gridStep / 2,
                        (gridSize - 1) * gridStep / 2,
                        MM)));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1}) R{2} headCmd={3} moveCmd={4}")
    @MethodSource("dataDecodeCommandBackward")
    void testDecodeCommandBackward(double robotX, double robotY, int robotDeg, int headCommand, int moveCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        Point2D target = function.target(moveCommand, model.gridMap());

        // When decode command
        RobotCommands cmd = function.decodeCommand(headCommand, moveCommand, model);

        // Then ...
        assertNotNull(cmd);
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        assertEquals(expectedHead, cmd.scanDirection());
        assertThat(cmd.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1}) R{2} headCmd={3} moveCmd={4}")
    @MethodSource("dataDecodeCommandForward")
    void testDecodeCommandForward(double robotX, double robotY, int robotDeg, int headCommand, int moveCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        Point2D target = function.target(moveCommand, model.gridMap());

        // When decode command
        RobotCommands cmd = function.decodeCommand(headCommand, moveCommand, model);

        // Then ...
        assertNotNull(cmd);
        assertEquals(RobotStatusId.FORWARD, cmd.status());
        assertEquals(expectedHead, cmd.scanDirection());
        assertThat(cmd.target(), pointCloseTo(target, MM));
    }

    @ParameterizedTest(name = "[{index}], Robot @({0},{1}) R{2}, command={3}")
    @MethodSource("dataHeadAngle")
    void testDecodeCommandHalt(double robotX, double robotY, int robotDeg, int headCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        // When decode command
        RobotCommands cmd = function.decodeCommand(headCommand, 0, model);
        // Then ...
        assertNotNull(cmd);
        assertTrue(cmd.isHalt());
        assertEquals(expectedHead, cmd.scanDirection());
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1}) R{2} headCmd={3} moveCmd={4}")
    @MethodSource("dataDecodeCommandRotation")
    void testDecodeCommandRotation(double robotX, double robotY, int robotDeg, int headCommand, int moveCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expectedHead = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expectedHead = clamp(expectedHead, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        int expectedDir = Complex.fromDeg((double) ((moveCommand - 1) * 360) / DEFAULT_NUM_ROTATIONS)
                .add(model.gridMap().direction())
                .toIntDeg();

        // When decode command
        RobotCommands cmd = function.decodeCommand(headCommand, moveCommand, model);

        // Then ...
        assertNotNull(cmd);
        assertTrue(cmd.isRotate());
        assertEquals(expectedHead, cmd.scanDirection());
        assertEquals(expectedDir, cmd.rotationDirection());
    }

    @Test
    void testDefaultCreate() {
        // Then the map should contain n points
        assertEquals(DEFAULT_NUM_ROTATIONS, function.numRotations());
        assertEquals(DEFAULT_NUM_HEAD_ROTATIONS, function.numHeadRotations());
        assertThat(function.indicesMap(), hasSize(DEFAULT_NUM_MOVE_ACTIONS));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2}")
    @CsvSource({
            "0,0,90",
            "1,1,90"
    })
    @MethodSource({
            "dataE"
    })
    void testETargetNE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);
        Point2D centre = model.gridMap().center();

        // When ...
        Point2D target = function.target(FORWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataE",
    })
    void testETargetNW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_NW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_NW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataE",
    })
    void testETargetSE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SE, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_SE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataE",
    })
    void testETargetSW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_SW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -3,-3, 37",
            "0,0, 0, 3,-3, 67",
            "0,0, 0, -3,3, 962",
            "0,0, 0, 3,3, 992",

            "0,0, 90, -3,-3, 67",
            "0,0, 90, 3,-3, 992",
            "0,0, 90, -3,3, 37",
            "0,0, 90, 3,3, 962",

            "0,0, -90, -3,-3, 962",
            "0,0, -90, 3,-3, 37",
            "0,0, -90, -3,3, 992",
            "0,0, -90, 3,3, 67",

            "0,0, -180, -3,-3, 992",
            "0,0, -180, 3,-3, 962",
            "0,0, -180, -3,3, 67",
            "0,0, -180, 3,3, 37",

            "1,1, 0, -2,-2, 37",
            "1,1, 0, 4,4, 992",
            "1,1, 90, -2,-2, 67",
            "1,1, 90, 4,4, 962",
            "1,1, -90, -2,-2, 962",
            "1,1, -90, 4,4, 67",
            "1,1, -180, -2,-2, 992",
            "1,1, -180, 4,4, 37",
    })
    void testForwardIndex(double robotX, double robotY, int robotDeg, double targetX, double targetY, int expectedIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        Point2D target = new Point2D.Double(targetX, targetY);
        RobotCommands cmd = RobotCommands.forward(target);
        // When ...
        int idx = function.moveIndex(cmd, model);

        // Then ...
        assertEquals(expectedIndex, idx);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -3,-3, 37",
            "0,0, 0, 3,-3, 67",
            "0,0, 0, -3,3, 962",
            "0,0, 0, 3,3, 992",

            "0,0, 90, -3,-3, 67",
            "0,0, 90, 3,-3, 992",
            "0,0, 90, -3,3, 37",
            "0,0, 90, 3,3, 962",

            "0,0, -90, -3,-3, 962",
            "0,0, -90, 3,-3, 37",
            "0,0, -90, -3,3, 992",
            "0,0, -90, 3,3, 67",

            "0,0, -180, -3,-3, 992",
            "0,0, -180, 3,-3, 962",
            "0,0, -180, -3,3, 67",
            "0,0, -180, 3,3, 37",

            "1,1, 0, -2,-2, 37",
            "1,1, 0, 4,4, 992",
            "1,1, 90, -2,-2, 67",
            "1,1, 90, 4,4, 962",
            "1,1, -90, -2,-2, 962",
            "1,1, -90, 4,4, 67",
            "1,1, -180, -2,-2, 992",
            "1,1, -180, 4,4, 37",
    })
    void testForwardMask(double robotX, double robotY, int robotDeg, double targetX, double targetY, int expectedIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        Point2D target = new Point2D.Double(targetX, targetY);
        RobotCommands cmd = RobotCommands.forward(target);
        // When ...
        Map<String, INDArray> masks = function.actionMasks(
                List.of(model, model),
                List.of(cmd, cmd));

        // Then ...
        assertNotNull(masks);
        assertThat(masks, hasKey(MOVE_ACTION_ID));
        assertThat(masks, hasKey(HEAD_ACTION_ID));
        INDArray mask = masks.get(MOVE_ACTION_ID);
        INDArray expected = Nd4j.zeros(2, 1 + DEFAULT_NUM_ROTATIONS + 2 * DEFAULT_NUM_MOVE_ACTIONS);
        expected.putScalar(0, expectedIndex, 1);
        expected.putScalar(1, expectedIndex, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest
    @MethodSource({
            "dataNW",
            "dataNE",
            "dataE",
            "dataS",
            "dataW",
    })
    void testHaltIndex(double robotX, double robotY, int robotDeg) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        RobotCommands cmd = RobotCommands.halt();
        // When ...
        int idx = function.moveIndex(cmd, model);

        // Then ...
        assertEquals(0, idx);
    }

    @ParameterizedTest(name = "[{index}] Robot R{0}, H {1} DEG")
    @CsvSource({
            "0, -90, 0",
            "0, 0, 6",
            "0, 90, 12",

            "90, -90, 0",
            "90, 0, 6",
            "90, 90, 12",

            "-90, -90, 0",
            "-90, 0, 6",
            "-90, 90, 12",

            "15, -90, 1",
            "15, 0, 7",
            "15, 90, 12",

            "-15, -90, 0",
            "-15, 0, 5",
            "-15, 90, 11",

            "75, -90, 0",
            "75, 0, 5",
            "75, 90, 11",

            "105, -90, 1",
            "105, 0, 7",
            "105, 90, 12",

            "-180, -90, 0",
            "-180, 0, 6",
            "-180, 90, 12",

            "165, -90, 0",
            "165, 0, 5",
            "165, 90, 11",

            "-165, -90, 1",
            "-165, 0, 7",
            "-165, 90, 12",
    })
    void testHaltActionMasks(int robotDeg, int headDeg, int expectedCommand) {
        // Given the world model
        createWorldModel(0, 0, robotDeg);
        RobotCommands cmd = RobotCommands.halt(headDeg);

        // When ...
        Map<String, INDArray> masks = function.actionMasks(
                List.of(model, model),
                List.of(cmd, cmd));

        // Then ...
        assertNotNull(masks);
        assertThat(masks, hasKey(MOVE_ACTION_ID));
        assertThat(masks, hasKey(HEAD_ACTION_ID));
        INDArray mask = masks.get(MOVE_ACTION_ID);
        INDArray expected = Nd4j.zeros(2, 1 + DEFAULT_NUM_ROTATIONS + 2 * DEFAULT_NUM_MOVE_ACTIONS);
        expected.putScalar(0, 0, 1);
        expected.putScalar(1, 0, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));

        mask = masks.get(HEAD_ACTION_ID);
        expected = Nd4j.zeros(2, DEFAULT_NUM_HEAD_ROTATIONS);
        expected.putScalar(0, expectedCommand, 1);
        expected.putScalar(1, expectedCommand, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));
    }

    @ParameterizedTest(name = "[{index}] Robot R{0}, H {1} DEG")
    @CsvSource({
            "0, -90, 0",
            "0, 0, 6",
            "0, 90, 12",

            "90, -90, 0",
            "90, 0, 6",
            "90, 90, 12",

            "-90, -90, 0",
            "-90, 0, 6",
            "-90, 90, 12",

            "15, -90, 1",
            "15, 0, 7",
            "15, 90, 12",

            "-15, -90, 0",
            "-15, 0, 5",
            "-15, 90, 11",

            "75, -90, 0",
            "75, 0, 5",
            "75, 90, 11",

            "105, -90, 1",
            "105, 0, 7",
            "105, 90, 12",

            "-180, -90, 0",
            "-180, 0, 6",
            "-180, 90, 12",

            "165, -90, 0",
            "165, 0, 5",
            "165, 90, 11",

            "-165, -90, 1",
            "-165, 0, 7",
            "-165, 90, 12",
    })
    void testHaltActions(int robotDeg, int headDeg, int expectedCommand) {
        // Given the world model
        createWorldModel(0, 0, robotDeg);
        RobotCommands cmd = RobotCommands.halt(headDeg);

        // When ...
        Map<String, Signal> actions = function.actions(cmd, model);
        // Then ...
        assertNotNull(actions);
        assertThat(actions.size(), is(2));
        assertThat(actions, hasKey(MOVE_ACTION_ID));
        assertThat(actions, hasKey(HEAD_ACTION_ID));

        assertThat(actions.get(MOVE_ACTION_ID), isA(ArraySignal.class));
        assertThat(actions.get(HEAD_ACTION_ID), isA(ArraySignal.class));

        ArraySignal value = (ArraySignal) actions.get(MOVE_ACTION_ID);
        assertThat(value.toINDArray(), matrixCloseTo(new long[]{1, 1}, EPSILON, 0));

        value = (ArraySignal) actions.get(HEAD_ACTION_ID);
        assertThat(value.toINDArray(), matrixCloseTo(new long[]{1, 1}, EPSILON, expectedCommand));
    }

    @ParameterizedTest(name = "[{index}], Robot @({0},{1}) R{2}, command={3}")
    @CsvSource({
            "0,0, 0, 0",
            "0,0, 0, 1",
            "0,0, 0, 2",
            "0,0, 0, 3",
            "0,0, 0, 4",
            "0,0, 0, 5",
    })
    @MethodSource("dataHeadAngle")
    void testHeadAngle(double robotX, double robotY, int robotDeg, int headCommand) {
        createWorldModel(robotX, robotY, robotDeg);
        // Then ...
        Complex relHeadAngle = Complex.fromDeg((double) (headCommand * 180) / (DEFAULT_NUM_HEAD_ROTATIONS - 1) - 90);
        Complex absHeadAngle = model.gridMap().direction().add(relHeadAngle);
        int expected = absHeadAngle.sub(model.robotStatus().direction()).toIntDeg();
        expected = clamp(expected, -DEFAULT_HEAD_FOV_DEG / 2, DEFAULT_HEAD_FOV_DEG / 2);
        assertEquals(expected, function.headAngle(headCommand, model));
    }

    @ParameterizedTest
    @CsvSource({
            "0, -90",
            "1,-75",
            "2, -60",
            "3, -45",
            "4, -30",
            "5, -15",
            "6, 0",
            "7, 15",
            "8, 30",
            "9, 45",
            "10, 60",
            "11, 75",
            "12, 90",
    })
    void testHeadAngle(int command, int expectedHeadDeg) {
        // Then ...
        assertThat(function.headAngle(command), angleCloseTo(expectedHeadDeg));
    }

    @ParameterizedTest
    @CsvSource({
            "-105, 0",
            "-90, 0",
            "-83, 0",
            "-82, 1",
            "-75, 1",
            "-60, 2",
            "-45, 3",
            "-30, 4",
            "-15, 5",
            "-8, 5",
            "-7, 6",
            "0, 6",
            "7, 6",
            "8, 7",
            "15, 7",
            "30, 8",
            "45, 9",
            "60, 10",
            "75, 11",
            "82, 11",
            "83, 12",
            "90, 12",
            "105, 12",
    })
    void testHeadIndex(int headDeg, int expectedCommand) {
        // When ...
        int cmd = function.headIndex(Complex.fromDeg(headDeg));

        // Then ...
        assertEquals(expectedCommand, cmd);
    }

    @ParameterizedTest(name = "[{index}] Robot R{0}, H {1} DEG")
    @CsvSource({
            "0, -90, 0",
            "0, 0, 6",
            "0, 90, 12",

            "90, -90, 0",
            "90, 0, 6",
            "90, 90, 12",

            "-90, -90, 0",
            "-90, 0, 6",
            "-90, 90, 12",

            "15, -90, 1",
            "15, 0, 7",
            "15, 90, 12",

            "-15, -90, 0",
            "-15, 0, 5",
            "-15, 90, 11",

            "75, -90, 0",
            "75, 0, 5",
            "75, 90, 11",

            "105, -90, 1",
            "105, 0, 7",
            "105, 90, 12",

            "-180, -90, 0",
            "-180, 0, 6",
            "-180, 90, 12",

            "165, -90, 0",
            "165, 0, 5",
            "165, 90, 11",

            "-165, -90, 1",
            "-165, 0, 7",
            "-165, 90, 12",
    })
    void testHeadIndex(int robotDeg, int headDeg, int expectedCommand) {
        createWorldModel(0, 0, robotDeg);

        // When ...
        int cmd = function.headIndex(RobotCommands.halt(headDeg), model);

        // Then ...
        assertEquals(expectedCommand, cmd);

        // When ...
        cmd = function.headIndex(RobotCommands.rotate(headDeg, 0), model);

        // Then ...
        assertEquals(expectedCommand, cmd);

        // When ...
        cmd = function.headIndex(RobotCommands.forward(headDeg, new Point2D.Double()), model);

        // Then ...
        assertEquals(expectedCommand, cmd);

        // When ...
        cmd = function.headIndex(RobotCommands.backward(headDeg, new Point2D.Double()), model);

        // Then ...
        assertEquals(expectedCommand, cmd);
    }

    @ParameterizedTest
    @CsvSource({
            "0, false",
            "1,false",
            "35,false",
            "36,false",
            "37,true",
            "38,true",
            "991,true",
            "992,true",
            "993,false",
            "994,false",
            "1947,false",
            "1948,false"
    })
    void testIsForward(int command, boolean expected) {
        // Then ...
        assertEquals(expected, function.isForward(command));
    }

    @ParameterizedTest
    @CsvSource({
            "0, true",
            "1,false",
            "1947,false",
            "1948,false"
    })
    void testIsHalt(int command, boolean expected) {
        // Then ...
        assertEquals(expected, function.isHalt(command));
    }

    @ParameterizedTest
    @CsvSource({
            "0, false",
            "1,true",
            "2,true",
            "35,true",
            "36,true",
            "37,false",
            "1947,false",
            "1948,false"
    })
    void testIsRotate(int command, boolean expected) {
        // Then ...
        assertEquals(expected, function.isRotate(command));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2}")
    @MethodSource({
            "dataNE",
            "dataNW"
    })
    void testNTargetNE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);
        Point2D centre = model.gridMap().center();

        // When ...
        Point2D target = function.target(FORWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataNE",
            "dataNW"
    })
    void testNTargetNW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_NW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_NW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataNE",
            "dataNW"
    })
    void testNTargetSE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SE, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_SE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataNE",
            "dataNW"
    })
    void testNTargetSW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_SW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0, 1",
            "0,0, 0, 90, 10",
            "0,0, 0, 180, 19",
            "0,0, 0, 270, 28",
            "0,0, 0, 350, 36",

            "0,0, 90, 0, 28",
            "0,0, 90, 90, 1",
            "0,0, 90, 180, 10",
            "0,0, 90, 270, 19",

            "0,0, -90, 0, 10",
            "0,0, -90, 90, 19",
            "0,0, -90, 180, 28",
            "0,0, -90, 270, 1",

            "0,0, -90, 0, 10",
            "0,0, -90, 90, 19",
            "0,0, -90, 180, 28",
            "0,0, -90, 270, 1",

            "0,0, -180, 0, 19",
            "0,0, -180, 90, 28",
            "0,0, -180, 180, 1",
            "0,0, -180, 270, 10",

            "0,0, 44, 0, 1",
            "0,0, 44, 90, 10",
            "0,0, 44, 180, 19",
            "0,0, 44, 270, 28",
            "0,0, 44, 350, 36",

            "1,1, 0, 0, 1",
            "1,1, 0, 90, 10",
            "1,1, 0, 180, 19",
            "1,1, 0, 270, 28",
            "1,1, 0, 350, 36",
    })
    void testRotateIndex(double robotX, double robotY, int robotDeg, int rotDeg, int expectedIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        RobotCommands cmd = RobotCommands.rotate(rotDeg);
        // When ...
        int idx = function.moveIndex(cmd, model);

        // Then ...
        assertEquals(expectedIndex, idx);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0, 1",
            "0,0, 0, 90, 10",
            "0,0, 0, 180, 19",
            "0,0, 0, 270, 28",
            "0,0, 0, 350, 36",

            "0,0, 90, 0, 28",
            "0,0, 90, 90, 1",
            "0,0, 90, 180, 10",
            "0,0, 90, 270, 19",

            "0,0, -90, 0, 10",
            "0,0, -90, 90, 19",
            "0,0, -90, 180, 28",
            "0,0, -90, 270, 1",

            "0,0, -90, 0, 10",
            "0,0, -90, 90, 19",
            "0,0, -90, 180, 28",
            "0,0, -90, 270, 1",

            "0,0, -180, 0, 19",
            "0,0, -180, 90, 28",
            "0,0, -180, 180, 1",
            "0,0, -180, 270, 10",

            "0,0, 44, 0, 1",
            "0,0, 44, 90, 10",
            "0,0, 44, 180, 19",
            "0,0, 44, 270, 28",
            "0,0, 44, 350, 36",

            "1,1, 0, 0, 1",
            "1,1, 0, 90, 10",
            "1,1, 0, 180, 19",
            "1,1, 0, 270, 28",
            "1,1, 0, 350, 36",
    })
    void testRotateMask(double robotX, double robotY, int robotDeg, int rotDeg, int expectedMoveIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        RobotCommands cmd = RobotCommands.rotate(rotDeg);
        // When ...
        Map<String, INDArray> masks = function.actionMasks(
                List.of(model, model),
                List.of(cmd, cmd));

        // Then ...

        // Then ...
        assertNotNull(masks);
        assertThat(masks, hasKey(MOVE_ACTION_ID));
        assertThat(masks, hasKey(HEAD_ACTION_ID));
        INDArray mask = masks.get(MOVE_ACTION_ID);
        INDArray expected = Nd4j.zeros(2, 1 + DEFAULT_NUM_ROTATIONS + 2 * DEFAULT_NUM_MOVE_ACTIONS);
        expected.putScalar(0, expectedMoveIndex, 1);
        expected.putScalar(1, expectedMoveIndex, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));
        /*
        mask = masks.get(HEAD_ACTION_ID);
        expected = Nd4j.zeros(2, DEFAULT_NUM_HEAD_ROTATIONS);
        expected.putScalar(0, 9, 1);
        expected.putScalar(1, 9, 1);
        assertThat(mask, matrixCloseTo(expected, 1e-3));

         */
    }

    @ParameterizedTest
    @MethodSource("dataRotation")
    void testRotation(int command, int expectedDeg) {
        // Then ...
        assertThat(function.rotation(command), angleCloseTo(expectedDeg));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "4, 0",
            "5, 1",
            "90, 9",
            "174, 17",
            "175, 18",
            "180, 18",
            "185, 18",
            "186, 19",
            "270, 27",
            "344, 34",
            "345, 35",
            "350, 35",
            "354, 35",
            "355, 0",
    })
    void testRotationIndex(int dirDeg, int expectedCommand) {
        // When ...
        int cmd = function.rotationIndex(Complex.fromDeg(dirDeg));

        // Then ...
        assertEquals(expectedCommand, cmd);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2}")
    @MethodSource({
            "dataS",
    })
    void testSTargetNE(double robotX, double robotY, int robotDeg) {
        // Given a robot directed to south (180 DEG +- 45)
        createWorldModel(robotX, robotY, robotDeg);
        Point2D centre = model.gridMap().center();

        // When getting target location of forward command to NE of grid map
        Point2D target = function.target(FORWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));

        // When getting target location of backward command to NE of grid map
        target = function.target(BACKWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataS",
    })
    void testSTargetNW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_NW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_NW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataS",
    })
    void testSTargetSE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SE, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_SE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataS",
    })
    void testSTargetSW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_SW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));
    }

    @Test
    void testSpec() {
        // Then the map should contain n points
        Map<String, SignalSpec> spec = function.spec();
        assertNotNull(spec);
        assertThat(spec, hasKey(HEAD_ACTION_ID));
        assertThat(spec, hasKey(MOVE_ACTION_ID));

        assertThat(spec.get(HEAD_ACTION_ID), isA(IntSignalSpec.class));
        IntSignalSpec signalSpec = (IntSignalSpec) spec.get(HEAD_ACTION_ID);
        assertArrayEquals(new long[]{1}, signalSpec.shape());
        assertEquals(DEFAULT_NUM_HEAD_ROTATIONS, signalSpec.numValues());

        assertThat(spec.get(MOVE_ACTION_ID), isA(IntSignalSpec.class));
        signalSpec = (IntSignalSpec) spec.get(MOVE_ACTION_ID);
        assertArrayEquals(new long[]{1}, signalSpec.shape());
        assertEquals(1 + DEFAULT_NUM_ROTATIONS + DEFAULT_NUM_MOVE_ACTIONS * 2, signalSpec.numValues());
    }

    @ParameterizedTest
    @CsvSource({
            "37, -3,-3",
            "52, 0,-3",
            "67, 3,-3",
            "962, -3,3",
            "977, 0,3",
            "992, 3,3",

            "993, -3,-3",
            "1008, 0,-3",
            "1023, 3,-3",
            "1918, -3,3",
            "1933, 0,3",
            "1948, 3,3"
    })
    void testTarget(int command, double xTarget, double yTarget) {
        // Then ...
        assertThat(function.target(command), pointCloseTo(xTarget, yTarget, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "-3,-3, 0",
            "-2.901,-3, 0",
            "-2.899,-3, 1",
            "-2.8,-3, 1",
            "-0.101,-3, 14",
            "-0.099,-3, 15",
            "0,-3, 15",
            "0.099,-3, 15",
            "0.101,-3, 16",
            "2.899,-3, 29",
            "2.901,-3, 30",
            "3,-3, 30",

            "-3,3, 925",
            "-2.901,3, 925",
            "-2.899,3, 926",
            "-0.101,3, 939",
            "-0.099,3, 940",
            "0,3, 940",
            "0.099,3, 940",
            "0.101,3, 941",
            "2.899,3, 954",
            "2.901,3, 955",
            "3,3, 955",
    })
    void testTargetIndex(double x, double y, int expectedIndex) {
        // When ...
        int idx = function.targetIndex(new Point2D.Double(x, y));

        // Then ...
        assertEquals(expectedIndex, idx);
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, -3,-3, 0",
            "0,0, 0, 3,-3, 30",
            "0,0, 0, -3,3, 925",
            "0,0, 0, 3,3, 955",

            "0,0, 90, -3,-3, 30",
            "0,0, 90, 3,-3, 955",
            "0,0, 90, -3,3, 0",
            "0,0, 90, 3,3, 925",

            "0,0, -90, -3,-3, 925",
            "0,0, -90, 3,-3, 0",
            "0,0, -90, -3,3, 955",
            "0,0, -90, 3,3, 30",

            "0,0, -180, -3,-3, 955",
            "0,0, -180, 3,-3, 925",
            "0,0, -180, -3,3, 30",
            "0,0, -180, 3,3, 0",

            "1,1, 0, -2,-2, 0",
            "1,1, 0, 4,4, 955",
            "1,1, 90, -2,-2, 30",
            "1,1, 90, 4,4, 925",
            "1,1, -90, -2,-2, 925",
            "1,1, -90, 4,4, 30",
            "1,1, -180, -2,-2, 955",
            "1,1, -180, 4,4, 0",
    })
    void testTargetIndexAbsolute(double robotX, double robotY, int robotDeg, double targetX, double targetY, int expectedIndex) {
        // Given the world model
        createWorldModel(robotX, robotY, robotDeg);
        // When ...
        int idx = function.targetIndex(new Point2D.Double(targetX, targetY), model);

        // Then ...
        assertEquals(expectedIndex, idx);
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2}")
    @MethodSource({
            "dataW"
    })
    void testWTargetNE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);
        Point2D centre = model.gridMap().center();

        // When ...
        Point2D target = function.target(FORWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_NE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataW",
    })
    void testWTargetNW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_NW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_NW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() - 3, centre.getY() - 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataW",
    })
    void testWTargetSE(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SE, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));

        // When ...
        target = function.target(BACKWARD_SE, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() + 3, MM));
    }

    @ParameterizedTest(name = "[{index}] Robot @({0},{1} R{2} command={3}")
    @MethodSource({
            "dataW",
    })
    void testWTargetSW(double robotX, double robotY, int robotDeg) {
        createWorldModel(robotX, robotY, robotDeg);

        // When ...
        Point2D target = function.target(FORWARD_SW, model.gridMap());

        // Then ...
        Point2D centre = model.gridMap().center();
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));

        // When ...
        target = function.target(BACKWARD_SW, model.gridMap());

        // Then ...
        assertThat(target, pointCloseTo(centre.getX() + 3, centre.getY() - 3, MM));
    }
}