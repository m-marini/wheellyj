/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.objectives;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoveToLabelTest {

    public static final double OBSTACLE_DISTANCE = 1.2;
    public static final double DECAY = 1;
    public static final int MAP_SIZE = 15;
    public static final double GRID_SIZE = 0.2;
    public static final int ECHO_TIME = 100;

    /**
     * Returns the world model with the given robot status and obstacle at 1.2 m from the origin to the given direction
     *
     * @param robotDir    the robot direction
     * @param headDeg     the head direction
     * @param obstacleDir the obstacle direction
     */
    static WorldModel createState(Complex robotDir, Complex headDeg, Complex obstacleDir) {
        Point2D obstacleLocation = obstacleDir.at(new Point2D.Float(), OBSTACLE_DISTANCE);
        RadarMap map = RadarMap.empty(GridTopology.create(new Point2D.Float(), MAP_SIZE, MAP_SIZE,
                        GRID_SIZE))
                .updateCellAt(obstacleLocation.getX(), obstacleLocation.getY(), cell ->
                        cell.addEchogenic(ECHO_TIME, DECAY));
        RobotStatus status = RobotStatus.create(DEFAULT_ROBOT_SPEC, x -> 12)
                .setDirection(robotDir)
                .setSensorDirection(headDeg);
        Map<String, LabelMarker> markers = Map.of(
                "A", new LabelMarker("A", obstacleLocation, 1, 0, 0)
        );

        WorldModel model = mock();
        when(model.radarMap()).thenReturn(map);
        when(model.robotStatus()).thenReturn(status);
        when(model.markers()).thenReturn(markers);
        return model;
    }

    @ParameterizedTest(
            name = "[{index}] robot R{1},{2} obstacle R{3} move R{4}")
    @CsvSource({
            // obstacle in front
            "2, 0, 0, 0, 0, 30",
            // obstacle at left front
            "2, 0, 0, 45, 45, 30",
            // obstacle at right front
            "2, 0, 0, 315, 315, 30",
            // obstacle at the left
            "2, 0, 0, 90, 90, 30",
            // obstacle at right
            "2, 0, 0, 270, 270, 30",

            // obstacle out of the direction
            "0, 31, 0, 0, 0, 30",
            // obstacle out of the direction
            "0, 329, 0, 0, 0, 30",
            // sensor out of range
            "0, 0, 31, 0, 0, 30",
            // sensor out of range
            "0, 0, 329, 0, 0, 30",

            // speed out of range
            "0, 0, 0, 0, 0, 0",
            // speed out of range
            "0, 0, 0, 0, 0, 0",
            // speed out of range
            "0, 0, 0, 0, 0, 60",
            // speed out of range
            "0, 0, 0, 0, 0, 60",

    })
    void create(double expected,
                int robotDeg,
                int sensorDeg,
                int obstacleDeg,
                int actionDeg,
                int actionSpeed
    ) throws IOException {
        // Given a move to label goal
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + MoveToLabel.SCHEMA_NAME,
                "class: " + MoveToLabel.class.getName(),
                "directionRange: 30",
                "sensorRange: 30",
                "reward: 2",
                "numSpeedValues: 5",
                "numDirectionValues: 8",
                "minSpeed: 1",
                "maxSpeed: 30"
        ));
        RewardFunction f = MoveToLabel.create(root, Locator.root());
        // And a world state with robot directed to robotDeg, sensor directed to sensorDeg
        // and obstacle directed to obstacleDeg
        WorldModel state = createState(Complex.fromDeg(robotDeg),
                Complex.fromDeg(sensorDeg), Complex.fromDeg(obstacleDeg));
        // And command move to actionDeg at speed actionSpeed
        RobotCommands cmd = RobotCommands.moveAndScan(Complex.fromDeg(actionDeg), actionSpeed, Complex.DEG0);
        double result = f.applyAsDouble(null, cmd, state);

        assertThat(result, closeTo(expected, 1e-4));
    }
}