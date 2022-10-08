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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.Random;

import static java.lang.Math.PI;
import static java.lang.Math.round;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SimRobotTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1e-3;
    public static final float MAX_ANGULAR_VELOCITY = (float) (0.280 / 0.136 * 180 / PI);

    @Test
    void create() {
        SimRobot robot = createRobot();
        assertEquals(new Point2D.Float(), robot.getRobotPos());
        assertEquals(0, robot.getRobotDir());
        assertEquals(0, robot.getSensorDir());
        assertEquals(0f, robot.getSensorDistance());
    }

    private SimRobot createRobot() {
        Random random = new Random(SEED);
        return new SimRobot(new MapBuilder(new GridTopology(0.2f)).build(),
                random, 0, 0);
    }

    @Test
    void moveFrom0To0By1() {
        SimRobot robot = createRobot();

        robot.move(0, 1);
        robot.tick(100);

        assertThat(robot.getRobotPos().getX(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(10e-3, DISTANCE_EPSILON));
        assertEquals(0, robot.getRobotDir());
        assertEquals(0, robot.getSensorDir());
        assertEquals(0f, robot.getSensorDistance());
    }

    @Test
    void moveFrom0To0By_1() {
        SimRobot robot = createRobot();

        robot.move(0, -1);
        robot.tick(100);

        assertThat(robot.getRobotPos().getX(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(-10e-3, DISTANCE_EPSILON));
        assertEquals(0, robot.getRobotDir());
        assertEquals(0, robot.getSensorDir());
        assertEquals(0f, robot.getSensorDistance());
    }

    @Test
    void rotateFrom0To5() {
        SimRobot robot = createRobot();

        robot.move(5, 0);
        robot.tick(100);

        assertThat(robot.getRobotPos().getX(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertEquals((int) round(MAX_ANGULAR_VELOCITY * 0.1 / 2), robot.getRobotDir());
        assertEquals(0, robot.getSensorDir());
        assertEquals(0f, robot.getSensorDistance());
    }

    @Test
    void rotateFrom0To90() {
        SimRobot robot = createRobot();

        robot.move(90, 0);
        robot.tick(100);

        assertThat(robot.getRobotPos().getX(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertEquals((int) round(MAX_ANGULAR_VELOCITY * 0.1), robot.getRobotDir());
        assertEquals(0, robot.getSensorDir());
        assertEquals(0f, robot.getSensorDistance());
    }

    @Test
    void tickStopping() {
        SimRobot robot = createRobot();

        robot.tick(100);

        assertEquals(new Point2D.Float(), robot.getRobotPos());
        assertEquals(0, robot.getRobotDir());
        assertEquals(0, robot.getSensorDir());
        assertEquals(0f, robot.getSensorDistance());
    }

}