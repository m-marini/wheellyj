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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.TestFunctions.*;
import static org.mmarini.wheelly.apis.MockRobot.ROBOT_SPEC;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class SimRobotObstacleTest {

    public static final int SEED = 1234;
    public static final double DISTANCE_EPSILON = 1.5e-3;
    public static final double MM1 = 1e-3;
    public static final double EPSILON_COLLISION = 5 * MM1;
    public static final double ROBOT_RADIUS = 150e-3;
    public static final float GRID_SIZE = 200e-3f;
    public static final int STALEMATE_INTERVAL = 60000;
    public static final long MESSAGE_INTERVAL = 500;
    public static final long INTERVAL = 10;
    public static final long CHANGE_OBSTACLES_PERIOD = 100000L;
    private static final double HALF_SIZE = 100e-3;
    private static final double HALF_LENGTH = 140e-3;
    private static final Logger logger = LoggerFactory.getLogger(SimRobotObstacleTest.class);

    public static Stream<Arguments> dataLabel() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(0, 359) // robotLocationDeg
                .uniform(0, 1)   // robotLocationDistance
                .uniform(0, 359) // robotDeg
                .uniform(-90, 90) // sensorDeg
                .uniform(-15, 15) // markerDeg
                .uniform(1, 2) // markerDistance
                .build(100);
    }

    private SimRobot robot;
    private TestSubscriber<WheellyContactsMessage> contactsSub;
    private TestSubscriber<WheellyProxyMessage> proxySub;
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
        MapBuilder mapBuilder = MapBuilder.create(GRID_SIZE);
        for (int i = 0; i < obsCoords.length - 1; i += 2) {
            mapBuilder.add(false, obsCoords[i], obsCoords[i + 1]);
        }
        return createRobot(location, robotDirection, sensorDirection, mapBuilder.build());
    }

    private SimRobot createRobot(Point2D location, Complex robotDirection, Complex sensorDirection, ObstacleMap map) {
        Random random = new Random(SEED);
        SimRobot simRobot = new SimRobot(ROBOT_SPEC, random, random,
                INTERVAL, 0, MESSAGE_INTERVAL, MESSAGE_INTERVAL, MESSAGE_INTERVAL, STALEMATE_INTERVAL, CHANGE_OBSTACLES_PERIOD,
                0, 0, RobotSpec.MAX_PPS, 0, 0);
        simRobot.robotPos(location.getX(), location.getY());
        simRobot.robotDir(robotDirection);
        simRobot.sensorDirection(sensorDirection);
        simRobot.obstacleMap(map);
        this.proxySub = new TestSubscriber<>();
        this.contactsSub = new TestSubscriber<>();
        this.motionSub = new TestSubscriber<>();
        simRobot.readMotion().subscribe(motionSub);
        simRobot.readContacts().subscribe(contactsSub);
        simRobot.readProxy().subscribe(proxySub);
        return simRobot;
    }

    /**
     * Given a simulated robot with a map grid of 0.2 m and an obstacle at 0,0
     * located at the given location and directed to the given direction
     *
     * @param location       the location
     * @param robotDirection the robot direction
     */
    private SimRobot createRobot(Point2D location, Complex robotDirection) {
        return createRobot(location, robotDirection, Complex.DEG0, 0, 0);
    }

    private SimRobot createRobotWithLabel(Point2D location, Complex robotDirection, Complex sensorDirection, Point2D labelLocation) {
        Point index = ObstacleMap.toIndex(labelLocation.getX(), labelLocation.getY(), GRID_SIZE);
        ObstacleMap.ObstacleCell cell = new ObstacleMap.ObstacleCell(index, labelLocation, true);
        ObstacleMap map = new ObstacleMap(List.of(cell), GRID_SIZE);
        return createRobot(location, robotDirection, sensorDirection, map);
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
                GRID_SIZE, 0,
                0, GRID_SIZE);
    }

    /*
     * Robot location for collision (250,250) = obstacle size 200 mm / 2 + robot radius 150 mm
     */
    @ParameterizedTest(name = "[{index}] Robot at ({0},{1}) head R{2} speed {3} pps")
    @CsvSource({
            // x,y, dir, sensorDir, speed, expFrontBlock,expRearBlock, expMovement
            // rear collisions from robot +(10,10)
            "260,260, 90,0, -60, false,true, -10",
            "260,260, 0,0,  -60, false,true, -10",
            "260,260, 45,0, -60, false,true, -14",

            // front collisions from robot +(10,10)
            "260,260, 270,90, 60, true,false, 10",
            "260,260, 180,-90, 60, true,false, 10",
    })
    void testCollision(int robotX, int robotY, int robotDeg, int sensorDeg,
                       int speed,
                       boolean expFrontCollision, boolean expBackCollision, int expMovement) {
        /*
         Given the obstacle map with 3 obstacles
         and the robot located at the given location directed to the given direction
         and sensor directed to the given direction
         */
        Point2D robotLocation = new Point2D.Double(robotX * MM1, robotY * MM1);
        Complex robotDirection = Complex.fromDeg(robotDeg);
        robotDeg = robotDirection.toIntDeg();
        Complex sensorDirection = Complex.fromDeg(sensorDeg);
        robot = createRobotWithObstacles(robotLocation, robotDirection, sensorDirection);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);

        /*
         When moving the robot to a given direction until contact
         */
        long maxTime = 1500;
        robot.move(robotDeg, speed);
        int finalRobotDeg = robotDeg;
        robot.readMotion().subscribe(ev ->
                robot.move(finalRobotDeg, speed)
        );
        try {
            robot.readContacts()
                    .firstElement()
                    .timeout(maxTime, TimeUnit.MILLISECONDS)
                    .blockingGet();
        } catch (Throwable err) {
        }
        robot.close();

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
        if (expBackCollision || expFrontCollision) {
            assertNotNull(contact);
            assertEquals(expFrontCollision, !contact.canMoveForward());
            assertEquals(expBackCollision, !contact.canMoveBackward());
        } else {
            assertNull(contact);
        }

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
    void testContactFront(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        Complex robotDir = locationDir.opposite();
        robot = createRobot(location, robotDir);
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);

        // And move ahead at max speed
        robot.move(robotDir.toIntDeg(), RobotSpec.MAX_PPS);
        pause(robot, 2 * MESSAGE_INTERVAL);
        robot.close();

        // Then
        motionSub.assertComplete();
        motionSub.assertNoErrors();
        // And the robot should not change the location
        WheellyMotionMessage motion = findMessage(motionSub.values(), notBefore(0));
        assertNotNull(motion);
        assertEquals(0L, motion.simulationTime());
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        // And the robot should not change the location after movement
        motion = TestFunctions.findMessage(motionSub.values(), after(500));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // and the proxy sensor should signal the obstacle at 140 mm
        proxySub.assertNoErrors();
        proxySub.assertComplete();
        WheellyProxyMessage proxy = findMessage(proxySub.values(), after(0));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(ROBOT_RADIUS + MM1, DISTANCE_EPSILON));
        // And the proxy sensor should signal the obstacle at 140 mm
        proxy = findMessage(proxySub.values(), after(500));
        assertNotNull(proxy);
        assertThat(robot.echoDistance(), closeTo(ROBOT_RADIUS, DISTANCE_EPSILON));

        // And front contact
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
    void testContactFrontMoveBack(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        robot = createRobot(location, locationDir.opposite());
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);
        // And move back at half-speed for 500 ms
        robot.move(locationDir.opposite().toIntDeg(), -RobotSpec.MAX_PPS / 2);
        pause(robot, 1000);
        robot.close();

        // And the robot should not change the location
        motionSub.assertNoErrors();
        motionSub.assertComplete();
        WheellyMotionMessage motion = findMessage(motionSub.values(), after(0));
        assertNotNull(motion);
        assertEquals(10L, motion.simulationTime());
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        // And the robot should not change the location after move
        motion = findMessage(motionSub.values(), after(510));
        assertNotNull(motion);
        double ds = 0;
        Point2D movePoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS + ds);
        assertThat(motion.robotLocation(), pointCloseTo(movePoint, DISTANCE_EPSILON));

        // and the proxy sensor should signal the obstacle at 140 mm
        proxySub.assertComplete();
        proxySub.assertNoErrors();
        WheellyProxyMessage proxy = findMessage(proxySub.values(), after(0));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(ROBOT_RADIUS, 2 * DISTANCE_EPSILON));
        // And proxy sensor should measure 140+39 mm of distance
        proxy = findMessage(proxySub.values(), after(510));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(ROBOT_RADIUS + ds, DISTANCE_EPSILON));

        // And front contact
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        WheellyContactsMessage contact = findMessage(contactsSub.values(), notBefore(0));
        assertNotNull(contact);
        assertFalse(contact.frontSensors());
        assertTrue(contact.rearSensors());
        assertFalse(contact.canMoveForward());
        assertTrue(contact.canMoveBackward());
        // And front contact
        contact = findMessage(contactsSub.values(), after(510));
        assertNotNull(contact);
        assertTrue(contact.frontSensors());
        assertTrue(contact.rearSensors());
        // and can move back and not forward (proxy block)
        assertFalse(contact.canMoveForward());
        assertTrue(contact.canMoveBackward());
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
    void testContactRear(int locationDeg) {
        // Given a robot directed to locationDeg
        Complex locationDir = Complex.fromDeg(locationDeg);
        // and located near the obstacle in the origin toward the robot direction
        Point2D location = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        robot = createRobot(location, locationDir);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);

        // And move back at half-speed for 500 ms
        robot.move(locationDeg, -RobotSpec.MAX_PPS / 2);
        pause(robot, 1010);
        robot.close();

        // And the robot should not change the location
        motionSub.assertComplete();
        motionSub.assertNoErrors();
        WheellyMotionMessage motion = findMessage(motionSub.values(), after(0));
        assertNotNull(motion);
        assertEquals(10, motion.simulationTime());
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        // And location after move
        motion = findMessage(motionSub.values(), after(510));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));

        // And front contact
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        WheellyContactsMessage contact = findMessage(contactsSub.values(), notBefore(0));
        assertNotNull(contact);
        assertTrue(contact.frontSensors());
        assertFalse(contact.rearSensors());
        assertTrue(contact.canMoveForward());
        assertFalse(contact.canMoveBackward());
        // And no message contact after move
        contact = findMessage(contactsSub.values(), after(500));
        assertNull(contact);


        // And proxy after move
        proxySub.assertComplete();
        proxySub.assertNoErrors();
        WheellyProxyMessage proxy = findMessage(proxySub.values(), TestFunctions.after(500));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(0, DISTANCE_EPSILON));
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
    void testContactRearMoveForward(int locationDeg) {
        // Given positioning robot at (0.76, 0)
        Complex locationDir = Complex.fromDeg(locationDeg);
        Point2D initialLocation = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS - MM1);
        robot = createRobot(initialLocation, locationDir);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);

        // When move front at half-speed for 1010 ms
        robot.move(locationDeg, RobotSpec.MAX_PPS / 2);
        pause(robot, 1010);
        robot.close();

        // Then
        // And the robot should not change the location
        WheellyMotionMessage motion = findMessage(motionSub.values(), after(0));
        assertNotNull(motion);
        assertEquals(10L, motion.simulationTime());
        Point2D contactPoint = locationDir.at(new Point2D.Float(), GRID_SIZE / 2 + ROBOT_RADIUS);
        assertThat(motion.robotLocation(), pointCloseTo(contactPoint, DISTANCE_EPSILON));
        // And the robot should be located ahead after the movement
        motion = findMessage(motionSub.values(), after(510));
        assertNotNull(motion);
        double expMovement = 1.5e-3;
        Point2D movePoint = locationDir.at(initialLocation, expMovement);
        double movement = motion.robotLocation().distance(initialLocation);
        assertThat(movement, closeTo(expMovement, DISTANCE_EPSILON));
        assertThat(motion.robotLocation(), pointCloseTo(movePoint, DISTANCE_EPSILON));

        // and the proxy sensor should signal no obstacle
        proxySub.assertComplete();
        proxySub.assertNoErrors();
        WheellyProxyMessage proxy = findMessage(proxySub.values(), after(0));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(0, DISTANCE_EPSILON));

        // And front contact
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        WheellyContactsMessage contact = findMessage(contactsSub.values(), after(0));
        assertNotNull(contact);
        assertTrue(contact.frontSensors());
        assertFalse(contact.rearSensors());
        assertTrue(contact.canMoveForward());
        assertFalse(contact.canMoveBackward());
        // And front contact
        contact = findMessage(contactsSub.values(), after(510));
        assertNotNull(contact);
        assertTrue(contact.frontSensors());
        assertTrue(contact.rearSensors());
        assertTrue(contact.canMoveForward());
        assertTrue(contact.canMoveBackward());
    }

    /*
     *
     */
    @ParameterizedTest
    @MethodSource("dataLabel")
    void testLabel(int robotLocationDeg, double robotLocationDistance,
                   int robotDeg, int sensorDeg, int markerDeg, double markerDistance) {
        // Given a simulated robot
        // with a labelled marker
        Point2D location = Complex.fromDeg(robotLocationDeg).at(new Point2D.Float(), robotLocationDistance);
        Point2D labelLocation = Complex.fromDeg(robotDeg + sensorDeg + markerDeg).at(location, markerDistance);
        robot = createRobotWithLabel(location, Complex.fromDeg(robotDeg), Complex.fromDeg(sensorDeg),
                labelLocation);
        TestSubscriber<CameraEvent> messagesSub = new TestSubscriber<>();
        robot.readCamera()
                .limit(1)
                .doOnNext(m -> logger.atDebug().log("t={}", m.simulationTime()))
                .subscribe(messagesSub);

        // When connect and wait for simulated 500 ms
        robot.connect();
        robot.halt();
        pause(robot, MESSAGE_INTERVAL);
        robot.close();

        // Then ...
        messagesSub.assertNoErrors();
        messagesSub.assertComplete();
        List<CameraEvent> messages = messagesSub.values();

        assertThat(robot.direction(), angleCloseTo(robotDeg, 1));
        assertThat(robot.location(), pointCloseTo(location, MM1));

        // And the camera event should be a label directed as expected
        assertThat(messages, hasSize(1));
        CameraEvent event = messages.getFirst();
        assertNotNull(event);
        assertEquals("A", event.qrCode());
        assertThat(event.direction(), angleCloseTo(markerDeg, 1));
    }

    @ParameterizedTest(name = "[{index}] R{0}, {1} PPS, contacts {2},{3}")
    @CsvSource({
            "0,-60, false,true",

            "45,0, false,true",
            "315,0, false,true",

            "225,0, false,true",
            "135,0, false,true",

            "180,0, false,true",

            "30,-30, false,true",
            "330,-30, false,true",

            "45,-30, false,true",
            "315,-30, false,true",

            "60,-30, false,true",
            "300,-30, false,true",

            "90,-30, false,true",
            "270,-30, false,true",
    })
    void testMove(int moveDeg, int speed, boolean canMoveForward, boolean canMoveBackward) {
        // Given the robot simulator in a map with 3 obstacles at 1, R0 from the robot
        Point2D robotLocation = new Point2D.Double();
        Complex robotDirection = Complex.DEG0;
        Complex sensorDirection = Complex.DEG90;
        robot = createRobot(robotLocation, robotDirection, sensorDirection,
                -0.2, 1,
                0, 1,
                0.2, 1
        );

        // When connect
        robot.connect();
        // And turning robot to 0 DEG
        robot.move(0, RobotSpec.MAX_PPS);
        robot.readMotion().subscribe(msg -> {
            logger.atDebug().log("t={} move(0,MAX_PPS)", msg.simulationTime());
            robot.move(0, RobotSpec.MAX_PPS);
        });
        // And wait for simulated 1500 ms or con move forward
        waitFor(robot.readContacts(), msg -> !msg.canMoveForward(), 5000);
        long contactTime = robot.simulationTime();

        // And turning the robot to the test direction at the test speed
        robot.move(moveDeg, speed);
        robot.readMotion().subscribe(msg -> {
            logger.atDebug().log("t={} move({}, {})", msg.simulationTime(), moveDeg, speed);
            robot.move(moveDeg, speed);
        });
        pause(robot, contactTime + 2000);
        robot.close();

        // Then ...
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        motionSub.assertComplete();
        motionSub.assertNoErrors();
        proxySub.assertComplete();
        proxySub.assertNoErrors();

        // And contact time
        WheellyContactsMessage contact = findMessage(contactsSub.values(), notBefore(contactTime));
        assertNotNull(contact);
        assertThat(contact.simulationTime(), equalTo(contactTime));
        assertEquals(canMoveForward, contact.canMoveForward());
        assertEquals(canMoveBackward, contact.canMoveBackward());

        // And
        WheellyMotionMessage motion = findMessage(motionSub.values(), notBefore(contactTime));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(0, 0.750, DISTANCE_EPSILON));

        // Then the robot should be directed to the move direction after movement
        assertFalse(motionSub.values().isEmpty());
        motion = motionSub.values().getLast();
        assertThat(motion.direction(), angleCloseTo(Complex.fromDeg(moveDeg), Complex.fromDeg(1)));
    }

    /*
     * Robot location for collision (250,250) = obstacle size 200 mm / 2 + robot radius 150 mm
     */
    @ParameterizedTest(name = "[{index}] Robot at ({0},{1}) head R{2} speed {3} pps")
    @CsvSource({
            // x,y, dir, sensorDir, speed, expFrontBlock,expRearBlock, expMovement
            "0,1000, 0,0,   60, false,false, 422", // no collision, max movement = 416mm
            "0,1000, 270,0, 60, false,false, 422", // no collision, max movement = 416mm
    })
    void testNoCollision(int robotX, int robotY, int robotDeg, int sensorDeg,
                         int speed,
                         boolean expFrontCollision, boolean expBackCollision, int expMovement) {
        /*
         Given the obstacle map with 3 obstacles
         and the robot located at the given location directed to the given direction
         and sensor directed to the given direction
         */
        Point2D robotLocation = new Point2D.Double(robotX * MM1, robotY * MM1);
        Complex robotDirection = Complex.fromDeg(robotDeg);
        robotDeg = robotDirection.toIntDeg();
        Complex sensorDirection = Complex.fromDeg(sensorDeg);
        robot = createRobotWithObstacles(robotLocation, robotDirection, sensorDirection);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);

        /*
         When moving the robot to a given direction until contact
         */
        long maxTime = 1500;
        robot.move(robotDeg, speed);
        int finalRobotDeg = robotDeg;
        robot.readMotion().subscribe(ev ->
                robot.move(finalRobotDeg, speed));
        robot.readMotion()
                .filter(t -> t.simulationTime() > maxTime)
                .firstElement()
                .blockingGet();
        robot.close();

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
        if (expBackCollision || expFrontCollision) {
            assertNotNull(contact);
            assertEquals(expFrontCollision, !contact.canMoveForward());
            assertEquals(expBackCollision, !contact.canMoveBackward());
        } else {
            assertNull(contact);
        }

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
    void testObstacleFront(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        double dr = 40e-3;
        Point2D location = locationDir.at(new Point2D.Float(), HALF_LENGTH + HALF_SIZE + dr);
        robot = createRobot(location, locationDir.opposite());

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);
        // And moving backward
        robot.move(locationDir.opposite().toIntDeg(), 1);
        pause(robot, 2 * MESSAGE_INTERVAL);
        robot.close();

        // Then ...
        assertThat(robot.location(), pointCloseTo(location, DISTANCE_EPSILON));
        assertThat(robot.echoDistance(), closeTo(HALF_LENGTH + dr, DISTANCE_EPSILON));

        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        motionSub.assertComplete();
        motionSub.assertNoErrors();
        proxySub.assertComplete();
        proxySub.assertNoErrors();

        // And the first proxy message after 1 ms should signal the obstacle
        WheellyProxyMessage proxy = findMessage(proxySub.values(), notBefore(1));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(HALF_LENGTH + dr, DISTANCE_EPSILON));
        // And the first proxy after the move command (500 ms)
        // it should signal the obstacle
        proxy = findMessage(proxySub.values(), notBefore(501));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(HALF_LENGTH + dr, DISTANCE_EPSILON));

        // And the first motion message after 1 ms should signal the obstacle
        WheellyMotionMessage motion = findMessage(motionSub.values(), notBefore(1));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(location, DISTANCE_EPSILON));
        // And the first motion after the move command (500 ms)
        // should have robot located at location (no move ???)
        motion = findMessage(motionSub.values(), notBefore(501));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(location, DISTANCE_EPSILON));

        // And the first contact message should signal no forward move possibility (echo proxy)
        assertFalse(contactsSub.values().isEmpty());
        WheellyContactsMessage contact = contactsSub.values().getFirst();
        assertFalse(contact.canMoveForward());
        assertTrue(contact.canMoveBackward());
        assertTrue(contact.frontSensors());
        assertTrue(contact.rearSensors());
        assertThat(contact, field("simulationTime", equalTo(INTERVAL)));
        // And there should not be any contacts after the move command (500 ms)
        contact = findMessage(contactsSub.values(), notBefore(501));
        assertNull(contact);
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
    void testObstacleRear(int locationDeg) {
        Complex locationDir = Complex.fromDeg(locationDeg);
        double dr = 40e-3;
        Point2D location = locationDir.at(new Point2D.Float(), ROBOT_RADIUS + GRID_SIZE / 2 + dr);
        robot = createRobot(location, locationDir);

        // When connect and wait for simulated 500 ms
        robot.connect();
        pause(robot, MESSAGE_INTERVAL);
        // And moving back
        robot.move(locationDeg, -RobotSpec.MAX_PPS);
        pause(robot, 2 * MESSAGE_INTERVAL);
        robot.close();

        // Then ...
        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        motionSub.assertComplete();
        motionSub.assertNoErrors();
        proxySub.assertComplete();
        proxySub.assertNoErrors();

        // And the first motion message after connection
        // should locate robot at initial location
        WheellyMotionMessage motion = findMessage(motionSub.values(), notBefore(1));
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(location, DISTANCE_EPSILON));
        // And the robot should be located near the obstacle (?)
        // at double dx = speed * DISTANCE_PER_PULSE * dt / 1000;
        motion = findMessage(motionSub.values(), notBefore(501));
        Point2D moveLocation = locationDir.at(location, -dr);
        assertNotNull(motion);
        assertThat(motion.robotLocation(), pointCloseTo(moveLocation, DISTANCE_EPSILON));

        // And the first proxy message after connection
        // should locate no obstacles
        WheellyProxyMessage proxy = findMessage(proxySub.values(), notBefore((1)));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(0, DISTANCE_EPSILON));
        // And the proxy message should signal no obstacles
        proxy = findMessage(proxySub.values(), notBefore(501));
        assertNotNull(proxy);
        assertThat(proxy.echoDistance(), closeTo(0, DISTANCE_EPSILON));

        // And should not be any contact message before starting the robot
        WheellyContactsMessage contact = findMessage(contactsSub.values(), m -> m.simulationTime() <= 500);
        assertNull(contact);
        // And the first contact message after the moving
        // should signal the rear obstacle
        contact = findMessage(contactsSub.values(), notBefore(501));
        assertNotNull(contact);
        assertTrue(contact.frontSensors());
        assertFalse(contact.rearSensors());
        assertTrue(contact.canMoveForward());
        assertFalse(contact.canMoveBackward());

    }
}