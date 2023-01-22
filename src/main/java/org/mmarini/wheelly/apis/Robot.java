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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static java.lang.Math.round;
import static java.lang.String.format;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Implements the Robot interface to the real robot
 */
public class Robot implements RobotApi, WithIOCallback {
    public static final int DEFAULT_PORT = 22;

    private static final Validator ROBOT_SPEC = objectPropertiesRequired(Map.of(
                    "host", string(),
                    "port", integer(minimum(1), maximum(32768)),
                    "connectionTimeout", positiveInteger(),
                    "readTimeout", positiveInteger(),
                    "configCommands", arrayItems(string(minLength(3)))
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

        Locator configCommandsLoc = locator.path("configCommands");
        String[] configCommands = !configCommandsLoc.getNode(root).isMissingNode()
                ? configCommandsLoc.elements(root).map(l -> l.getNode(root).asText()).toArray(String[]::new)
                : new String[0];
        return Robot.create(host, port, connectionTimeout, readTimeout, configCommands);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost         the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configCommands    the configuration commands
     */
    public static Robot create(String robotHost, int port, long connectionTimeout, long readTimeout, String... configCommands) {
        RobotSocket socket = new RobotSocket(robotHost, port, connectionTimeout, readTimeout);
        return new Robot(socket, configCommands);
    }

    private final RobotSocket socket;
    private final String[] configCommands;
    private RobotStatus status;
    private Consumer<String> onReadLine;
    private Consumer<String> onWriteLine;
    private boolean initialized;

    /**
     * Create a Robot interface
     *
     * @param socket         the robot socket
     * @param configCommands the motor theta corrections
     */
    public Robot(RobotSocket socket, String[] configCommands) {
        this.socket = socket;
        status = RobotStatus.create();
        this.configCommands = configCommands;
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

    private void init() throws InterruptedException {
        sync();
        for (String cmd : configCommands) {
            writeCommand(cmd);
            Thread.sleep(200);
        }
        initialized = true;
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
    public void setOnReadLine(Consumer<String> onReadLine) {
        this.onReadLine = onReadLine;
    }

    @Override
    public void setOnWriteLine(Consumer<String> onWriteLine) {
        this.onWriteLine = onWriteLine;
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
                    if (onReadLine != null) {
                        onReadLine.accept(line.value());
                    }
                    try {
                        ClockSyncEvent clock = ClockSyncEvent.from(line.value(), line.time(TimeUnit.MILLISECONDS));
                        if (now == clock.getOriginateTimestamp()) {
                            clock.getRemoteOffset();
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
        if (!initialized){
            try {
                init();
            } catch (InterruptedException e) {
            }
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
                    if (onReadLine != null) {
                        onReadLine.accept(line.value());
                    }
                    logger.atDebug().setMessage(">>> {}").addArgument(line::value).log();
                    if (line.value().startsWith("!!")) {
                        logger.atError().setMessage(">>> {}").addArgument(line::value).log();
                    }
                    if (line.value().startsWith("//")) {
                        logger.atWarn().setMessage(">>> {}").addArgument(line::value).log();
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
            if (onWriteLine != null) {
                onWriteLine.accept(cmd);
            }
            socket.writeCommand(cmd);
        } catch (IOException ex) {
            logger.atError().setCause(ex).log();
        }
    }
}
