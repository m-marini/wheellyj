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

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.*;

class SimRobotObstacleTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1e-3;
    private static final float XO = 1F;
    private static final float HALF_SIZE = 100e-3F;
    private static final float HALF_LENGTH = 140e-3F;

    @Test
    void contactFront() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE + 1e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(140e-3, DISTANCE_EPSILON));
        assertEquals(12, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertFalse(robot.getCanMoveForward());

        robot.move(90, 1);
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(140e-3, DISTANCE_EPSILON));
        assertEquals(12, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertFalse(robot.getCanMoveForward());
    }

    @Test
    void contactFrontMoveBack() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(140e-3, DISTANCE_EPSILON));
        assertEquals(12, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertFalse(robot.getCanMoveForward());

        robot.move(90, -1);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }

        assertThat(robot.getRobotPos().getX(), closeTo(x0 - 33.2e-3, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(140e-3 + 33.2e-3, DISTANCE_EPSILON));
        assertEquals(0, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertFalse(robot.getCanMoveForward());
    }

    @Test
    void contactRear() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE + 1e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, robot.getContacts());
        assertFalse(robot.getCanMoveBackward());
        assertTrue(robot.getCanMoveForward());

        robot.move(-90, -1);
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, robot.getContacts());
        assertFalse(robot.getCanMoveBackward());
        assertTrue(robot.getCanMoveForward());
    }

    @Test
    void contactRearMoveForward() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, robot.getContacts());
        assertFalse(robot.getCanMoveBackward());
        assertTrue(robot.getCanMoveForward());

        robot.move(-90, 1);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }

        assertThat(robot.getRobotPos().getX(), closeTo(x0 - 33.2e-3F, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(0, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertTrue(robot.getCanMoveForward());
    }

    private SimRobot createRobot() {
        Random random = new Random(SEED);
        return new SimRobot(new MapBuilder(new GridTopology(0.2f)).add(XO, 0).build(),
                random, 0, 0, null, 0, 0);
    }

    @Test
    void obstacleFront() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE - 40e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(XO - x0 - HALF_SIZE, DISTANCE_EPSILON));
        assertEquals(0, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertFalse(robot.getCanMoveForward());

        robot.move(90, 1);
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(XO - x0 - HALF_SIZE, DISTANCE_EPSILON));
        assertEquals(0, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertFalse(robot.getCanMoveForward());
    }

    @Test
    void obstacleRear() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE - 50e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getRobotPos().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(0, robot.getContacts());
        assertTrue(robot.getCanMoveBackward());
        assertTrue(robot.getCanMoveForward());

        robot.move(-90, -1);
        for (int i = 0; i < 100; i++) {
            robot.tick(3);
        }
        assertThat(robot.getRobotPos().getX(), closeTo(XO - HALF_LENGTH - HALF_SIZE, 20e-3));
        assertThat(robot.getRobotPos().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat((double) robot.getSensorDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, robot.getContacts());
        assertFalse(robot.getCanMoveBackward());
        assertTrue(robot.getCanMoveForward());
    }

}