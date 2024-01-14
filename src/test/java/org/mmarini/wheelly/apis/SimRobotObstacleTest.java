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

import static java.lang.Math.toRadians;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.wheelly.apis.RobotStatus.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;

class SimRobotObstacleTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1e-3;
    public static final double MM1 = 1e-3;
    public static final double ROBOT_RADIUS = 150e-3;
    public static final float GRID_SIZE = 200e-3f;
    private static final double XO = 1;
    private static final double HALF_SIZE = 100e-3;
    private static final double HALF_LENGTH = 140e-3;

    /**
     * Given a space with an obstacle at X0,0
     * and robot directed to 90 DEG at distance of 239 mm from obstacle (-1 mm border distance obstacle - robot)
     * When haltCommand the robot and after 300ms
     * Then the robot should remain stopped
     * and the distance sensor should signal 140mm
     * and the proximity should signal 12 (front contacts)
     * and the forward block should be active
     * and the backward block should be inactive
     */
    @Test
    void contactFront() {
        SimRobot robot = createRobot();
        // When positioning robot at (0.749, 0)
        double xContact = XO - GRID_SIZE / 2 - ROBOT_RADIUS;
        double x0 = xContact - MM1;
        robot.setRobotPos(x0, 0);
        // and directed to 90 DEG
        robot.setRobotDir(90);
        robot.halt();

        // and ticking for 300 ms
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));

        // and the proxy sensor should signal obstacle at 140 mm
        assertThat(robot.getEchoDistance(), closeTo(ROBOT_RADIUS + MM1, DISTANCE_EPSILON));

        // And front contact
        assertFalse(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // and cannot move forward
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        // When move ahead to 90 DEG at max speed
        robot.move(90, MAX_PPS);
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.getLocation().getX(), closeTo(x0 - MM1, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));

        // And the proxy sensor should signal obstacle at 140 mm
        assertThat(robot.getEchoDistance(), closeTo(ROBOT_RADIUS + 2 * MM1, DISTANCE_EPSILON));

        // and front contact
        assertFalse(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // And robot cannot move ahead
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());
    }

    @Test
    void contactFrontMoveBack() {
        SimRobot robot = createRobot();
        double x0 = XO - ROBOT_RADIUS - GRID_SIZE / 2;

        // When positioning robot at (0.850, 0)
        robot.setRobotPos(x0, 0);
        // and directed to 90 DEG
        robot.setRobotDir(90);
        robot.halt();
        // and ticking for 300 ms
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));

        // and the proxy sensor should signal obstacle at 140 mm
        assertThat(robot.getEchoDistance(), closeTo(ROBOT_RADIUS, DISTANCE_EPSILON));

        // and front contact
        assertFalse(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // and robot cannot move forward
        assertTrue(robot.canMoveBackward());
        assertFalse(robot.canMoveForward());

        // When move back at half speed for 300 ms
        robot.move(90, -MAX_PPS / 2);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }

        // Then the robot should be located back of 50 mm
        double ds = 38e-3;
        assertThat(robot.getLocation().getX(), closeTo(x0 - ds, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // and can move back and not forward (proxy block)
        assertTrue(robot.canMoveBackward());
        assertFalse(robot.canMoveForward());

        // And proxy sensor should measure 140+39 mm of distance
        assertThat(robot.getEchoDistance(), closeTo(ROBOT_RADIUS + ds, DISTANCE_EPSILON));
    }

    @Test
    void contactRear() {
        SimRobot robot = createRobot();
        double xContact = XO - ROBOT_RADIUS - GRID_SIZE / 2;
        double x0 = xContact - MM1;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));

        // and rear contact
        assertTrue(robot.isFrontSensor());
        assertFalse(robot.isRearSensor());

        // And cannot move back
        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

        robot.move(-90, -1);
        robot.tick(300);

        assertThat(robot.getLocation().getX(), closeTo(x0 - MM1, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));

        // and rear contact
        assertTrue(robot.isFrontSensor());
        assertFalse(robot.isRearSensor());

        // And cannot move back
        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

    }

    @Test
    void contactRearMoveForward() {
        SimRobot robot = createRobot();
        // When positioning robot at (0.76, 0)
        double x0 = XO - ROBOT_RADIUS - GRID_SIZE / 2;
        robot.setRobotPos(x0, 0);
        // and directed to -90 DEG
        robot.setRobotDir(-90);
        robot.halt();
        // and ticking for 300 ms
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));

        // and the proxy sensor should signal no obstacle
        assertThat(robot.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));

        // and rear contact
        assertTrue(robot.isFrontSensor());
        assertFalse(robot.isRearSensor());

        // and robot cannot move backward
        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

        // When move front at max speed for 300 ms
        robot.move(-90, MAX_PPS / 2);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }

        // Then the robot should be located ahead of 44 mm
        double ds = 38e-3;
        assertThat(robot.getLocation().getX(), closeTo(x0 - ds, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // and can move back and forward
        assertTrue(robot.canMoveBackward());
        assertTrue(robot.canMoveForward());

        // and the proxy sensor should signal no obstacle
        assertThat(robot.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
    }

    /**
     * Given a simulated robot with a map grid of 0.2 m and an obstacle at 1,0
     */
    private SimRobot createRobot() {
        Random random = new Random(SEED);
        SimRobot simRobot = new SimRobot(new MapBuilder(new GridTopology(GRID_SIZE)).add(XO, 0).build(),
                random, 0, 0, toRadians(15), MAX_PPS, 500, 500);
        simRobot.connect();
        simRobot.configure();
        return simRobot;
    }

    @Test
    void obstacleFront() {
        SimRobot robot = createRobot();
        double x0 = XO - HALF_LENGTH - HALF_SIZE - 40e-3;
        robot.setRobotPos(x0, 0);
        robot.setRobotDir(90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getEchoDistance(), closeTo(XO - x0 - HALF_SIZE, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // and cannot move ahead (proxy signal)
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        robot.move(90, 1);
        robot.tick(300);

        assertThat(robot.getLocation().getX(), closeTo(x0, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getEchoDistance(), closeTo(XO - x0 - HALF_SIZE, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        // and cannot move ahead
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());
    }

    /**
     * Given a robot directed to -90 DEG with an obstacle at 50 mm from the back
     * <pre>
     *     < R | - 50 mm - |  O  |
     *       |-------------+--+--|
     *       xr            xc XO
     * </pre>
     * When move backward to -90 DEG at max speed for 12 steps of 30 ms
     * Than the robot should stop at obstacle
     * And the rear sensor contacts should be active
     * And backward movement sensor should be off
     */
    @Test
    void obstacleRear() {
        SimRobot robot = createRobot();
        double ds = 50e-3;
        double xr = XO - ROBOT_RADIUS - GRID_SIZE / 2 - ds;
        robot.setRobotPos(xr, 0);
        robot.setRobotDir(-90);
        robot.halt();
        robot.tick(300);

        assertThat(robot.getLocation().getX(), closeTo(xr, DISTANCE_EPSILON));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));

        assertTrue(robot.isFrontSensor());
        assertTrue(robot.isRearSensor());

        assertTrue(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        int speed = MAX_PPS;
        robot.move(-90, -speed);
        long dt = 30;
        for (int i = 0; i < 12; i++) {
            robot.tick(dt);
        }

        assertTrue(robot.isFrontSensor());
        assertFalse(robot.isRearSensor());

        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

        // and the robot should be located near the obstacle (?)
        double dx = speed * DISTANCE_PER_PULSE * dt / 1000 + MM1;
        double xc = XO - GRID_SIZE / 2;
        assertThat(robot.getLocation().getX(), closeTo(xc - ROBOT_RADIUS, dx));
        assertThat(robot.getLocation().getY(), closeTo(0, DISTANCE_EPSILON));
        assertThat(robot.getEchoDistance(), closeTo(0, DISTANCE_EPSILON));
    }

}