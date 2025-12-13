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

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.TestFunctions;

import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Random;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.*;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.SimRobot.SAFE_DISTANCE;
import static org.mmarini.wheelly.apis.Utils.MM;

class SimRobotObstacleTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1.5e-3;
    public static final double MM1 = 1e-3;
    public static final double EPSILON_COLLISION = 5 * MM1;
    public static final float GRID_SIZE = 200e-3f;
    public static final int STALEMATE_INTERVAL = 60000;
    public static final long MESSAGE_INTERVAL = 500;
    public static final long INTERVAL = 10;
    public static final long CHANGE_OBSTACLES_PERIOD = 100000L;
    public static final double MM10 = 10e-3;

    public static Stream<Arguments> dataAllDirection() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359)
                .build(100);
    }

    private SimRobot robot;
    private TestSubscriber<WheellyContactsMessage> contactsSub;
    private TestSubscriber<WheellyLidarMessage> lidarSub;
    private TestSubscriber<WheellyMotionMessage> motionSub;

    /**
     * Given a simulated robot with a map grid of 0.2 m and obstacles at x,y,...
     * located at the given location and directed to the given direction
     *
     * @param location       the location
     * @param robotDirection the robot direction
     * @param obsCoords      the obstacle coordinates x,y, ...
     */
    private SimRobot createRobot(Point2D location, Complex robotDirection, Complex sensorDirection, double... obsCoords) {
        MapBuilder mapBuilder = MapBuilder.create();
        for (int i = 0; i < obsCoords.length - 1; i += 2) {
            mapBuilder.put(DEFAULT_OBSTACLE_RADIUS, null, obsCoords[i], obsCoords[i + 1]);
        }
        return createRobot(location, robotDirection, sensorDirection, mapBuilder.build());
    }

    /**
     * Return the robot
     *
     * @param location        the location
     * @param robotDirection  the direction
     * @param sensorDirection the sensor direction
     * @param map             the obstacle map
     */
    private SimRobot createRobot(Point2D location, Complex robotDirection, Complex sensorDirection, Collection<Obstacle> map) {
        Random random = new Random(SEED);
        SimRobot simRobot = new SimRobot(DEFAULT_ROBOT_SPEC, random, random,
                0, INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, STALEMATE_INTERVAL, CHANGE_OBSTACLES_PERIOD,
                0, 0, RobotSpec.MAX_PPS, 0, 0);
        simRobot.robotPos(location.getX(), location.getY());
        simRobot.robotDir(robotDirection);
        simRobot.sensorDirection(sensorDirection);
        simRobot.obstacleMap(map);
        this.lidarSub = new TestSubscriber<>();
        this.contactsSub = new TestSubscriber<>();
        this.motionSub = new TestSubscriber<>();
        simRobot.readMotion().subscribe(motionSub);
        simRobot.readContacts().subscribe(contactsSub);
        simRobot.readLidar().subscribe(lidarSub);
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

        /*
         When moving the robot to a given direction until contact
         */
        long maxTime = 1500;
        do {
            robot.move(robotDir.toIntDeg(), MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || !robot.status().frontSensor()
                || !robot.status().rearSensor()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();


        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be detected
        assertFalse(robot.status().frontSensor());
        assertThat(contactsSub.values(), not(empty()));
        assertFalse(contactsSub.values().getFirst().frontSensors());

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

        /*
         * Then the robot should remain stopped at contact point
         */
        WheellyMotionMessage motion = findMessage(motionSub.values(), notBefore(0));
        assertNotNull(motion);
        assertEquals(0L, motion.simulationTime());
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // And when move ahead at max speed
        robot.move(robotDir.toIntDeg(), RobotSpec.MAX_PPS);
        robot.simulate();
        robot.close();

        // Then event flow should be completed without errors
        motionSub.assertComplete();
        motionSub.assertNoErrors();

        // And the robot should not change the location after movement
        motion = TestFunctions.findMessage(motionSub.values(), after(0));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        /*
         * and the sensor should signal no obstacle (head directed to right)
         */
        lidarSub.assertNoErrors();
        lidarSub.assertComplete();
        WheellyLidarMessage lidarMessage = findMessage(lidarSub.values(), after(0));
        assertNotNull(lidarMessage);
        assertEquals(0, lidarMessage.frontDistance());

        /*
         * and the proximity should signal 12 (front contacts),
         * and the forward block should be active
         * and the backward block should be inactive
         */
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        WheellyContactsMessage contact = findMessage(contactsSub.values(), notBefore(0));
        assertNotNull(contact);
        assertFalse(contact.frontSensors());
        assertTrue(contact.rearSensors());
        assertFalse(contact.canMoveForward());
        assertTrue(contact.canMoveBackward());
        assertEquals(10L, contact.simulationTime());

        // And no contact message after movement
        contact = findMessage(contactsSub.values(), after(500));
        assertNull(contact);
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
        long maxTime = 1500;
        do {
            robot.move(robotDir.toIntDeg(), MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || !robot.status().canMoveForward()
                || !robot.status().canMoveBackward()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();


        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be detected
        assertTrue(robot.status().frontSensor());
        assertFalse(robot.status().canMoveForward());
        assertThat(contactsSub.values(), not(empty()));
        assertTrue(contactsSub.values().getFirst().frontSensors());
        assertFalse(contactsSub.values().getFirst().canMoveForward());

        // And robot movement should be close the collision distance
        assertThat(robot.location().distance(location), closeTo(MM10, MM));
    }

    /*
     * Robot location for collision (250,250) = obstacle size 200 mm / 2 + robot radius 150 mm
     */
    @ParameterizedTest(name = "[{index}] Robot at ({0},{1}) head R{2} speed {3} pps")
    @CsvSource({
            // x,y, dir, sensorDir, speed, expMovement
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
        robotDeg = robotDirection.toIntDeg();
        Complex sensorDirection = Complex.DEG0;
        robot = createRobotWithObstacles(robotLocation, robotDirection, sensorDirection);

        // When connect and wait for simulated 500 ms
        robot.syncConnect();


        /*
         When moving the robot to a given direction until contact
         */
        long maxTime = 1500;
        do {
            robot.move(robotDeg, MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime));
        robot.close();
        robot.readContacts()
                .ignoreElements()
                .blockingAwait();

        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();

        // And no collision should be detected before moving
        WheellyContactsMessage contact = findMessage(contactsSub.values(), before(500));
        assertNull(contact);

        /*
         And collision should be detected after moving
         */
        contact = findMessage(contactsSub.values(), notBefore(500));
        assertNull(contact);

        /*
         and the last robot location should be the expected location
         */
        Point2D moveLocation = robotDirection.at(robotLocation, expMovement * MM1);
        motionSub.assertComplete();
        motionSub.assertNoErrors();
        assertThat(motionSub.values(), hasSize(greaterThanOrEqualTo(1)));
        WheellyMotionMessage motion = motionSub.values().getLast();
        double movement = robotLocation.distance(motion.robotLocation());
        assertThat(movement, closeTo(abs(expMovement * MM1), EPSILON_COLLISION));
        assertThat(motion.robotLocation(), pointCloseTo(moveLocation, EPSILON_COLLISION));
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
        long maxTime = 1500;
        do {
            robot.move(locationDir.toIntDeg(), -MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || !robot.status().frontSensor()
                || !robot.status().rearSensor()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();


        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be detected
        assertFalse(robot.status().rearSensor());
        assertThat(contactsSub.values(), not(empty()));
        assertFalse(contactsSub.values().getFirst().rearSensors());

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

        /*
         * Then the robot should remain stopped at contact point
         */
        WheellyMotionMessage motion = findMessage(motionSub.values(), notBefore(0));
        assertNotNull(motion);
        assertEquals(0L, motion.simulationTime());
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // And when move backward at max speed
        robot.move(locationDir.toIntDeg(), -RobotSpec.MAX_PPS);
        robot.simulate();
        robot.close();

        // Then event flow should be completed without errors
        motionSub.assertComplete();
        motionSub.assertNoErrors();

        // And the robot should not change the location after movement
        motion = TestFunctions.findMessage(motionSub.values(), after(0));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        /*
         * and the sensor should signal no rear obstacle (head directed to right)
         */
        lidarSub.assertNoErrors();
        lidarSub.assertComplete();
        WheellyLidarMessage lidarMessage = findMessage(lidarSub.values(), after(0));
        assertNotNull(lidarMessage);
        assertEquals(0, lidarMessage.rearDistance());

        /*
         * and the proximity should signal 12 (front contacts),
         * and the forward block should be active
         * and the backward block should be inactive
         */
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        WheellyContactsMessage contact = findMessage(contactsSub.values(), notBefore(0));
        assertNotNull(contact);
        assertTrue(contact.frontSensors());
        assertFalse(contact.rearSensors());
        assertTrue(contact.canMoveForward());
        assertFalse(contact.canMoveBackward());
        assertEquals(10L, contact.simulationTime());

        // And no contact message after movement
        contact = findMessage(contactsSub.values(), after(500));
        assertNull(contact);
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
        long maxTime = 1500;
        do {
            robot.move(locationDir.toIntDeg(), -MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || !robot.status().canMoveForward()
                || !robot.status().canMoveBackward()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();


        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be detected
        assertTrue(robot.status().rearSensor());
        assertFalse(robot.status().canMoveBackward());
        assertThat(contactsSub.values(), not(empty()));
        assertTrue(contactsSub.values().getFirst().rearSensors());
        assertFalse(contactsSub.values().getFirst().canMoveBackward());

        // And robot movement should be close the collision distance
        assertThat(robot.location().distance(location), closeTo(MM10, MM));
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

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.status().canMoveForward());

        // When moving backward
        long maxTime = 1500;
        do {
            robot.move(robotDir.toIntDeg(), -MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || robot.status().canMoveForward() && robot.status().canMoveBackward()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();

        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.status().frontSensor());
        assertThat(contactsSub.values(), not(empty()));
        assertTrue(contactsSub.values().getLast().canMoveForward());
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
        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.status().frontSensor());

        // When moving backward
        long maxTime = 1500;
        do {
            robot.move(robotDir.toIntDeg(), -MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || robot.status().frontSensor() && robot.status().rearSensor()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();

        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.status().frontSensor());
        assertThat(contactsSub.values(), not(empty()));
        assertTrue(contactsSub.values().getLast().frontSensors());
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

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.status().canMoveBackward());

        // When moving forward
        long maxTime = 1500;
        do {
            robot.move(locationDir.toIntDeg(), MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || robot.status().canMoveForward() && robot.status().canMoveBackward()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();

        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.status().canMoveBackward());
        assertThat(contactsSub.values(), not(empty()));
        assertTrue(contactsSub.values().getLast().canMoveBackward());
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

        /*
         * When connect robot
         */
        robot.syncConnect();
        robot.simulate();

        // Then collision should be detected
        assertFalse(robot.status().rearSensor());

        // When moving forward
        long maxTime = 1500;
        do {
            robot.move(locationDir.toIntDeg(), MAX_PPS);
            robot.simulate();
        } while (!(robot.simulationTime() >= maxTime
                || robot.status().frontSensor() && robot.status().rearSensor()));
        robot.close();
        robot.readContacts().ignoreElements().blockingAwait();

        // Then
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        assertTrue(robot.simulationTime() < maxTime);

        // And collision should be no longer detected
        assertTrue(robot.status().rearSensor());
        assertThat(contactsSub.values(), not(empty()));
        assertTrue(contactsSub.values().getLast().rearSensors());
    }
}