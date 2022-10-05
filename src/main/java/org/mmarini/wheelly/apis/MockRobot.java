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

import org.mmarini.wheelly.model.WheellyStatus;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class MockRobot implements RobotApi {
    private long time;
    private long resetTime;
    private Point2D robotPos;
    private int robotDir;
    private int sensorDir;
    private float sensorDistance;

    public MockRobot() {
        this(new Point2D.Float(), 0, 0, 0);
    }

    public MockRobot(Point2D robotPos, int robotDir, int sensorDir, float sensorDistance) {
        this.robotPos = requireNonNull(robotPos);
        this.robotDir = robotDir;
        this.sensorDir = sensorDir;
        this.sensorDistance = sensorDistance;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public boolean getCanMoveBackward() {
        return false;
    }

    @Override
    public boolean getCanMoveForward() {
        return false;
    }

    @Override
    public int getContacts() {
        return 0;
    }

    @Override
    public long getElapsed() {
        return time - resetTime;
    }

    @Override
    public Optional<ObstacleMap> getObstaclesMap() {
        return Optional.empty();
    }

    @Override
    public int getRobotDir() {
        return robotDir;
    }

    public void setRobotDir(int robotDir) {
        this.robotDir = robotDir;
    }

    @Override
    public Point2D getRobotPos() {
        return robotPos;
    }

    public void setRobotPos(Point2D robotPos) {
        this.robotPos = robotPos;
    }

    @Override
    public int getSensorDir() {
        return sensorDir;
    }

    public void setSensorDir(int sensorDir) {
        this.sensorDir = sensorDir;
    }

    @Override
    public float getSensorDistance() {
        return sensorDistance;
    }

    public void setSensorDistance(float sensorDistance) {
        this.sensorDistance = sensorDistance;
    }

    @Override
    public WheellyStatus getStatus() {
        return WheellyStatus.create(robotPos, robotDir, sensorDir, sensorDistance, 0,
                0, 0, 0, false, false, false,
                false, 0, 0, 0);
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public void halt() {
    }

    @Override
    public void move(int dir, float speed) {
    }

    @Override
    public void reset() {
        resetTime = time;
    }

    @Override
    public void scan(int dir) {
    }

    @Override
    public void start() {
    }

    @Override
    public void tick(long dt) {
        time += dt;
    }
}
