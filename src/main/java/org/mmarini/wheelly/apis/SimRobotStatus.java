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

import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static java.lang.Math.clamp;
import static org.mmarini.wheelly.apis.Obstacle.DEFAULT_OBSTACLE_RADIUS;
import static org.mmarini.wheelly.apis.RobotStatusId.HALT;
import static org.mmarini.wheelly.apis.SimRobot.LABEL;
import static org.mmarini.wheelly.apis.SimRobot.SAFE_DISTANCE;
import static org.mmarini.wheelly.apis.Utils.expRandom;

/**
 * The simulated robot status
 *
 * @param simulationTime      the simulation time (ms)
 * @param status              the status of robot
 * @param targetDirection     the target rotation direction
 * @param target              the target movement
 * @param connected           true if robot connected
 * @param closed              true if robot closed
 * @param stalemate           true if robot is stalemate
 * @param headRotation        the head angle
 * @param frontDistance       the front obstacle distance (m)
 * @param rearDistance        the rear obstacle distance (m)
 * @param frontSensor         true if front sensor without contact
 * @param rearSensor          true if rear sensor without contact
 * @param leftPps             the left power (pps)
 * @param rightPps            the right power (pps)
 * @param motionTimeout       the motion message timeout (ms)
 * @param lidarTimeout        the lidar message timeout (ms)
 * @param cameraTimeout       the camera event timeout (ms)
 * @param stalemateTimeout    the stalemate timeout (ms)
 * @param startSimulationTime the start simulation instant (ns)
 * @param lastTick            the last tick instant (ns)
 * @param template            the template map
 * @param obstacleMap         the obstacle map
 * @param mapExpiration       the map expiration (change map time) (ms)
 * @param randomMapExpiration the map random expiration (change che content of the map) (ms)
 */
