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
    public static final float RECEPTIVE_DISTANCE = 0.1F;
    private static final float XO = 1F;
    private static final float HALF_SIZE = 100e-3F;
    private static final float HALF_LENGTH = 140e-3F;

    /**
     * Given a space with an obstacle at X0,0
     * and robot directed to 90 DEG at distance of 239 mm from obstacle (-1 mm border distance obstacle - robot)
     * When halt the robot and after 300ms
     * Then the robot should remain stoped
     * and the distance sensor should signal 140mm
     * and the proximity sould signal 12 (front contacts)
     * and the forward block should be active
     * and the backward block should be inactive
     */
    @Test
    void contactFront() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE + 1e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();

        robot.tick(300);
        RobotStatus status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(140e-3, DISTANCE_EPSILON));
        assertEquals(12, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertFalse(status.canMoveForward());

        robot.move(90, 1);
        robot.tick(300);

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(140e-3, DISTANCE_EPSILON));
        assertEquals(12, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertFalse(status.canMoveForward());
    }

    @Test
    void contactFrontMoveBack() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();
        robot.tick(300);
        RobotStatus status = robot.getStatus();
        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(140e-3, DISTANCE_EPSILON));
        assertEquals(12, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertFalse(status.canMoveForward());

        robot.move(90, -1);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }
        status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0 - 34.2e-3, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(140e-3 + 34.2e-3, DISTANCE_EPSILON));
        assertEquals(0, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertFalse(status.canMoveForward());
    }

    @Test
    void contactRear() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE + 1e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);
        RobotStatus status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, status.getContacts());
        assertFalse(status.canMoveBackward());
        assertTrue(status.canMoveForward());

        robot.move(-90, -1);
        robot.tick(300);
        status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, status.getContacts());
        assertFalse(status.canMoveBackward());
        assertTrue(status.canMoveForward());
    }

    @Test
    void contactRearMoveForward() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);
        RobotStatus status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, status.getContacts());
        assertFalse(status.canMoveBackward());
        assertTrue(status.canMoveForward());

        robot.move(-90, 1);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }
        status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0 - 33.2e-3F, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(0, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertTrue(status.canMoveForward());
    }

    private SimRobot createRobot() {
        Random random = new Random(SEED);
        return new SimRobot(new MapBuilder(new GridTopology(0.2f)).add(XO, 0).build(),
                random, 0, 0, null, 0, 0, RECEPTIVE_DISTANCE);
    }

    @Test
    void obstacleFront() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE - 40e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();
        robot.tick(300);
        RobotStatus status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(XO - x0 - HALF_SIZE, DISTANCE_EPSILON));
        assertEquals(0, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertFalse(status.canMoveForward());

        robot.move(90, 1);
        robot.tick(300);

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(XO - x0 - HALF_SIZE, DISTANCE_EPSILON));
        assertEquals(0, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertFalse(status.canMoveForward());
    }

    @Test
    void obstacleRear() {
        SimRobot robot = createRobot();
        float x0 = XO - HALF_LENGTH - HALF_SIZE - 50e-3F;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);
        RobotStatus status = robot.getStatus();

        assertThat(status.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(0, status.getContacts());
        assertTrue(status.canMoveBackward());
        assertTrue(status.canMoveForward());

        robot.move(-90, -1);
        for (int i = 0; i < 100; i++) {
            robot.tick(3);
        }
        status = robot.getStatus();
        assertThat(status.getLocation().getX(), closeTo(XO - HALF_LENGTH - HALF_SIZE, 20e-3));
        assertThat(status.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(status.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
        assertEquals(3, status.getContacts());
        assertFalse(status.canMoveBackward());
        assertTrue(status.canMoveForward());
    }

}