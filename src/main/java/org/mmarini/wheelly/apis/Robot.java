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
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Implements the Robot interface to the real robot
 */
public class Robot implements RobotApi {
    public static final int DEFAULT_PORT = 22;
    public static final long DEFAULT_CONNECTION_TIMEOUT = 10000;
    public static final long DEFAULT_READ_TIMEOUT = 3000;
    public static final int DEFAULT_RADAR_WIDTH = 50;
    public static final int DEFAULT_RADAR_HEIGHT = 50;
    public static final float DEFAULT_RADAR_GRID = 0.2F;
    public static final long DEFAULT_RADAR_PERSISTENCE = 300000L;
    public static final long DEFAULT_RADAR_CLEAN = 30000L;

    private static final Logger logger = LoggerFactory.getLogger(Robot.class);
    private static final Validator ROBOT_SPEC = objectPropertiesRequired(Map.of(
                    "host", string(),
                    "port", integer(minimum(1), maximum(32768)),
                    "connectionTimeout", positiveInteger(),
                    "readTimeout", positiveInteger(),
                    "radarWidth", positiveInteger(),
                    "radarHeight", positiveInteger(),
                    "radarGrid", positiveNumber(),
                    "radarCleanInterval", positiveInteger(),
                    "radarPersistence", positiveInteger()
            ),
            List.of("host"));

    /**
     * Returns an interface to the robot
     *
     * @param robotHost        the robot host
     * @param port             the robot port
     * @param radarMap         the radar map
     * @param radarPersistence
     * @param cleanInterval
     */
    public static Robot create(String robotHost, int port, RadarMap radarMap, long radarPersistence, long cleanInterval) {
        return Robot.create(robotHost, port, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT, radarMap, radarPersistence, cleanInterval);
    }

    public static Robot create(JsonNode root, Locator locator) {
        ROBOT_SPEC.apply(locator).accept(root);
        String host = locator.path("host").getNode(root).asText();
        int port = locator.path("port").getNode(root).asInt(DEFAULT_PORT);
        long connectionTimeout = locator.path("connectionTimeout").getNode(root).asLong(DEFAULT_CONNECTION_TIMEOUT);
        long readTimeout = locator.path("readTimeout").getNode(root).asLong(Robot.DEFAULT_READ_TIMEOUT);
        int radarWidth = locator.path("radarWidth").getNode(root).asInt(DEFAULT_RADAR_WIDTH);
        int radarHeight = locator.path("radarHeight").getNode(root).asInt(DEFAULT_RADAR_HEIGHT);
        float radarGrid = (float) locator.path("radarGrid").getNode(root).asDouble(DEFAULT_RADAR_GRID);
        long radarCleanInterval = locator.path("radarCleanInterval").getNode(root).asLong(DEFAULT_RADAR_CLEAN);
        long radarPersistence = locator.path("radarPersistence").getNode(root).asLong(DEFAULT_RADAR_PERSISTENCE);
        RadarMap radarMap = RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid);
        return Robot.create(host, port, connectionTimeout, readTimeout, radarMap, radarPersistence, radarCleanInterval);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost         the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout in millis
     * @param readTimeout       the read timeout in millis
     * @param radarMap          the radar map
     * @param radarPersistence
     * @param cleanInterval
     */
    public static Robot create(String robotHost, int port, long connectionTimeout, long readTimeout, RadarMap radarMap, long radarPersistence, long cleanInterval) {
        RobotSocket socket = new RobotSocket(robotHost, port, connectionTimeout, readTimeout);
        return new Robot(socket, radarMap, radarPersistence, cleanInterval);
    }

    private final RobotSocket socket;
    private final RadarMap radarMap;
    private final long radarPersistence;
    private final long cleanInterval;
    private Long timestampOffset;
    private long time;
    private long resetTime;
    private WheellyStatus status;
    private long cleanTimeout;

    /**
     * Create a Robot interface
     *
     * @param socket           the robot socket
     * @param radarMap
     * @param radarPersistence
     * @param cleanInterval
     */
    public Robot(RobotSocket socket, RadarMap radarMap, long radarPersistence, long cleanInterval) {
        this.socket = socket;
        this.radarMap = radarMap;
        this.radarPersistence = radarPersistence;
        this.cleanInterval = cleanInterval;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public boolean getCanMoveBackward() {
        return status != null && !status.getCannotMoveBackward();
    }

    @Override
    public boolean getCanMoveForward() {
        return status != null && !status.getCannotMoveForward();
    }

    @Override
    public int getContacts() {
        return status != null ? status.getContactSensors() : 0;
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
        return radarMap;
    }

    @Override
    public int getRobotDir() {
        return status != null ? status.getRobotDeg() : 0;
    }

    @Override
    public Point2D getRobotPos() {
        return status != null ? status.getRobotLocation() : new Point2D.Float();
    }

    @Override
    public int getSensorDir() {
        return status != null ? status.getSensorRelativeDeg() : 0;
    }

    @Override
    public float getSensorDistance() {
        return status != null ? (float) status.getSampleDistance() : 0;
    }

    @Override
    public WheellyStatus getStatus() {
        return status;
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public void halt() {
        writeCommand("al");
    }

    @Override
    public void move(int dir, float speed) {
        writeCommand(format(Locale.ENGLISH, "mv %d %.2f", dir, speed));
    }

    @Override
    public void reset() {
        time = System.currentTimeMillis();
        resetTime = time;
        status = null;
    }

    @Override
    public void scan(int dir) {
        writeCommand("sc " + dir);
    }

    @Override
    public void start() {
        try {
            socket.connect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Synchronizes the local clock with the remote clock
     */
    private void sync() {
        long now = System.currentTimeMillis();
        try {
            writeCommand("ck " + now);
            for (; ; ) {
                Timed<String> line = socket.readLine();
                if (line != null) {
                    try {
                        ClockSyncEvent clock = ClockSyncEvent.from(line.value(), line.time(TimeUnit.MILLISECONDS));
                        if (now == clock.getOriginateTimestamp()) {
                            timestampOffset = clock.getRemoteOffset();
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void tick(long dt) {
        if (timestampOffset == null) {
            sync();
        }
        long timeout = time + dt;
        try {
            while (time < timeout) {
                status = null;
                time = System.currentTimeMillis();
                Timed<String> line = socket.readLine();
                if (line != null) {
                    try {
                        status = WheellyStatus.from(line.value());
                        time = line.time(TimeUnit.MILLISECONDS);
                        if (radarMap != null) {
                            RadarMap.SensorSignal signal = new RadarMap.SensorSignal(this.getRobotPos(),
                                    normalizeDegAngle(getRobotDir() + getSensorDir()),
                                    (float) status.getSampleDistance(), time);
                            radarMap.update(signal);
                            if (time >= this.cleanTimeout) {
                                radarMap.clean(time - this.radarPersistence);
                                cleanTimeout = time + cleanInterval;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes a command to robot
     *
     * @param cmd the command
     */
    private void writeCommand(String cmd) {
        try {
            socket.writeCommand(cmd);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}