public record SimRobotStatus(
        long simulationTime,
        RobotStatusId status,
        Complex targetDirection, Point2D target,
        boolean connected,
        boolean closed,
        boolean stalemate, Complex headRotation, double frontDistance, double rearDistance,
        boolean frontSensor, boolean rearSensor,
        double leftPps, double rightPps,
        long motionTimeout, long lidarTimeout, long cameraTimeout, long stalemateTimeout, long startSimulationTime,
        long lastTick, Collection<Obstacle> template, Collection<Obstacle> obstacleMap,
        long mapExpiration,
        long randomMapExpiration
) implements RobotStatusApi {
    public static final double MAX_OBSTACLE_DISTANCE = 3;
    public static final double MIN_OBSTACLE_DISTANCE = 1;

    /**
     * Returns status with the changed camera timeout
     *
     * @param cameraTimeout the camera timeout (ms)
     */
    public SimRobotStatus cameraTimeout(long cameraTimeout) {
        return this.cameraTimeout == cameraTimeout
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns true if robot can move backward
     */
    public boolean canMoveBackward() {
        return rearSensor && (rearDistance == 0 || rearDistance > SAFE_DISTANCE);
    }

    /**
     * Returns true if robot can move forward
     */
    public boolean canMoveForward() {
        return frontSensor && (frontDistance == 0 || frontDistance > SAFE_DISTANCE);
    }

    /**
     * Returns status with the changed closed flag
     *
     * @param closed true if the robot is closed
     */
    public SimRobotStatus closed(boolean closed) {
        return this.closed == closed
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns the status with motor speed composed by linear speed and rotation speed
     *
     * @param linear   linear speed (pps)
     * @param rotation rotation speed (pps)
     */
    public SimRobotStatus composeSpeed(double linear, double rotation) {
        return speed(linear + rotation, linear - rotation);
    }

    @Override
    public boolean configured() {
        return connected;
    }

    @Override
    public boolean configuring() {
        return false;
    }

    /**
     * Returns status with the changed connected flag
     *
     * @param connected true if the robot is connected
     */
    public SimRobotStatus connected(boolean connected) {
        return this.connected == connected
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation, frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap, mapExpiration, randomMapExpiration);
    }

    @Override
    public boolean connecting() {
        return false;
    }

    /**
     * Creates a new obstacle map from the current template and sets the map expiration and random map expiration
     *
     * @param random          the random number generator
     * @param robotLocation   the robot location
     * @param numObstacles    the number of obstacles
     * @param numLabels       the number of labels
     * @param mapPeriod       the map period (ms)
     * @param mapRandomPeriod the random map period (ms)
     */
    public SimRobotStatus createObstacleMap(Random random, Point2D robotLocation,
                                            int numObstacles, int numLabels,
                                            long mapPeriod, long mapRandomPeriod) {
        List<Obstacle> map = MapBuilder.create(template)
                // add obstacles
                .rand(random, DEFAULT_OBSTACLE_RADIUS, null, new Point2D.Double(), MAX_OBSTACLE_DISTANCE,
                        robotLocation, MIN_OBSTACLE_DISTANCE, numObstacles)
                // add labels
                .rand(random, DEFAULT_OBSTACLE_RADIUS, LABEL, new Point2D.Double(), MAX_OBSTACLE_DISTANCE,
                        robotLocation, MIN_OBSTACLE_DISTANCE, numLabels)
                .build();
        long mapExpiration = simulationTime + expRandom(random, mapPeriod);
        long mapRandomExpiration = simulationTime + expRandom(random, mapRandomPeriod);
        return obstacleMap(map)
                .mapExpiration(mapExpiration)
                .randomMapExpiration(mapRandomExpiration);
    }

    /**
     * Returns status with the front distance
     *
     * @param frontDistance the front distance (m)
     */
    public SimRobotStatus frontDistance(double frontDistance) {
        return this.frontDistance == frontDistance
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed front sensor
     *
     * @param frontSensor true if the front sensor is clear
     */
    public SimRobotStatus frontSensor(boolean frontSensor) {
        return this.frontSensor == frontSensor
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns the halt status
     */
    public SimRobotStatus halt() {
        return status(HALT)
                .speed(0, 0);
    }

    /**
     * Returns status with the changed head rotation
     *
     * @param headRotation the head rotation
     */
    public SimRobotStatus headRotation(Complex headRotation) {
        return Objects.equals(this.headRotation, headRotation)
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate,
                headRotation, frontDistance, rearDistance, frontSensor, rearSensor,
                leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime,
                lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed last tick
     *
     * @param lastTick the last tick instant (ns)
     */
    public SimRobotStatus lastTick(long lastTick) {
        return this.lastTick == lastTick
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed lidar timeout
     *
     * @param lidarTimeout the lidar timeout (ms)
     */
    public SimRobotStatus lidarTimeout(long lidarTimeout) {
        return this.lidarTimeout == lidarTimeout
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed map expiration
     *
     * @param mapExpiration the map expiration instant (ns)
     */
    public SimRobotStatus mapExpiration(long mapExpiration) {
        return this.mapExpiration == mapExpiration
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed motion timeout
     *
     * @param motionTimeout the motion timeout (ms)
     */
    public SimRobotStatus motionTimeout(long motionTimeout) {
        return this.motionTimeout == motionTimeout
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed obstacle map
     *
     * @param obstacleMap the obstacle map
     */
    public SimRobotStatus obstacleMap(Collection<Obstacle> obstacleMap) {
        return !Objects.equals(this.obstacleMap, obstacleMap)
                ? new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration)
                : this;
    }

    /**
     * Returns status with the changed map random expiration
     *
     * @param randomMapExpiration the map random expiration instant (ns)
     */
    public SimRobotStatus randomMapExpiration(long randomMapExpiration) {
        return this.randomMapExpiration == randomMapExpiration
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the rear distance
     *
     * @param rearDistance the rear distance (m)
     */
    public SimRobotStatus rearDistance(double rearDistance) {
        return this.rearDistance == rearDistance
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed front sensor
     *
     * @param rearSensor true if the rear sensor is clear
     */
    public SimRobotStatus rearSensor(boolean rearSensor) {
        return this.rearSensor == rearSensor
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed simulation time
     *
     * @param simulationTime simulation time (ms)
     */
    public SimRobotStatus simulationTime(long simulationTime) {
        return this.simulationTime == simulationTime
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns the status with left and right motor speed
     *
     * @param leftPps  the left speed (pps)
     * @param rightPps the right speed (pps)
     */
    public SimRobotStatus speed(double leftPps, double rightPps) {
        leftPps = clamp(leftPps, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
        rightPps = clamp(rightPps, -RobotSpec.MAX_PPS, RobotSpec.MAX_PPS);
        return this.leftPps == leftPps
                && this.rightPps == rightPps
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed stalemate flag
     *
     * @param stalemate true if the robot is stalemate
     */
    public SimRobotStatus stalemate(boolean stalemate) {
        return this.stalemate == stalemate
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed stalemate timeout
     *
     * @param stalemateTimeout the stalemate timeout (ms)
     */
    public SimRobotStatus stalemateTimeout(long stalemateTimeout) {
        return this.stalemateTimeout == stalemateTimeout
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed start simulation instant
     *
     * @param startSimulationTime the start simulation instant (ns)
     */
    public SimRobotStatus startSimulationTime(long startSimulationTime) {
        return this.startSimulationTime == startSimulationTime
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed status id
     *
     * @param status the status id
     */
    public SimRobotStatus status(RobotStatusId status) {
        return Objects.equals(this.status, status)
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed target
     *
     * @param target the target
     */
    public SimRobotStatus target(Point2D target) {
        return Objects.equals(this.target, target)
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the changed target direction
     *
     * @param targetDirection the target direction
     */
    public SimRobotStatus targetDirection(Complex targetDirection) {
        return Objects.equals(this.targetDirection, targetDirection)
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }

    /**
     * Returns status with the template map
     *
     * @param template the template map
     */
    public SimRobotStatus template(Collection<Obstacle> template) {
        return Objects.equals(this.template, template)
                ? this
                : new SimRobotStatus(simulationTime, status, targetDirection, target, connected, closed, stalemate, headRotation,
                frontDistance, rearDistance, frontSensor, rearSensor, leftPps, rightPps, motionTimeout, lidarTimeout, cameraTimeout, stalemateTimeout, startSimulationTime, lastTick, template, obstacleMap,
                mapExpiration, randomMapExpiration);
    }
}
