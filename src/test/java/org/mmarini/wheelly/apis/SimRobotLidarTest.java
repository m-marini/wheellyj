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

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.wheelly.TestFunctions.findMessage;
import static org.mmarini.wheelly.TestFunctions.notBefore;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotSpec.*;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.apis.Utils.m2mm;

public class SimRobotLidarTest {

    public static final long SEED = 1234;
    public static final double MIN_OBSTALCE_DISTANCE = DEFAULT_OBSTACLE_RADIUS + ROBOT_RADIUS + MM;
    public static final double MAX_OBSTALCE_DISTANCE = MAX_RADAR_DISTANCE - DEFAULT_OBSTACLE_RADIUS - DEFAULT_HEAD_Y - DEFAULT_FRONT_LIDAR_DISTANCE;
    public static final double CENTRES_DISTANCE = MIN_OBSTALCE_DISTANCE / sin(toRadians((double) DEFAULT_LIDAR_FOV_DEG / 2));
    public static final long CHANGE_MAP_PERIOD = 600000;

    public static Stream<Arguments> dataLidarFarObstacle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5.0, 5.0, 17) // x
                .uniform(-5.0, 5.0, 17) // x
                .uniform(0, 359) // robot dir
                .uniform(-90, 90) // head dir
                .uniform(0, 359) // obs dir
                .choice(MAX_RADAR_DISTANCE + MM, MAX_RADAR_DISTANCE + 1) // obs distance
                .build(100);
    }

    public static Stream<Arguments> dataLidarFarOuterObstacle() {
        int outerAngleDeg = (int) (round(2 * toDegrees(asin(DEFAULT_OBSTACLE_RADIUS / MAX_OBSTALCE_DISTANCE)) + (double) DEFAULT_LIDAR_FOV_DEG / 2) + 1);
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5.0, 5.0, 17) // x
                .uniform(-5.0, 5.0, 17) // x
                .uniform(0, 359) // robot dir
                .uniform(-90, 90) // head dir
                .uniform(outerAngleDeg, 360 - outerAngleDeg, 3) // obs dir
                .choice(MAX_OBSTALCE_DISTANCE, MAX_OBSTALCE_DISTANCE) // obs distance
                .build(100);
    }

    public static Stream<Arguments> dataLidarInnerObstacle() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5.0, 5.0, 17) // x
                .uniform(-5.0, 5.0, 17) // x
                .uniform(0, 359) // robot dir
                .uniform(-90, 90) // head dir
                .uniform(-10, 10) // obs dir
                .choice(MIN_OBSTALCE_DISTANCE, MAX_OBSTALCE_DISTANCE) // obs distance
                .build(100);
    }

    public static Stream<Arguments> dataLidarNearOuterObstacle() {
        double d = MIN_OBSTALCE_DISTANCE + CENTRES_DISTANCE;
        int outerAngleDeg = (int) (round(2 * toDegrees(asin(DEFAULT_OBSTACLE_RADIUS / d)) + (double) DEFAULT_LIDAR_FOV_DEG / 2) + 1);
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-5.0, 5.0, 17) // x
                .uniform(-5.0, 5.0, 17) // x
                .uniform(0, 359) // robot dir
                .uniform(-90, 90) // head dir
                .uniform(outerAngleDeg, 360 - outerAngleDeg, 3) // obs dir
                .choice(d, d) // obs distance
                .build(100);
    }

    private SimRobot robot;
    private TestSubscriber<WheellyLidarMessage> lidarSub;

    /**
     * Adds an obstacle at given distance + obstacle radius directed to the direction from the front lidar
     *
     * @param obsDirDeg   the direction (DEG)
     * @param obsDistance the distance (m)
     */
    void createFrontObstacle(int obsDirDeg, double obsDistance) {
        Point2D lidar = robot.frontLidarLocation();
        Complex headDir = robot.headAbsDirection();
        Complex obsDir = headDir.add(Complex.fromDeg(obsDirDeg));
        Point2D obsLoc = obsDir.at(lidar, obsDistance + DEFAULT_OBSTACLE_RADIUS);
        Obstacle obstacle = new Obstacle(obsLoc, DEFAULT_OBSTACLE_RADIUS, null);
        robot.obstacleMap(List.of(obstacle));
    }

    /**
     * Adds an obstacle at given distance + obstacle radius directed to the direction from the rear lidar
     *
     * @param obsDirDeg   the direction (DEG)
     * @param obsDistance the distance (m)
     */
    void createRearObstacle(int obsDirDeg, double obsDistance) {
        Point2D lidar = robot.rearLidarLocation();
        Complex headDir = robot.headAbsDirection();
        Complex obsDir = headDir.opposite().add(Complex.fromDeg(obsDirDeg));
        Point2D obsLoc = obsDir.at(lidar, obsDistance + DEFAULT_OBSTACLE_RADIUS);
        Obstacle obstacle = new Obstacle(obsLoc, DEFAULT_OBSTACLE_RADIUS, null);
        robot.obstacleMap(List.of(obstacle));
    }

    void createRobot(double xRobot, double yRobot, int robotDirDeg, int headDirDeg) {
        robot = new SimRobot(DEFAULT_ROBOT_SPEC, new Random(SEED), new Random(SEED),
                0, 10,
                SimRobot.DEFAULT_MOTION_INTERVAL, 5, SimRobot.DEFAULT_CAMERA_INTERVAL, SimRobot.DEFAULT_STALEMATE_INTERVAL,
                0, 0, SimRobot.DEFAULT_MAX_ANGULAR_SPEED, List.of(), 1, 0,
                CHANGE_MAP_PERIOD, CHANGE_MAP_PERIOD);
        robot.robotPos(xRobot, yRobot);
        robot.robotDir(Complex.fromDeg(robotDirDeg));
        robot.sensorDirection(Complex.fromDeg(headDirDeg));
        robot.readLidar().subscribe(lidarSub);
    }

    @BeforeEach
    void setUp() {
        this.lidarSub = new TestSubscriber<>();
    }

    @ParameterizedTest(name = "[{index}] @({0},{1}) R{2} Head {3} DEG obs {4} DEG D{5}")
    @MethodSource("dataLidarInnerObstacle")
    void testFrontLidarInnerObstacle(double xRobot, double yRobot, int robotDir, int headDir, int obsDir, double obsDistance) {
        // Given a robot location and direction and sensor direction
        // And front obstacle distance and direction
        createRobot(xRobot, yRobot, robotDir, headDir);
        createFrontObstacle(obsDir, obsDistance);

        // When connect and wait for simulated 500 ms
        robot.connect();
        robot.readLidar()
                .filter(msg -> msg.simulationTime() > 0)
                .blockingFirst();
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        lidarSub.assertComplete();
        lidarSub.assertNoErrors();

        // And the first proxy message after 1 ms should signal the obstacle
        WheellyLidarMessage lidar = findMessage(lidarSub.values(), notBefore(1));
        assertNotNull(lidar);
        assertEquals(m2mm(obsDistance), lidar.frontDistance());
    }

    @ParameterizedTest(name = "[{index}] @({0},{1}) R{2} Head {3} DEG obs {4} DEG D{5}")
    @MethodSource({
            "dataLidarNearOuterObstacle",
            "dataLidarFarOuterObstacle",
            "dataLidarFarObstacle"
    })
    void testFrontLidarOuterObstacle(double xRobot, double yRobot, int robotDir, int headDir, int obsDir, double obsDistance) {
        // Given a robot location and direction and sensor direction
        // And front obstacle distance and direction
        createRobot(xRobot, yRobot, robotDir, headDir);
        createFrontObstacle(obsDir, obsDistance);

        // When connect and wait for simulated 500 ms
        robot.connect();
        robot.readLidar()
                .filter(msg -> msg.simulationTime() > 0)
                .blockingFirst();
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        lidarSub.assertComplete();
        lidarSub.assertNoErrors();

        // And the first proxy message after 1 ms should signal the obstacle
        WheellyLidarMessage lidar = findMessage(lidarSub.values(), notBefore(1));
        assertNotNull(lidar);
        assertEquals(0, lidar.frontDistance());
    }

    @ParameterizedTest(name = "[{index}] @({0},{1}) R{2} Head {3} DEG obs {4} DEG D{5}")
    @MethodSource("dataLidarInnerObstacle")
    void testRearLidarInnerObstacle(double xRobot, double yRobot, int robotDir, int headDir, int obsDir, double obsDistance) {
        // Given a robot location and direction and sensor direction
        // And front obstacle distance and direction
        createRobot(xRobot, yRobot, robotDir, headDir);
        createRearObstacle(obsDir, obsDistance);

        // When connect and wait for simulated 500 ms
        robot.connect();
        robot.readLidar()
                .filter(msg -> msg.simulationTime() > 0)
                .blockingFirst();
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        lidarSub.assertComplete();
        lidarSub.assertNoErrors();

        // And the first proxy message after 1 ms should signal the obstacle
        WheellyLidarMessage lidar = findMessage(lidarSub.values(), notBefore(1));
        assertNotNull(lidar);
        assertEquals(m2mm(obsDistance), lidar.rearDistance());
    }

    @ParameterizedTest(name = "[{index}] @({0},{1}) R{2} Head {3} DEG obs {4} DEG D{5}")
    @MethodSource({
            "dataLidarNearOuterObstacle",
            "dataLidarFarOuterObstacle",
            "dataLidarFarObstacle"
    })
    void testRearLidarOuterObstacle(double xRobot, double yRobot, int robotDir, int headDir, int obsDir, double obsDistance) {
        // Given a robot location and direction and sensor direction
        // And front obstacle distance and direction
        createRobot(xRobot, yRobot, robotDir, headDir);
        createRearObstacle(obsDir, obsDistance);

        // When connect and wait for simulated 500 ms
        robot.connect();
        robot.readLidar()
                .filter(msg -> msg.simulationTime() > 0)
                .blockingFirst();
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        lidarSub.assertComplete();
        lidarSub.assertNoErrors();

        // And the first proxy message after 1 ms should signal the obstacle
        WheellyLidarMessage lidar = findMessage(lidarSub.values(), notBefore(1));
        assertNotNull(lidar);
        assertEquals(0, lidar.rearDistance());
    }
}
