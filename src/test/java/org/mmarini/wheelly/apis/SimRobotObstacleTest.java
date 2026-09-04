/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.SimRobot.SAFE_DISTANCE;
import static org.mmarini.wheelly.apis.Utils.MM;

class SimRobotObstacleTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1.5e-3;
    public static final double MM1 = 1e-3;
    public static final float GRID_SIZE = 200e-3f;
    public static final int STALEMATE_INTERVAL = 60000;
    public static final long MESSAGE_INTERVAL = 500;
    public static final long INTERVAL = 10;
    public static final long CHANGE_MAP_PERIOD = 100000L;
    public static final double MM10 = 10e-3;
    public static final int NUM_RANDOM_TEST_CASES = 100;

    public static Stream<Arguments> dataAllDirection() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359)
                .build(NUM_RANDOM_TEST_CASES);
    }

    private SimRobot robot;
    private List<WheellyContactsMessage> contacts;
    private List<WheellyLidarMessage> lidars;

    /**
     * Given a simulated robot with a map grid of 0.2 m and obstacles at x,y,...
     * located at the given location and directed to the given direction
     *
     * @param location       the location
     * @param robotDirection the robot direction
     * @param obsCoords      the obstacle coordinates x,y, ...
     */
    private SimRobot createRobot(Point2D location, Complex robotDirection, Complex sensorDirection, double... obsCoords) {
        MapBuilder mapBuilder = MapBuilder.empty(41, 0.2);
        for (int i = 0; i < obsCoords.length - 1; i += 2) {
            mapBuilder = mapBuilder.addObstacle(obsCoords[i], obsCoords[i + 1], null);
        }
        return createRobot(location, robotDirection, sensorDirection, mapBuilder);
    }

    /**
     * Return the robot
     *
     * @param location        the location
     * @param robotDirection  the direction
     * @param sensorDirection the sensor direction
     * @param mapBuilder      the obstacle map
     */
    private SimRobot createRobot(Point2D location, Complex robotDirection, Complex sensorDirection, MapBuilder mapBuilder) {
        Random random = new Random(SEED);
        SimRobot simRobot = new SimRobot(DEFAULT_ROBOT_SPEC, random, random,
                0, INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, STALEMATE_INTERVAL,
                0, 0,
                List.of(mapBuilder), 0, 0, CHANGE_MAP_PERIOD, CHANGE_MAP_PERIOD,
                SimRobot.DEFAULT_WORLD_SIZE);
        simRobot.robotPos(location.getX(), location.getY());
        simRobot.robotDir(robotDirection);
        simRobot.sensorDirection(sensorDirection);
        simRobot.obstacleMap(mapBuilder.build());

        simRobot.addOnContacts(contacts::add);
        simRobot.addOnLidar(lidars::add);

        return simRobot;
    }

    /**
     * Given a simulated robot located at the given location and directed to the given direction
     * and head directed to 90 DEG (looking right) with an obstacle at (0,0)
     *
     * @param location       the location
     * @param robotDirection the robot direction
     */
    private SimRobot createRobot(Point2D location, Complex robotDirection) {
        return createRobot(location, robotDirection, Complex.DEG90, 0, 0);
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
    private SimRobot createRobotWithObstacles(Point2D robotLocation, Complex robotDirection, Complex sensorDirection) {
        return createRobot(robotLocation, robotDirection, sensorDirection,
                0, 0,
                DEFAULT_OBSTACLE_RADIUS, 0,
                0, DEFAULT_OBSTACLE_RADIUS);
    }

    @BeforeEach
    void setUp() {
        this.lidars = new ArrayList<>();
        this.contacts = new ArrayList<>();
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testFrontCollision(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 10 mm from the obstacle
         * (obstacle radius + robot radius + 10 mm)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS + MM10);
        Complex robotDir = locationDir.opposite();
        robot = createRobot(location, robotDir);

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.forward(new Point2D.Double());

        /*
         When moving the robot to a given direction until contact
         */
        long maxTime = 1500;
        do {
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || !robot.frontSensor()
                || !robot.rearSensor()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be detected
        assertFalse(robot.canMoveForward());
        assertFalse(robot.frontSensor());
        assertThat(contacts, hasSize(2));
        assertTrue(contacts.getFirst().frontSensors());
        assertFalse(contacts.getLast().frontSensors());

        // And robot movement should be close the collision distance
        assertThat(robot.location().distance(location), closeTo(MM10, MM));
    }

    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testFrontContact(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 249 mm from the obstacle
         * (-1 mm obstacle radius - robot radius)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS - MM1);
        Complex robotDir = locationDir.opposite();
        robot = createRobot(location, robotDir);
        /*
         * And the robot location at contact point
         */
        Point2D contactPoint = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS);

        /*
         * When connect robot
         */
        robot.syncConnect();

        // And when move ahead at max power
        robot.forward(new Point2D.Double());
        robot.simulate();
        robot.close();
        robot.simulate();

        /*
         * Then the robot should remain stopped at contact point
         */
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        assertFalse(robot.frontSensor());
        assertFalse(robot.canMoveForward());

        /*
         * And contact message should be emitted
         */
        assertThat(contacts, hasSize(2));
        assertFalse(contacts.getLast().frontSensors());
        assertFalse(contacts.getLast().canMoveForward());

        /*
         * and the sensor should signal no obstacle (head directed to right)
         */
        assertThat(robot.frontDistance(), closeTo(0, MM));

        assertThat(lidars, hasSize(2));
        WheellyLidarMessage lidarMessage = lidars.getLast();
        assertNotNull(lidarMessage);
        assertEquals(0, lidarMessage.frontDistance());
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testFrontStop(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 10 mm from safety obstacle distance
         * (obstacle radius + robot radius + 10 mm)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        // Obstacle center---Obstacle bound-----SafePoint---------Lidar--------------Head--------RobotCenter
        //      <---Obstacle radius--><----10mm----><-safe distance-><-Lidar distance-><-Head distance->
        Point2D location = locationDir.at(new Point2D.Float(),
                DEFAULT_OBSTACLE_RADIUS + SAFE_DISTANCE + DEFAULT_HEAD_Y + DEFAULT_FRONT_LIDAR_DISTANCE + MM10);
        Complex robotDir = locationDir.opposite();
        robot = createRobot(location, robotDir, Complex.DEG0, 0, 0);

        /*
         * When connect robot
         */
        robot.syncConnect();

        /*
         When moving the robot to a given direction until contact
         */
        robot.forward(new Point2D.Double());
        long maxTime = 1500;
        do {
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || !robot.canMoveForward()
                || !robot.canMoveBackward()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be detected
        assertTrue(robot.frontSensor());
        assertFalse(robot.canMoveForward());
        assertThat(contacts, hasSize(2));
        assertTrue(contacts.getLast().frontSensors());
        assertFalse(contacts.getLast().canMoveForward());

        // And robot movement should be close the collision distance
        assertThat(robot.location().distance(location), closeTo(MM10, 2 * MM));
    }

    /*
     * Robot location for collision (250,250) = obstacle size 200 mm / 2 + robot radius 150 mm
     */
    @ParameterizedTest(name = "[{index}] Robot at ({0},{1}) head R{2} move by {3} mm")
    @CsvSource({
            // x,y, dir, sensorDir, power, expMovement
            "0,1000, 0, 422", // no collision, max movement = 416mm
            "0,1000, 270, 422",
    })
    void testNoCollision(int robotX, int robotY, int robotDeg, int expMovement) {
        /*
         Given the obstacle map with 3 obstacles
         and the robot located at the given location directed to the given direction
         and sensor directed to the given direction
         */
        Point2D robotLocation = new Point2D.Double(robotX * MM1, robotY * MM1);
        Complex robotDirection = Complex.fromDeg(robotDeg);
        Complex sensorDirection = Complex.DEG0;
        robot = createRobotWithObstacles(robotLocation, robotDirection, sensorDirection);
        Point2D moveLocation = robotDirection.at(robotLocation, expMovement * MM1);

        // When connect and wait for simulated 500 ms
        robot.syncConnect();

        /*
         When moving the robot to a given direction for maxTime
         */
        long maxTime = 3000;
        do {
            robot.forward(moveLocation);
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime));
        robot.close();
        robot.simulate();

        // Then robot cannot be blocked
        assertTrue(robot.canMoveForward());
        assertTrue(robot.canMoveBackward());
        // and should move to expected location
        double movement = robotLocation.distance(robot.location());
        assertThat(movement, closeTo(abs(expMovement * MM1), DEFAULT_TARGET_RANGE));
        assertThat(robot.location(), pointCloseTo(moveLocation, DEFAULT_TARGET_RANGE));

        // and should generate just initial contact message
        assertThat(contacts, hasSize(1));
        assertTrue(contacts.getFirst().canMoveForward());
        assertTrue(contacts.getFirst().canMoveBackward());
        assertTrue(contacts.getFirst().frontSensors());
        assertTrue(contacts.getFirst().rearSensors());
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRearCollision(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given direction DEG at distance of 10 mm from the obstacle
         * (obstacle radius + robot radius + 10 mm)
         * (obstacle rear of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS + MM10);
        robot = createRobot(location, locationDir);

        /*
         * When connect robot
         */
        robot.syncConnect();

        /*
         When moving the robot backward the given direction until contact
         */
        robot.backward(new Point2D.Double());
        long maxTime = 1500;
        do {
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || !robot.frontSensor()
                || !robot.rearSensor()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be detected
        assertFalse(robot.rearSensor());
        assertFalse(robot.canMoveBackward());
        assertThat(contacts, hasSize(2));
        assertTrue(contacts.getFirst().rearSensors());
        assertFalse(contacts.getLast().rearSensors());

        // And robot movement should be close the collision distance
        assertThat(robot.location().distance(location), closeTo(MM10, MM));
    }

    @ParameterizedTest
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRearContact(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given direction DEG at distance of 249 mm from the obstacle
         * (-1 mm obstacle radius - robot radius)
         * (obstacle rear of robot)
         * and robot directed to 90 DEG at distance of 249 mm from the obstacle
         * (-1 mm obstacle radius- robot radius)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS - MM1);
        robot = createRobot(location, locationDir);
        /*
         * And the robot location at contact point
         */
        Point2D contactPoint = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS);

        /*
         * When connect robot
         */
        robot.syncConnect();

        // And when move backward at max power
        robot.backward(new Point2D.Double());
        robot.simulate();
        robot.close();
        robot.simulate();

        /*
         * Then the robot should remain stopped at contact point
         */
        assertThat(robot.location(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        assertFalse(robot.rearSensor());
        assertFalse(robot.canMoveBackward());

        /*
         * And contact message should be emitted
         */
        assertThat(contacts, hasSize(2));
        assertFalse(contacts.getLast().rearSensors());
        assertFalse(contacts.getLast().canMoveBackward());

        /*
         * and the sensor should signal no obstacle (head directed to right)
         */
        assertThat(robot.rearDistance(), closeTo(0, MM));

        assertThat(lidars, hasSize(2));
        WheellyLidarMessage lidarMessage = lidars.getLast();
        assertNotNull(lidarMessage);
        assertEquals(0, lidarMessage.rearDistance());
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRearStop(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 10 mm from safety obstacle distance
         * (obstacle radius + robot radius + 10 mm)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        // Obstacle center---Obstacle bound---SafePoint------------RobotCenter---Lidar--------------Head
        //                                                              <--------Head distance------->
        //      <---Obstacle radius--><----10mm----><--------safe distance---------><-Lidar distance->
        //
        Point2D location = locationDir.at(new Point2D.Float(),
                DEFAULT_OBSTACLE_RADIUS + MM10 + SAFE_DISTANCE + DEFAULT_REAR_LIDAR_DISTANCE - DEFAULT_HEAD_Y);
        robot = createRobot(location, locationDir, Complex.DEG0, 0, 0);

        /*
         * When connect robot
         */
        robot.syncConnect();

        /*
         When moving the robot to a given direction until contact
         */
        robot.backward(new Point2D.Double());
        long maxTime = 1500;
        do {
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || !robot.canMoveForward()
                || !robot.canMoveBackward()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be detected
        assertTrue(robot.rearSensor());
        assertFalse(robot.canMoveBackward());
        assertThat(contacts, hasSize(2));
        assertTrue(contacts.getLast().rearSensors());
        assertFalse(contacts.getLast().canMoveBackward());

        // And robot movement should be close the collision distance
        assertThat(robot.location().distance(location), closeTo(MM10, 2 * MM));
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRemoveFrontCollision(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 10 mm from safety obstacle distance
         * (obstacle radius + robot radius + 10 mm)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        // Obstacle center---Obstacle bound-----SafePoint---------Lidar--------------Head--------RobotCenter
        //      <---Obstacle radius--><---- -1 mm----><-safe distance-><-Lidar distance-><-Head distance->
        Point2D location = locationDir.at(new Point2D.Float(),
                DEFAULT_OBSTACLE_RADIUS + SAFE_DISTANCE + DEFAULT_HEAD_Y + DEFAULT_FRONT_LIDAR_DISTANCE - MM1);
        Complex robotDir = locationDir.opposite();
        robot = createRobot(location, robotDir, Complex.DEG0, 0, 0);
        Point2D target = locationDir.at(location, SAFE_DISTANCE + DEFAULT_TARGET_RANGE);

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.canMoveForward());

        // When moving backward
        long maxTime = 1500;
        do {
            robot.backward(target);
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || robot.canMoveForward() && robot.canMoveBackward()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.frontSensor());
        assertThat(contacts, not(empty()));
        assertTrue(contacts.getLast().canMoveForward());
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRemoveFrontContact(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 249 mm from the obstacle
         * (-1 mm obstacle radius - robot radius)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS - MM1);
        Complex robotDir = locationDir.opposite();
        robot = createRobot(location, robotDir);
        Point2D target = locationDir.at(location, SAFE_DISTANCE + DEFAULT_TARGET_RANGE);

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.frontSensor());
        assertTrue(robot.canMoveBackward());

        // When moving backward
        robot.backward(target);
        long maxTime = 1500;
        do {
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || robot.frontSensor() && robot.rearSensor()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.frontSensor());
        assertThat(contacts, not(empty()));
        assertTrue(contacts.getLast().frontSensors());
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRemoveRearCollision(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given opposite direction DEG at distance of 10 mm from safety obstacle distance
         * (obstacle radius + robot radius + 10 mm)
         * (obstacle in front of robot)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        // Obstacle center---Obstacle bound---SafePoint------------RobotCenter---Lidar--------------Head
        //                                                              <--------Head distance------->
        //      <---Obstacle radius--><---- -1 mm----><--------safe distance---------><-Lidar distance->
        //
        Point2D location = locationDir.at(new Point2D.Float(),
                DEFAULT_OBSTACLE_RADIUS - MM + SAFE_DISTANCE + DEFAULT_REAR_LIDAR_DISTANCE - DEFAULT_HEAD_Y);
        robot = createRobot(location, locationDir, Complex.DEG0, 0, 0);
        Point2D target = locationDir.at(location, SAFE_DISTANCE + DEFAULT_TARGET_RANGE);

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.canMoveBackward());

        // When moving forward
        long maxTime = 1500;
        do {
            robot.forward(target);
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || robot.canMoveForward() && robot.canMoveBackward()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.canMoveBackward());
        assertThat(contacts, not(empty()));
        assertTrue(contacts.getLast().canMoveBackward());
    }

    @ParameterizedTest(name = "[{index}] Robot at R{0}")
    @ValueSource(ints = {
            0, 90, 180, 270
    })
    @MethodSource("dataAllDirection")
    void testRemoveRearContact(int locationDeg) {
        /*
         * Given a space with an obstacle at 0,0
         * and robot directed to given direction DEG at distance of 249 mm from the obstacle
         * (-1 mm obstacle radius - robot radius)
         * (obstacle rear of robot)
         * and robot directed to 90 DEG at distance of 249 mm from the obstacle
         * (-1 mm obstacle radius- robot radius)
         *       ^
         *       |
         *    -->O---->
         */
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS - MM1);
        robot = createRobot(location, locationDir);
        Point2D target = locationDir.at(location, SAFE_DISTANCE + DEFAULT_TARGET_RANGE);

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.rearSensor());

        // When moving forward
        long maxTime = 1500;
        do {
            robot.forward(target);
            robot.simulate();
        } while (!(robot.robotTime() >= maxTime
                || robot.frontSensor() && robot.rearSensor()));
        robot.close();
        robot.simulate();

        // Then
        assertTrue(robot.robotTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.rearSensor());
        assertThat(contacts, not(empty()));
        assertTrue(contacts.getLast().rearSensors());
    }
}