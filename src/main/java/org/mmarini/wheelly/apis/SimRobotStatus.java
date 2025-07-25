/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import java.util.Objects;

import static org.mmarini.wheelly.apis.SimRobot.SAFE_DISTANCE;

/**
 * The simulated robot status
 *
 * @param simulationTime      the simulation time (ms)
 * @param connected           true if robot connected
 * @param closed              true if roboto closed
 * @param stalemate           true if robot is stalemate
 * @param sensorDirection     the sensor direction
 * @param echoDistance        the echo distance (m)
 * @param frontSensor         true if front sensor without contact
 * @param rearSensor          true if rear sensor without contect
 * @param direction           the robot direction
 * @param speed               the speed (pps)
 * @param leftPps             the left speed (pps)
 * @param rightPps            the right speed (pps)
 * @param obstacleMap         the obstacle map
 * @param motionTimeout       the motion message timeout (ms)
 * @param proxyTimeout        the proxy message timeout (ms)
 * @param cameraTimeout       the camera event timeout (ms)
 * @param stalemateTimeout    the stalemate timeout (ms)
 * @param nearestCell         the nearest obstacle cell in sensor range
 * @param startSimulationTime the start simulation instant (ns)
 * @param lastTick            the last tick instant (ns)
 */
public record SimRobotStatus(
        long simulationTime,
        boolean connected,
        boolean closed,
        boolean stalemate, Complex sensorDirection, double echoDistance, boolean frontSensor, boolean rearSensor,
        Complex direction, int speed, double leftPps, double rightPps,
        ObstacleMap obstacleMap,
        long motionTimeout, long proxyTimeout, long cameraTimeout,
        long stalemateTimeout, ObstacleMap.ObstacleCell nearestCell, long startSimulationTime,
        long lastTick) implements RobotStatusApi {

    /**
     * Returns status with the changed camera timeout
     *
     * @param cameraTimeout the camera timeout (ms)
     */
    public SimRobotStatus cameraTimeout(long cameraTimeout) {
        return this.cameraTimeout == cameraTimeout
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns true if robot can move backward
     */
    public boolean canMoveBackward() {
        return rearSensor;
    }

    /**
     * Returns true if robot can move forward
     */
    public boolean canMoveForward() {
        return frontSensor && (echoDistance == 0 || echoDistance > SAFE_DISTANCE);
    }

    /**
     * Returns status with the changed closed flag
     *
     * @param closed true if the robot is closed
     */
    public SimRobotStatus closed(boolean closed) {
        return this.closed == closed
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
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
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    @Override
    public boolean connecting() {
        return false;
    }

    /**
     * Returns status with the robot direction
     *
     * @param direction the robot direction
     */
    public SimRobotStatus direction(Complex direction) {
        return Objects.equals(this.direction, direction)
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed echo distance
     *
     * @param echoDistance the echo distance (m)
     */
    public SimRobotStatus echoDistance(double echoDistance) {
        return this.echoDistance == echoDistance
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed front sensor
     *
     * @param frontSensor true if the front sensor is clear
     */
    public SimRobotStatus frontSensor(boolean frontSensor) {
        return this.frontSensor == frontSensor
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed last tick
     *
     * @param lastTick the last tick instant (ns)
     */
    public SimRobotStatus lastTick(long lastTick) {
        return this.lastTick == lastTick
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed left speed
     *
     * @param leftSpeed the left speed (pps)
     */
    public SimRobotStatus leftPps(double leftSpeed) {
        return this.leftPps == leftSpeed
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftSpeed, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed motion timeout
     *
     * @param motionTimeout the motion timeout (ms)
     */
    public SimRobotStatus motionTimeout(long motionTimeout) {
        return this.motionTimeout == motionTimeout
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed nearest cell
     *
     * @param nearestCell the nearest cell
     */
    public SimRobotStatus nearestCell(ObstacleMap.ObstacleCell nearestCell) {
        return Objects.equals(this.nearestCell, nearestCell)
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed proxy timeout
     *
     * @param proxyTimeout the proxy timeout (ms)
     */
    public SimRobotStatus proxyTimeout(long proxyTimeout) {
        return this.proxyTimeout == proxyTimeout
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed front sensor
     *
     * @param rearSensor true if the rear sensor is clear
     */
    public SimRobotStatus rearSensor(boolean rearSensor) {
        return this.rearSensor == rearSensor
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed right speed
     *
     * @param rightSpeed the right speed (pps)
     */
    public SimRobotStatus rightPps(double rightSpeed) {
        return this.rightPps == rightSpeed
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightSpeed, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed sensor direction
     *
     * @param sensorDirection the robot direction
     */
    public SimRobotStatus sensorDirection(Complex sensorDirection) {
        return Objects.equals(this.sensorDirection, sensorDirection)
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed obstacle map
     *
     * @param obstacleMap the obstacle map
     */
    public SimRobotStatus setObstacleMap(ObstacleMap obstacleMap) {
        return this.obstacleMap == obstacleMap
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed simulation time
     *
     * @param simulationTime simultation time (ms)
     */
    public SimRobotStatus simulationTime(long simulationTime) {
        return this.simulationTime == simulationTime
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed speed
     *
     * @param speed the speed
     */
    public SimRobotStatus speed(int speed) {
        return this.speed == speed
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed stalemate flag
     *
     * @param stalemate true if the robot is stalemate
     */
    public SimRobotStatus stalemate(boolean stalemate) {
        return this.stalemate == stalemate
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed stalemate timeout
     *
     * @param stalemateTimeout the stalemate timeout (ms)
     */
    public SimRobotStatus stalemateTimeout(long stalemateTimeout) {
        return this.stalemateTimeout == stalemateTimeout
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }

    /**
     * Returns status with the changed start simulation instant
     *
     * @param startSimulationTime the start simulation instant (ns)
     */
    public SimRobotStatus startSimulationTime(long startSimulationTime) {
        return this.startSimulationTime == startSimulationTime
                ? this
                : new SimRobotStatus(simulationTime, connected, closed, stalemate, sensorDirection, echoDistance, frontSensor, rearSensor, direction, speed, leftPps, rightPps, obstacleMap, motionTimeout, proxyTimeout, cameraTimeout, stalemateTimeout, nearestCell, startSimulationTime, lastTick);
    }
}
