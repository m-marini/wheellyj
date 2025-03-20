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
import org.mmarini.yaml.Locator;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.function.Consumer;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

public class MockRobot implements RobotApi {
    public static final double CONTACT_RADIUS = 0.3;
    public static final int RECEPTIVE_ANGLE_DEG = 15;
    public static final double MAX_RADAR_DISTANCE = 3d;
    public static final RobotSpec ROBOT_SPEC = new RobotSpec(MAX_RADAR_DISTANCE, Complex.fromDeg(RECEPTIVE_ANGLE_DEG), CONTACT_RADIUS);

    public static MockRobot create(JsonNode root, Locator locator) {
        float x = (float) locator.path("x").getNode(root).asDouble(0);
        float y = (float) locator.path("y").getNode(root).asDouble(0);
        Point2D robotPos1 = new Point2D.Float(x, y);
        Complex robotDir1 = Complex.fromDeg(locator.path("direction").getNode(root).asInt(0));
        Complex sensorDir1 = Complex.fromDeg(locator.path("sensor").getNode(root).asInt(0));
        float sensorDistance1 = (float) locator.path("distance").getNode(root).asDouble(0);
        return new MockRobot(robotPos1, robotDir1, sensorDir1, sensorDistance1);
    }

    private final Point2D robotPos;
    private final Complex robotDir;
    private final Complex sensorDir;
    private final float sensorDistance;
    protected Consumer<WheellyMotionMessage> onMotion;
    protected Consumer<WheellyProxyMessage> onProxy;
    protected Consumer<WheellyContactsMessage> onContacts;
    protected Consumer<ClockSyncEvent> onClock;
    private long simulationTime;

    public MockRobot() {
        this(new Point2D.Float(), Complex.DEG0, Complex.DEG0, 0);
    }

    public MockRobot(Point2D robotPos, Complex robotDir, Complex sensorDir, float sensorDistance) {
        this.robotPos = requireNonNull(robotPos);
        this.robotDir = robotDir;
        this.sensorDir = sensorDir;
        this.sensorDistance = sensorDistance;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void configure() throws IOException {
        if (onClock != null) {
            onClock.accept(new ClockSyncEvent(simulationTime, simulationTime, simulationTime, simulationTime));
        }
        sendMotion();
        sendProxy();
        sendContacts();
    }

    @Override
    public void connect() throws IOException {
    }

    public RobotStatus getStatus() {
        return RobotStatus.create(ROBOT_SPEC, x -> 12d)
                .setDirection(robotDir)
                .setSensorDirection(sensorDir)
                .setEchoDistance(sensorDistance)
                .setLocation(robotPos);
    }

    @Override
    public void halt() {
    }

    @Override
    public boolean isHalt() {
        return false;
    }

    @Override
    public void move(Complex dir, int speed) throws IOException {

    }

    @Override
    public RobotSpec robotSpec() {
        return ROBOT_SPEC;
    }

    @Override
    public void scan(Complex dir) {
    }

    protected void sendContacts() {
        if (onContacts != null) {
            onContacts.accept(
                    new WheellyContactsMessage(
                            System.currentTimeMillis(), simulationTime, simulationTime,
                            true, true,
                            true, true)
            );
        }
    }

    protected void sendMotion() {
        if (onMotion != null) {
            onMotion.accept(
                    new WheellyMotionMessage(
                            System.currentTimeMillis(), simulationTime, simulationTime,
                            robotPos.getX() / RobotStatus.DISTANCE_PER_PULSE,
                            robotPos.getY() / RobotStatus.DISTANCE_PER_PULSE,
                            robotDir.toIntDeg(),
                            0, 0,
                            0, true,
                            0, 0,
                            0, 0
                    ));
        }
    }

    protected void sendProxy() {
        if (onProxy != null) {
            onProxy.accept(
                    new WheellyProxyMessage(
                            System.currentTimeMillis(), simulationTime, simulationTime,
                            sensorDir.toIntDeg(), round(sensorDistance / RobotStatus.DISTANCE_SCALE),
                            robotPos.getX() / RobotStatus.DISTANCE_PER_PULSE,
                            robotPos.getY() / RobotStatus.DISTANCE_PER_PULSE,
                            robotDir.toIntDeg())
            );
        }
    }

    @Override
    public void setOnCamera(Consumer<CameraEvent> callback) {

    }

    @Override
    public void setOnClock(Consumer<ClockSyncEvent> callback) {
        onClock = callback;
    }

    @Override
    public void setOnContacts(Consumer<WheellyContactsMessage> callback) {
        onContacts = callback;
    }

    @Override
    public void setOnMotion(Consumer<WheellyMotionMessage> callback) {
        onMotion = callback;
    }

    @Override
    public void setOnProxy(Consumer<WheellyProxyMessage> callback) {
        onProxy = callback;
    }

    @Override
    public void setOnSupply(Consumer<WheellySupplyMessage> callback) {
    }

    public void setSimulationTime(long simulationTime) {
        this.simulationTime = simulationTime;
    }

    @Override
    public long simulationTime() {
        return simulationTime;
    }

    @Override
    public void tick(long dt) {
        simulationTime += dt;
        sendMotion();
        sendProxy();
        sendContacts();
    }
}
