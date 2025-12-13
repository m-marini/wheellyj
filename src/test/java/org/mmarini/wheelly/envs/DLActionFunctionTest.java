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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.envs.DLActionFunction.MOVE_ACTION_ID;
import static org.mmarini.wheelly.envs.DLActionFunction.SENSOR_ACTION_ID;

class DLActionFunctionTest {
    public static final int NUM_DIRECTIONS = 24;
    public static final int NUM_SPEEDS = 5;
    public static final int NUM_MOVE_ACTIONS = NUM_DIRECTIONS * NUM_SPEEDS;
    public static final int NUM_SENSOR_DIRECTIONS = 9;
    public static final double EPSILON = 1e-5;
    public static final int SEED = 1234;

    static Stream<Arguments> dataActionSignals() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359)
                .uniform(-90, 90)
                .uniform(-90, 90)
                .build(100);
    }

    private DLActionFunction dataGen;

    @BeforeEach
    void setUp() {
        this.dataGen = new DLActionFunction(NUM_DIRECTIONS, NUM_SPEEDS, NUM_SENSOR_DIRECTIONS);

    }

    @ParameterizedTest(name = "[{index}] R{0} move={1}, scan={2}")
    @CsvSource({
            "0, 0,0, -90,false,-180,-60",
            "0, 0,1, -90,false,-180,-60",
            "0, 0,2, -67,false,-180,-60",
            "0, 0,4, 0,false,-180,-60",
            "0, 0,6, 68,false,-180,-60",
            "0, 0,7, 90,false,-180,-60",
            "0, 0,8, 90,false,-180,-60",

            "0, 1,4, 0,false,-180,-30",
            "0, 2,4, 0,false,-180,0",
            "0, 3,4, 0,false,-180,30",
            "0, 4,4, 0,false,-180,60",

            "0, 5,4, 0,false,-165,-60",
            "0, 6,4, 0,false,-165,-30",
            "0, 7,4, 0,false,-165,0",
            "0, 8,4, 0,false,-165,30",
            "0, 9,4, 0,false,-165,60",

            "0, 60,4, 0,false,0,-60",
            "0, 61,4, 0,false,0,-30",
            "0, 62,4, 0,true,0,0",
            "0, 63,4, 0,false,0,30",
            "0, 64,4, 0,false,0,60",

            "0, 115,4, 0,false,165,-60",
            "0, 116,4, 0,false,165,-30",
            "0, 117,4, 0,false,165,0",
            "0, 118,4, 0,false,165,30",
            "0, 119,4, 0,false,165,60",

            "-44, 0,0, -90,false,-180,-60",
            "-44, 0,4, 44,false,-180,-60",
            "-44, 0,8, 90,false,-180,-60",

            "44, 0,0, -90,false,-180,-60",
            "44, 0,4, -44,false,-180,-60",
            "44, 0,8, 90,false,-180,-60",

            "-46, 0,0, -90,false,90,-60",
            "-46, 0,4, -44,false,90,-60",
            "-46, 0,8, 90,false,90,-60",

            "-90, 0,0, -90,false,90,-60",
            "-90, 0,4, 0,false,90,-60",
            "-90, 0,8, 90,false,90,-60",

            "-180, 0,0, -90,false,0,-60",
            "-180, 0,4, 0,false,0,-60",
            "-180, 0,8, 90,false,0,-60",})
    void testActionCommand(int directionDeg, int moveAction, int scanAction,
                           int expScan, boolean expHalt, int expDir, int expSpeed) {
        // Given
        WorldModel model = new WorldModelBuilder()
                .robotDir(directionDeg)
                .build();
        List<WorldModel> states = List.of(model, model);
        Map<String, Signal> signals = Map.of(
                MOVE_ACTION_ID, new ArraySignal(
                        Nd4j.createFromArray(moveAction, moveAction)
                                .castTo(DataType.FLOAT)
                                .reshape(2, 1)),
                SENSOR_ACTION_ID, new ArraySignal(
                        Nd4j.createFromArray(scanAction, scanAction)
                                .castTo(DataType.FLOAT)
                                .reshape(2, 1))
        );
        // When ...
        List<RobotCommands> commands = dataGen.actions(states, signals);

        // Then ...
        assertThat(commands, hasSize(2));
        for (int i = 0; i < 2; i++) {
            RobotCommands command = commands.get(i);
            assertTrue(command.scan());
            assertEquals(expHalt, command.halt());
            assertEquals(!expHalt, command.move());
            assertEquals(expScan, command.scanDirection().toIntDeg());
            assertEquals(expDir, command.moveDirection().toIntDeg());
            assertEquals(expSpeed, command.speed());
        }
    }

    @ParameterizedTest
    @MethodSource("dataActionSignals")
    void testActionSignals(int directionDeg, int sensorDeg, int scanDeg) {
        // Given
        WorldModel model = new WorldModelBuilder()
                .robotDir(directionDeg)
                .sensorDir(sensorDeg)
                .build();
        List<WorldModel> states = List.of(model, model);
        RobotCommands command = RobotCommands.scan(Complex.fromDeg(scanDeg));
        List<RobotCommands> commands = List.of(command, command);

        // When ...
        Map<String, Signal> signals = dataGen.actions(states, commands);

        // Then ...
        assertThat(signals, hasKey(MOVE_ACTION_ID));
        INDArray move = signals.get(MOVE_ACTION_ID).toINDArray();
        Complex mapDir = model.gridMap().direction();
        Complex moveDir = command.moveDirection().sub(mapDir);
        int expectedMoveIndex = dataGen.moveIndex(moveDir, command.speed());
        assertThat(expectedMoveIndex, allOf(greaterThanOrEqualTo(0), lessThan(NUM_MOVE_ACTIONS)));
        assertThat(move, matrixCloseTo(new long[]{2, 1}, EPSILON, expectedMoveIndex, expectedMoveIndex));

        // And
        assertThat(signals, hasKey(SENSOR_ACTION_ID));
        INDArray sensor = signals.get(SENSOR_ACTION_ID).toINDArray();

        Complex sensorMapDir = command.scanDirection().sub(mapDir);
        int sensorIndex = dataGen.sensorIndex(sensorMapDir);
        assertThat(sensor, matrixCloseTo(new long[]{2, 1}, EPSILON, sensorIndex, sensorIndex));
    }

    @ParameterizedTest(name = "[{index}] move={1}")
    @CsvSource({
            "0, -180",
            "1, -180",
            "2, -180",
            "3, -180",
            "4, -180",

            "5, -165",
            "6, -165",
            "7, -165",
            "8, -165",
            "9, -165",

            "10, -150",
            "11, -150",
            "12, -150",
            "13, -150",
            "14, -150",

            "62 ,0",

            "115, 165",
            "116, 165",
            "117, 165",
            "118, 165",
            "119, 165",
    })
    void testDirection(int moveAction, int expDirDeg) {
        assertEquals(expDirDeg, dataGen.direction(moveAction).toIntDeg());
    }

    @ParameterizedTest
    @CsvSource({
            "-180, 0",
            "-173, 0",
            "-172, 1",
            "-158, 1",
            "-157, 2",
            "-143, 2",
            "-142, 3",
            "-128, 3",
            "-127, 4",
            "-8, 11",
            "-7, 12",
            "7, 12",
            "8, 13",
            "157, 22",
            "158, 23",
            "172, 23",
            "173, 0",
            "179, 0",
    })
    void testDirectionIndex(int directionDeg, int expectedIndex) {
        assertEquals(expectedIndex, dataGen.directionIndex(Complex.fromDeg(directionDeg)));
    }

    @ParameterizedTest
    @CsvSource({
            "-180, -60, 0",
            "-180, -30, 1",
            "-180, 0, 2",
            "-180, 30, 3",
            "-180, 60, 4",
            "0, -60, 60",
            "0, -30, 61",
            "0, 0, 62",
            "0, 30, 63",
            "0, 60, 64",
            "179, -60, 0",
            "179, -30, 1",
            "179, 0, 2",
            "179, 30, 3",
            "179, 60, 4",
    })
    void testMoveIndex(int directionDeg, int speed, int expectedIndex) {
        assertEquals(expectedIndex, dataGen.moveIndex(Complex.fromDeg(directionDeg), speed));
    }

    @ParameterizedTest(name = "[{index}] move={1}")
    @CsvSource({
            "0, -135",
            "1, -101",
            "2, -67",
            "3, -34",
            "4, 0",
            "5, 34",
            "6, 68",
            "7, 101",
            "8, 135",
    })
    void testSensorDirection(int scanAction, int expDirDeg) {
        assertEquals(expDirDeg, dataGen.sensorDirection(scanAction).toIntDeg());
    }

    @ParameterizedTest
    @CsvSource({
            "-135, 0",
            "-105, 0",
            "-104, 1",
            "-76, 1",
            "-75, 2",
            "-46, 2",
            "-45, 3",
            "-16, 3",
            "-15, 4",
            "14, 4",
            "15, 5",
            "44, 5",
            "45, 6",
            "74, 6",
            "75, 7",
            "104, 7",
            "105, 8",
            "135, 8",
    })
    void testSensorIndex(int sensorDeg, int expectedIndex) {
        assertEquals(expectedIndex, dataGen.sensorIndex(Complex.fromDeg(sensorDeg)));
    }

    @ParameterizedTest(name = "[{index}] scan={0}")
    @CsvSource({
            "0, -60",
            "1, -30",
            "2, 0",
            "3, 30",
            "4, 60",

            "5, -60",
            "6, -30",
            "7, 0",
            "8, 30",
            "9, 60",

            "115, -60",
            "116, -30",
            "117, 0",
            "118, 30",
            "119, 60",
    })
    void testSpeed(int moveAction, int expSpeed) {
        assertEquals(expSpeed, dataGen.speed(moveAction));
    }

    @ParameterizedTest
    @CsvSource({
            "-60, 0",
            "-37, 0",
            "-36, 1",
            "-13, 1",
            "-12, 2",
            "11, 2",
            "12, 3",
            "35, 3",
            "36, 4",
            "60, 4",
    })
    void testSpeedIndex(int speed, int expectedIndex) {
        assertEquals(expectedIndex, dataGen.speedIndex(speed));
    }

}