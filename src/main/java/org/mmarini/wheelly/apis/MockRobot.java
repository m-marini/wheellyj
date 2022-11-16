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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

public class MockRobot implements RobotApi {
    public static final Validator ROBOT_SPEC = objectProperties(Map.of(
            "x", number(),
            "y", number(),
            "direction", integer(minimum(-180), maximum(180)),
            "sensor", integer(minimum(-90), maximum(90)),
            "distance", nonNegativeNumber()
    ));

    public static MockRobot create(JsonNode root, Locator locator) {
        ROBOT_SPEC.apply(locator).accept(root);
        float x = (float) locator.path("x").getNode(root).asDouble(0);
        float y = (float) locator.path("y").getNode(root).asDouble(0);
        Point2D robotPos1 = new Point2D.Float(x, y);
        int robotDir1 = locator.path("direction").getNode(root).asInt(0);
        int sensorDir1 = locator.path("sensor").getNode(root).asInt(0);
        float sensorDistance1 = (float) locator.path("distance").getNode(root).asDouble(0);
        return new MockRobot(robotPos1, robotDir1, sensorDir1, sensorDistance1);
    }


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
    public RadarMap getRadarMap() {
        return null;
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
                false);
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
