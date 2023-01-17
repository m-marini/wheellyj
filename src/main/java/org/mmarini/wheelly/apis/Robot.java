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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.round;
import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Implements the Robot interface to the real robot
 */
public class Robot implements RobotApi {
    public static final int DEFAULT_PORT = 22;

    private static final Validator NEG_THETA = array(
            prefixItems(
                    integer(minimum(-254), maximum(-1)),
                    integer(minimum(-255), maximum(0))),
            minItems(2), maxItems(2));

    private static final Validator POS_THETA = array(
            prefixItems(
                    integer(minimum(1), maximum(254)),
                    integer(minimum(0), maximum(255))),
            minItems(2), maxItems(2));

    private static final Validator THETA_SPEC = array(
            prefixItems(NEG_THETA, POS_THETA),
            minItems(2), maxItems(2));
    private static final Validator ROBOT_SPEC = objectPropertiesRequired(Map.of(
                    "host", string(),
                    "port", integer(minimum(1), maximum(32768)),
                    "connectionTimeout", positiveInteger(),
                    "readTimeout", positiveInteger(),
                    "leftTheta", THETA_SPEC,
                    "rightTheta", THETA_SPEC
            ),
            List.of("host",
                    "connectionTimeout",
                    "readTimeout"
            ));
    private static final Logger logger = LoggerFactory.getLogger(Robot.class);
    private static final int MAX_SPEED_VALUE = 4;

    /**
     * Returns the robot from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of robot spec
     */
    public static Robot create(JsonNode root, Locator locator) {
        ROBOT_SPEC.apply(locator).accept(root);
        String host = locator.path("host").getNode(root).asText();
        int port = locator.path("port").getNode(root).asInt(DEFAULT_PORT);
        long connectionTimeout = locator.path("connectionTimeout").getNode(root).asLong();
        long readTimeout = locator.path("readTimeout").getNode(root).asLong();
        int[] motorTheta = new int[]{-128, -128, 128, 128, -128, -128, 128, 128};
        Locator leftThetaLoc = locator.path("leftTheta");
        if (!leftThetaLoc.getNode(root).isMissingNode()) {
            int[] theta = loadTheta(root, leftThetaLoc);
            System.arraycopy(theta, 0, motorTheta, 0, 4);
        }
        Locator rightThetaLoc = locator.path("rightTheta");
        if (!rightThetaLoc.getNode(root).isMissingNode()) {
            int[] theta = loadTheta(root, rightThetaLoc);
            System.arraycopy(theta, 0, motorTheta, 4, 4);
        }
        StringJoiner s = new StringJoiner(" ");
        Arrays.stream(motorTheta).mapToObj(String::valueOf).forEach(s::add);
        return Robot.create(host, port, connectionTimeout, readTimeout, s.toString());
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost         the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param motorTheta        the motor theta corrections
     */
    public static Robot create(String robotHost, int port, long connectionTimeout, long readTimeout, String motorTheta) {
        RobotSocket socket = new RobotSocket(robotHost, port, connectionTimeout, readTimeout);
        return new Robot(socket, motorTheta);
    }

    /**
     * Returns the theta values from configuration [x1, y1, x2, y2]
     *
     * @param root    the configuration document
     * @param locator the theta locator
     */
    private static int[] loadTheta(JsonNode root, Locator locator) {
        return locator.elements(root)
                .flatMap(l1 -> l1.elements(root)
                        .map(l2 -> l2.getNode(root).asInt()))
                .mapToInt(i -> i)
                .toArray();
    }

    private final RobotSocket socket;
    private final String motorTheta;
    private Long timestampOffset;
    private RobotStatus status;

    /**
     * Create a Robot interface
     *
     * @param socket     the robot socket
     * @param motorTheta the motor theta corrections
     */
    public Robot(RobotSocket socket, String motorTheta) {
        this.socket = socket;
        status = RobotStatus.create();
        this.motorTheta = motorTheta;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    @Override
    public RobotStatus getStatus() {
        return status;
    }

    @Override
    public void halt() {
        writeCommand("ha");
    }

    @Override
    public void move(int dir, double speed) {
        writeCommand(format(Locale.ENGLISH, "mv %d %d", dir, round(speed * MAX_SPEED_VALUE)));
    }

    @Override
    public void reset() {
        long time = System.currentTimeMillis();
        status = status.setTime(time).setResetTime(time);
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
            writeCommand("cm " + motorTheta);
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
                    logger.atDebug().setMessage(">>> {}").addArgument(line::value).log();
                    if (line.value().startsWith("!!")) {
                        logger.atError().setMessage(">>> {}").addArgument(line::value).log();
                    }
                    try {
                        // Create the new status
                        status = status.setWheellyStatus(WheellyStatus.create(line));
                    } catch (Throwable ignored) {
                    }
                }
            }
            logger.atDebug().setMessage(">>> {}").addArgument(status).log();
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
            logger.atError().setCause(ex).log();
        }
    }
}
