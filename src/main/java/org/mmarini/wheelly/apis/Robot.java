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
import java.util.concurrent.TimeUnit;

import static java.lang.Math.round;
import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Implements the Robot interface to the real robot
 */
public class Robot implements RobotApi {
    public static final int DEFAULT_PORT = 22;
    public static final long DEFAULT_CONNECTION_TIMEOUT = 10000;
    public static final long DEFAULT_READ_TIMEOUT = 3000;

    public static final int[] MOTOR_CONFIG = new int[]{-15, -78, 6, 32, -10, -68, 5, 32};

    private static final Logger logger = LoggerFactory.getLogger(Robot.class);
    private static final Validator ROBOT_SPEC = objectPropertiesRequired(Map.of(
                    "host", string(),
                    "port", integer(minimum(1), maximum(32768)),
                    "connectionTimeout", positiveInteger(),
                    "readTimeout", positiveInteger(),
                    "radarWidth", positiveInteger(),
                    "radarHeight", positiveInteger(),
                    "radarGrid", positiveNumber(),
                    "radarReceptiveDistance", positiveNumber(),
                    "radarCleanInterval", positiveInteger(),
                    "radarPersistence", positiveInteger()
            ),
            List.of("host",
                    "connectionTimeout",
                    "readTimeout",
                    "radarWidth",
                    "radarHeight",
                    "radarGrid",
                    "radarReceptiveDistance",
                    "radarCleanInterval",
                    "radarPersistence"
            ));
    private static final int MAX_SPEED_VALUE = 4;

    /**
     * Returns an interface to the robot
     *
     * @param robotHost              the robot host
     * @param port                   the robot port
     * @param radarMap               the radar map
     * @param radarPersistence       the radar persistence duration (ms)
     * @param cleanInterval          the radar clean interval (ms)
     * @param radarReceptiveDistance the radar receptive distance (m)
     */
    public static Robot create(String robotHost, int port, RadarMap radarMap, long radarPersistence, long cleanInterval, float radarReceptiveDistance) {
        return Robot.create(robotHost, port, DEFAULT_CONNECTION_TIMEOUT, DEFAULT_READ_TIMEOUT, radarMap, radarPersistence, cleanInterval, radarReceptiveDistance);
    }

    /**
     * Returns the robot from configuration
     *
     * @param root    the configuratin document
     * @param locator the locator of robot spec
     */
    public static Robot create(JsonNode root, Locator locator) {
        ROBOT_SPEC.apply(locator).accept(root);
        String host = locator.path("host").getNode(root).asText();
        int port = locator.path("port").getNode(root).asInt(DEFAULT_PORT);
        long connectionTimeout = locator.path("connectionTimeout").getNode(root).asLong();
        long readTimeout = locator.path("readTimeout").getNode(root).asLong();
        int radarWidth = locator.path("radarWidth").getNode(root).asInt();
        int radarHeight = locator.path("radarHeight").getNode(root).asInt();
        float radarGrid = (float) locator.path("radarGrid").getNode(root).asDouble();
        long radarCleanInterval = locator.path("radarCleanInterval").getNode(root).asLong();
        long radarPersistence = locator.path("radarPersistence").getNode(root).asLong();
        float radarReceptiveDistance = (float) locator.path("radarReceptiveDistance").getNode(root).asDouble();
        RadarMap radarMap = RadarMap.create(radarWidth, radarHeight, new Point2D.Float(), radarGrid);
        return Robot.create(host, port, connectionTimeout, readTimeout, radarMap, radarPersistence, radarCleanInterval, radarReceptiveDistance);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost              the robot host
     * @param port                   the robot port
     * @param connectionTimeout      the connection timeout (ms)
     * @param readTimeout            the read timeout (ms))
     * @param radarMap               the radar map
     * @param radarPersistence       the radar persistence duration (ms)
     * @param cleanInterval          the radar clean interval (ms)
     * @param radarReceptiveDistance the radar receptive distance (m)
     */
    public static Robot create(String robotHost, int port, long connectionTimeout, long readTimeout, RadarMap radarMap, long radarPersistence, long cleanInterval, float radarReceptiveDistance) {
        RobotSocket socket = new RobotSocket(robotHost, port, connectionTimeout, readTimeout);
        return new Robot(socket, radarMap, radarPersistence, cleanInterval, radarReceptiveDistance);
    }

    private final RobotSocket socket;
    private final long radarPersistence;
    private final long cleanInterval;
    private final float radarReceptiveDistance;
    private Long timestampOffset;
    private WheellyStatus status;
    private long cleanTimeout;

    /**
     * Create a Robot interface
     *
     * @param socket                 the robot socket
     * @param radarMap               the radar map
     * @param radarPersistence       the radar persistence duration (ms)
     * @param cleanInterval          the radar clean interval (ms)
     * @param radarReceptiveDistance the radar threshold distance (m)
     */
    public Robot(RobotSocket socket, RadarMap radarMap, long radarPersistence, long cleanInterval, float radarReceptiveDistance) {
        this.socket = socket;
        this.radarReceptiveDistance = radarReceptiveDistance;
        status = WheellyStatus.create();
        status.setRadarMap(radarMap);
        this.radarPersistence = radarPersistence;
        this.cleanInterval = cleanInterval;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public WheellyStatus getStatus() {
        return status;
    }

    @Override
    public void halt() {
        writeCommand("al");
    }

    @Override
    public void move(int dir, float speed) {
        writeCommand(format(Locale.ENGLISH, "mv %d %d", dir, round(speed * MAX_SPEED_VALUE)));
    }

    @Override
    public void reset() {
        long time = System.currentTimeMillis();
        status.setTime(time);
        status.setResetTime(time);
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
        long time = status.getTime();
        long timeout = time + dt;
        try {
            // Repeat until interval timeout
            while (time < timeout) {
                time = System.currentTimeMillis();
                // Read the robot status
                Timed<String> line = socket.readLine();
                if (line != null) {
                    logger.debug(">>> {}", line.value());
                    if (line.value().startsWith("!!")) {
                        logger.error(">>> {}", line.value());
                    }
                    try {
                        // Create the new status
                        status = status.updateFromString(line, radarReceptiveDistance);
                        RadarMap radarMap = status.getRadarMap();
                        if (radarMap != null) {
                            if (time >= this.cleanTimeout) {
                                radarMap.clean(time - this.radarPersistence);
                                cleanTimeout = time + cleanInterval;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            logger.debug(">>> {}", status);
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
