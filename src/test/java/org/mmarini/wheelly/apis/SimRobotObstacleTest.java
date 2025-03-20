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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.geom.Point2D;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;

class SimRobotObstacleTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1.5e-3;
    public static final double MM1 = 1e-3;
    public static final double ROBOT_RADIUS = 150e-3;
    public static final float GRID_SIZE = 200e-3f;
    public static final int MOVE_INTERATIONS = 10;
    private static final double HALF_SIZE = 100e-3;
    private static final double HALF_LENGTH = 140e-3;
    public static final int STALEMATE_INTERVAL = 60000;

    /**
     * Given a simulated robot with a map grid of 0.2 m and an obstacle at 0,0
     * located at the given location and directed to the given direction
     *
     * @param location       the location
     * @param robotDirection the robot direction
     */
    private static SimRobot createRobot(Point2D location, Complex robotDirection) {
        return createRobot(location, robotDirection, Complex.DEG0, 0, 0);
    }

    /**
     * Given a simulated robot with a map grid of 0.2 m and obstacles at x,y,...
     * located at the given location and directed to the given direction
     *
     * @param location       the location
     * @param robotDirection the robot direction
     * @param obsCoords      the obstacle coordinates x,y, ...
     */
    private static SimRobot createRobot(Point2D location, Complex robotDirection, Complex sensorDirection, double... obsCoords) {
        Random random = new Random(SEED);
        MapBuilder mapBuilder = MapBuilder.create(GRID_SIZE);
        for (int i = 0; i < obsCoords.length - 1; i += 2) {
            mapBuilder.add(false, obsCoords[i], obsCoords[i + 1]);
        }
        SimRobot simRobot = new SimRobot(ROBOT_SPEC, mapBuilder.build(),
                random, null, 0, 0,
                MAX_PPS,
                500, 500,
                0, 0, 0, STALEMATE_INTERVAL);
        simRobot.connect();
        simRobot.configure();
        simRobot.setRobotPos(location.getX(), location.getY());
        simRobot.setRobotDir(robotDirection);
        simRobot.setSensorDirection(sensorDirection);
        return simRobot;
    }

    /**
     * Given an obstacle map with 3 obstacle
     * <pre>
     *        ^
     *        |
     *        O
     *     ---OO-->
     * </pre>
     * and a robot located at a given distance and direction from origin directed to a given direction
     *
     * @param robotLocation   the robot location
     * @param robotDirection  the robot direction
     * @param sensorDirection the sensor direction
     */
    private static SimRobot createRobotWithObstacles(Point2D robotLocation, Complex robotDirection, Complex sensorDirection) {
        return createRobot(robotLocation, robotDirection, sensorDirection,
                0, 0,
                GRID_SIZE, 0,
                0, GRID_SIZE);
    }

    /*
     * Robot location for collision (250,250) = obstacle size 200 mm / 2 + robot radius 150 mm
     */
    @ParameterizedTest(name = "[{index}] Robot at ({0},{1}) head R{2} speed {3} pps")
    @CsvSource({
            "0,1000, 0,0,   60, false,false, 49", // no collision, max movement = 93mm
            "0,1000, 270,0, 60, false,false, 49", // no collision, max movement = 49mm

            // rear collisions from robot +(10,10)
            "260,260, 90,0, -60, false,true, -10",
            "260,260, 0,0,  -60, false,true, -10",
            "260,260, 45,0, -60, false,true, -14",

            // front collisions from robot +(10,10)
            "260,260, 270,90, 60, true,false, 10",
            "260,260, 180,-90, 60, true,false, 10",
    })
    void collisionTest(int robotX, int robotY, int robotDeg, int sensorDeg,
                       int speed,
                       boolean expFrontCollision, boolean expBackCollision, int expMovement) {
        /*
         Given the obstacle map with 3 obstacles
         and the robot located at the given location directed to the given direction
         and sensor directed to the given direction
         */
        Point2D robotLocation = new Point2D.Double(robotX * MM1, robotY * MM1);
        Complex robotDirection = Complex.fromDeg(robotDeg);
        Complex sensorDirection = Complex.fromDeg(sensorDeg);
        SimRobot robot = createRobotWithObstacles(robotLocation, robotDirection, sensorDirection);
        robot.halt();
        robot.tick(300);

        /*
         Then no collision should be detected
         */
        assertTrue(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        /*
         When moving the robot to a given direction,
         */
        robot.move(robotDirection, speed);
        long dt = 30;
        for (int i = 0; i < MOVE_INTERATIONS; i++) {
            robot.tick(dt);
        }

        /*
         Then expected collisions should be detected,
         */
        assertEquals(expFrontCollision, !robot.canMoveForward());
        assertEquals(expBackCollision, !robot.canMoveBackward());

        /*
         and the robot should be located at the expected location
         */
        Point2D moveLocation = robotDirection.at(robotLocation, expMovement * MM1);
        assertThat(robot.location(), pointCloseTo(moveLocation, DISTANCE_EPSILON));
    }

    /**
     * Given a space with an obstacle at 0,0
     * and robot directed to 90 DEG at distance of 239 mm from the obstacle
     * (-1 mm border distance obstacle - robot)
     * <pre>
     *       ^
     *       |
     *    -->O---->
     * </pre>
     * When haltCommand the robot and after 300 ms.
     * Then the robot should remain stopped,
     * and the distance sensor should signal 140 mm
     * and the proximity should signal 12 (front contacts)
     * and the forward block should be active
     * and the backward block should be inactive
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    void contactFront(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        Complex robotDir = locationDir.opposite();
        SimRobot robot = createRobot(location, robotDir);
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);

        // When halt robot
        robot.halt();

        // and ticking for 300 ms
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // and the proxy sensor should signal the obstacle at 140 mm
        assertThat(robot.echoDistance(), closeTo(ROBOT_RADIUS + MM1, DISTANCE_EPSILON));

        // And front contact
        assertFalse(robot.frontSensor());
        assertTrue(robot.rearSensor());

        // and cannot move forward
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        // When move ahead at max speed
        robot.move(robotDir, MAX_PPS);
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // And the proxy sensor should signal the obstacle at 140 mm
        assertThat(robot.echoDistance(), closeTo(ROBOT_RADIUS, DISTANCE_EPSILON));

        // and front contact
        assertFalse(robot.frontSensor());
        assertTrue(robot.rearSensor());

        // And robot cannot move ahead
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());
    }

    /*
     * <pre>
     *       ^
     *       |
     *    -->O---->
     * </pre>
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    void contactFrontMoveBack(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        SimRobot robot = createRobot(location, locationDir.opposite());
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);

        robot.halt();
        // and ticking for 300 ms
        robot.tick(300);

        // Then the robot should not change the location
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // and the proxy sensor should signal the obstacle at 151 mm
        assertThat(robot.echoDistance(), closeTo(ROBOT_RADIUS, 2 * DISTANCE_EPSILON));

        // and front contact
        assertFalse(robot.frontSensor());
        assertTrue(robot.rearSensor());

        // and robot cannot move forward
        assertTrue(robot.canMoveBackward());
        assertFalse(robot.canMoveForward());

        // When move back at half-speed for 300 ms
        robot.move(locationDir.opposite(), -MAX_PPS / 2);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }

        // Then the robot should be located back of 50 mm
        double ds = 38e-3;
        Point2D movePoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS + ds);
        assertThat(robot.location(), pointCloseTo(movePoint, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.frontSensor());
        assertTrue(robot.rearSensor());

        // and can move back and not forward (proxy block)
        assertTrue(robot.canMoveBackward());
        assertFalse(robot.canMoveForward());

        // And proxy sensor should measure 140+39 mm of distance
        assertThat(robot.echoDistance(), closeTo(ROBOT_RADIUS + ds, DISTANCE_EPSILON));
    }

    /*
     * <pre>
     *       ^
     *       |
     *    --<O---->
     * </pre>
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    void contactRear(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        SimRobot robot = createRobot(location, locationDir);

        // When halt and tick
        robot.halt();
        robot.tick(300);

        // Then ...
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // and rear contact
        assertTrue(robot.frontSensor());
        assertFalse(robot.rearSensor());

        // And cannot move back
        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

        robot.move(locationDir, -1);
        robot.tick(300);

        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        assertThat(robot.echoDistance(), closeTo(0, DISTANCE_EPSILON));

        // and rear contact
        assertTrue(robot.frontSensor());
        assertFalse(robot.rearSensor());

        // And cannot move back
        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());
    }

    /*
     * <pre>
     *       ^
     *       |
     *    --<O---->
     * </pre>
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    void contactRearMoveForward(int locationDeg) {
        // Given positioning robot at (0.76, 0)
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        SimRobot robot = createRobot(location, locationDir);

        // When halt
        robot.halt();
        // and ticking for 300 ms
        robot.tick(300);

        // Then the robot should not change the location
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // and the proxy sensor should signal no obstacle
        assertThat(robot.echoDistance(), closeTo(0, DISTANCE_EPSILON));

        // and rear contact
        assertTrue(robot.frontSensor());
        assertFalse(robot.rearSensor());

        // and robot cannot move backward
        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

        // When move front at max speed for 300 ms
        robot.move(locationDir, MAX_PPS / 2);
        for (int i = 0; i < 10; i++) {
            robot.tick(30);
        }

        // Then the robot should be located ahead of 44 mm
        double ds = 38e-3;
        Point2D movePoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS + ds);
        assertThat(robot.location(), pointCloseTo(movePoint, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.frontSensor());
        assertTrue(robot.rearSensor());

        // and can move back and forward
        assertTrue(robot.canMoveBackward());
        assertTrue(robot.canMoveForward());

        // and the proxy sensor should signal no obstacle
        assertThat(robot.echoDistance(), closeTo(0, DISTANCE_EPSILON));
    }

    @ParameterizedTest(name = "[{index}] R{0}, {1} PPS, contacts {2},{3}")
    @CsvSource({
            "0,-60, true,true",

            "45,0, true,true",
            "315,0, false,true",

            "225,0, true,true",
            "135,0, true,true",

            "180,0, true,true",

            "30,-30, true,true",
            "330,-30, true,true",

            "45,-30, true,true",
            "315,-30, true,true",

            "60,-30, true,true",
            "300,-30, true,true",

            "90,-30, true,true",
            "270,-30, true,true",
    })
    void moveTest(int moveDeg, int speed, boolean canMoveForward, boolean canMoveBackward) {
        // Given the robot simulator in a map with 3 obstacles at 1, R0 from the robot
        Point2D robotLocation = new Point2D.Double();
        Complex robotDirection = Complex.DEG0;
        Complex sensorDirection = Complex.DEG90;
        SimRobot robot = createRobot(robotLocation, robotDirection, sensorDirection,
                -0.2, 1,
                0, 1,
                0.2, 1
        );
        robot.halt();
        robot.tick(300);
        // And moving the robot ahead till the obstacle contact
        while (robot.canMoveForward()) {
            robot.move(Complex.DEG0, MAX_PPS);
            robot.tick(30);
        }
        assertFalse(robot.canMoveForward());

        // Then the robot should stop at 0.703 m from the origin
        assertThat(robot.location(), pointCloseTo(0, 0.750, DISTANCE_EPSILON));

        // When turning the robot to 45 DEG back
        Complex moveDirection = Complex.fromDeg(moveDeg);
        for (int i = 0; i < MOVE_INTERATIONS * 10; i++) {
            robot.move(moveDirection, speed);
            robot.tick(30);
        }

        // Then the robot should be directed to move direction
        Complex direction = robot.direction();
        assertThat(direction, angleCloseTo(moveDirection, 1));
        // And should have expected contact sensor signals
        assertEquals(canMoveForward, robot.canMoveForward());
        assertEquals(canMoveBackward, robot.canMoveBackward());
    }

    /*
     * <pre>
     *       ^
     *       |
     *    ->-O---->
     * </pre>
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    void obstacleFront(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        double dr = 40e-3;
        Point2D location = locationDir.at(new Point2D.Float(), HALF_LENGTH + HALF_SIZE + dr);
        SimRobot robot = createRobot(location, locationDir.opposite());

        // When ...
        robot.halt();
        robot.tick(300);

        // Then ...
        assertThat(robot.location(), pointCloseTo(location, DISTANCE_EPSILON));
        assertThat(robot.echoDistance(), closeTo(HALF_LENGTH + dr, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.frontSensor());
        assertTrue(robot.rearSensor());

        // and cannot move ahead (proxy signal)
        assertFalse(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        robot.move(locationDir.opposite(), 1);
        robot.tick(300);

        assertThat(robot.location(), pointCloseTo(location, DISTANCE_EPSILON));
        assertThat(robot.echoDistance(), closeTo(HALF_LENGTH + dr, DISTANCE_EPSILON));

        // and no contacts
        assertTrue(robot.frontSensor());
        assertTrue(robot.rearSensor());

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
     * When move backward to -90 DEG at max speed for 12 steps of 30 ms.
     * Then the robot should stop at the obstacle,
     * And the rear sensor contacts should be active
     * And backward movement sensor should be off
     */
    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    void obstacleRear(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        double dr = 40e-3;
        Point2D location = locationDir.at(new Point2D.Float(), ROBOT_RADIUS + GRID_SIZE / 2 + dr);
        SimRobot robot = createRobot(location, locationDir);

        // When ...
        robot.halt();
        robot.tick(300);

        // Then ...
        assertThat(robot.location(), pointCloseTo(location, DISTANCE_EPSILON));
        assertThat(robot.echoDistance(), closeTo(0, DISTANCE_EPSILON));

        assertTrue(robot.frontSensor());
        assertTrue(robot.rearSensor());

        assertTrue(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());

        // When moving back
        robot.move(locationDir, -MAX_PPS);
        long dt = 30;
        for (int i = 0; i < MOVE_INTERATIONS; i++) {
            robot.tick(dt);
        }

        // Then ...
        assertTrue(robot.frontSensor());
        assertFalse(robot.rearSensor());

        assertTrue(robot.canMoveForward());
        assertFalse(robot.canMoveBackward());

        // and the robot should be located near the obstacle (?)
//        double dx = speed * DISTANCE_PER_PULSE * dt / 1000;
        Point2D moveLocation = locationDir.at(location, -dr);
        assertThat(robot.location(), pointCloseTo(moveLocation, DISTANCE_EPSILON));
        assertThat(robot.echoDistance(), closeTo(0, DISTANCE_EPSILON));
    }
}