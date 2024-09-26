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
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Locale;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Implements the Robot interface to the real robot.
 * <p>
 * The usage samples is
 * <code><pre>
 *
 *     RobotApi robot = new Robot(...);
 *
 *     robot.connect();
 *
 *     robot.configure();
 *
 *     // Sending any command
 *     robot.haltCommand();
 *     robot.scan(...);
 *     robot.move(...);
 *
 *     // Setting the callback on events
 *     robot.setOnClock(...);
 *     robot.setOnMotion(...);
 *     robot.setOnProxy(...);
 *     robot.setOnContacts(...);
 *     robot.setOnSupply(...);
 *
 *     // Polling for status changed
 *     while (...) {
 *         robot.tick(...);
 *     }
 *
 *     robotSocket.close();
 * </pre>
 * </code>
 * </p>
 */
public class Robot implements RobotApi, WithIOCallback {
    public static final int DEFAULT_ROBOT_PORT = 22;
    public static final int DEFAULT_CAMERA_PORT = 8100;
    public static final int FLUSH_INTERVAL = 1000;
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/robot-schema-2.0";
    private static final Logger logger = LoggerFactory.getLogger(Robot.class);

    /**
     * Returns the robot from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of robot spec
     */
    public static Robot create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        String robotHost = locator.path("robotHost").getNode(root).asText();
        int robotPort = locator.path("robotPort").getNode(root).asInt(DEFAULT_ROBOT_PORT);
        String cameraHost = locator.path("cameraHost").getNode(root).asText();
        int cameraPort = locator.path("cameraPort").getNode(root).asInt(DEFAULT_CAMERA_PORT);
        long connectionTimeout = locator.path("connectionTimeout").getNode(root).asLong();
        long readTimeout = locator.path("readTimeout").getNode(root).asLong();
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong();

        Locator configCommandsLoc = locator.path("configCommands");
        String[] configCommands = !configCommandsLoc.getNode(root).isMissingNode()
                ? configCommandsLoc.elements(root).map(l -> l.getNode(root).asText()).toArray(String[]::new)
                : new String[0];
        return Robot.create(robotHost, robotPort,
                cameraHost, cameraPort,
                connectionTimeout, readTimeout, configureTimeout,
                configCommands);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost         the robot host
     * @param robotPort         the robot port
     * @param cameraHost        the camera host
     * @param cameraPort        the camera port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configureTimeout  the configuration timeout (ms)
     * @param configCommands    the configuration commands
     */
    public static Robot create(String robotHost, int robotPort,
                               String cameraHost, int cameraPort,
                               long connectionTimeout, long readTimeout, long configureTimeout,
                               String... configCommands) {
        LineSocket robotSocket1 = new LineSocket(robotHost, robotPort, connectionTimeout, readTimeout);
        LineSocket cameraSocket1 = new LineSocket(cameraHost, cameraPort, connectionTimeout, readTimeout);
        return new Robot(robotSocket1, cameraSocket1,
                configureTimeout,
                configCommands);
    }

    private final String[] configCommands;
    private final long configureTimeout;
    private final LineSocket robotSocket;
    private final LineSocket cameraSocket;
    private Consumer<String> onReadLine;
    private Consumer<String> onWriteLine;
    private Consumer<WheellyMotionMessage> onMotion;
    private Consumer<WheellyProxyMessage> onProxy;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellySupplyMessage> onSupply;
    private Consumer<ClockSyncEvent> onClock;
    private Consumer<CameraEvent> onCamera;
    private ClockConverter clockConverter;
    private long simulationTime;

    /**
     * Create a Robot interface
     *
     * @param robotSocket      the robot socket
     * @param cameraSocket     the camera socket
     * @param configureTimeout the configuration timeout (ms)
     * @param configCommands   the motor theta corrections
     */
    protected Robot(LineSocket robotSocket, LineSocket cameraSocket, long configureTimeout, String[] configCommands) {
        this.configCommands = requireNonNull(configCommands);
        this.clockConverter = ClockConverter.identity();
        this.configureTimeout = configureTimeout;
        this.robotSocket = robotSocket;
        this.cameraSocket = cameraSocket;
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        try {
            robotSocket.close();
        } catch (Exception ex) {
            logger.atError().setCause(ex).log();
        }
        try {
            cameraSocket.close();
        } catch (Exception ex) {
            logger.atError().setCause(ex).log();
        }
    }

    @Override
    public void configure() throws IOException {
        sync();
        for (String cmd : configCommands) {
            configure(cmd);
        }
    }

    /**
     * Sends the configuration command and wait for confirmation for a maximum of configured timeout
     *
     * @param command the configuration command
     * @throws IOException in case of error
     */
    private void configure(String command) throws IOException {
        writeCommand(command);
        long time = System.currentTimeMillis();
        long timeout = time + configureTimeout;
        // Repeat until interval timeout
        while (time < timeout) {
            time = System.currentTimeMillis();
            // Read the robot status
            Timed<String> line = readRobotLine();
            if (line != null) {
                if (line.value().equals("// " + command)) {
                    return;
                }
                parseForWheellyMessage(line);
            }
        }
        throw new InterruptedIOException(format("Timeout on configure %s", command));
    }

    @Override
    public void connect() throws IOException {
        robotSocket.connect();
        cameraSocket.connect();
    }

    /**
     * Flushes the message for interval
     */
    private void flush() throws IOException {
        long timeout = System.currentTimeMillis() + FLUSH_INTERVAL;
        while (System.currentTimeMillis() <= timeout) {
            Timed<String> line = readRobotLine();
            parseForWheellyMessage(line);
            if (line == null) {
                break;
            }
        }
    }

    @Override
    public void halt() throws IOException {
        writeCommand("ha");
    }

    @Override
    public void move(Complex dir, int speed) throws IOException {
        writeCommand(format(Locale.ENGLISH, "mv %d %d", dir.toIntDeg(), speed));
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with received message.
     *
     * @param line the data line received
     */
    private void parseForCameraMessage(Timed<String> line) {
        CameraEvent msg = CameraEvent.create(line.value());
        if (onCamera != null) {
            onCamera.accept(msg);
        }
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with received message.
     *
     * @param line the data line received
     */
    private void parseForWheellyMessage(Timed<String> line) {
        WheellyMessage.fromLine(line, clockConverter).ifPresent(msg -> {
            switch (msg) {
                case WheellyMotionMessage ignored -> {
                    if (onMotion != null) {
                        onMotion.accept((WheellyMotionMessage) msg);
                    }
                }
                case WheellyContactsMessage ignored -> {
                    if (onContacts != null) {
                        onContacts.accept((WheellyContactsMessage) msg);
                    }
                }
                case WheellyProxyMessage ignored -> {
                    if (onProxy != null) {
                        onProxy.accept((WheellyProxyMessage) msg);
                    }
                }
                case WheellySupplyMessage ignored -> {
                    if (onSupply != null) {
                        onSupply.accept((WheellySupplyMessage) msg);
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * Returns the line read from camera connection
     * Notifies the line if onReadLine call back has been registered
     *
     * @throws IOException in case of error
     */
    private Timed<String> readCameraLine() throws IOException {
        Timed<String> line = cameraSocket.readLine();
        logger.atDebug().setMessage("Read {}").addArgument(line).log();
        if (line != null) {
            if (onReadLine != null) {
                onReadLine.accept(line.value());
            }
        }
        return line;
    }

    /**
     * Returns the line read from robot connection
     * Notifies the line if onReadLine call back has been registered
     *
     * @throws IOException in case of error
     */
    private Timed<String> readRobotLine() throws IOException {
        Timed<String> line = robotSocket.readLine();
        logger.atDebug().setMessage("Read {}").addArgument(line).log();
        if (line != null) {
            if (onReadLine != null) {
                onReadLine.accept(line.value());
            }
        }
        return line;
    }

    @Override
    public void scan(Complex dir) throws IOException {
        writeCommand("sc " + dir.toIntDeg());
    }

    @Override
    public void setOnCamera(Consumer<CameraEvent> callback) {
        this.onCamera = callback;
    }

    @Override
    public void setOnClock(Consumer<ClockSyncEvent> callback) {
        this.onClock = callback;
    }

    @Override
    public void setOnContacts(Consumer<WheellyContactsMessage> callback) {
        this.onContacts = callback;
    }

    @Override
    public void setOnMotion(Consumer<WheellyMotionMessage> callback) {
        this.onMotion = callback;
    }

    @Override
    public void setOnProxy(Consumer<WheellyProxyMessage> callback) {
        this.onProxy = callback;
    }

    @Override
    public void setOnReadLine(Consumer<String> onReadLine) {
        this.onReadLine = onReadLine;
    }

    @Override
    public void setOnSupply(Consumer<WheellySupplyMessage> callback) {
        this.onSupply = callback;
    }

    @Override
    public void setOnWriteLine(Consumer<String> onWriteLine) {
        this.onWriteLine = onWriteLine;
    }

    @Override
    public long simulationTime() {
        return simulationTime;
    }

    /**
     * Synchronizes the local clock with the remote clock
     */
    private void sync() throws IOException {
        flush();
        long t0 = simulationTime;
        writeCommand("ck " + t0);
        long time = System.currentTimeMillis();
        long timeout = time + configureTimeout;
        // Repeat until interval timeout
        while (time < timeout) {
            time = System.currentTimeMillis();
            // Read the robot status
            Timed<String> line = readRobotLine();
            if (line != null) {
                if (line.value().startsWith("ck " + t0 + " ")) {
                    try {
                        ClockSyncEvent clockEvent = ClockSyncEvent.from(line.value(), line.time());
                        if (t0 == clockEvent.originateTimestamp()) {
                            if (onClock != null) {
                                onClock.accept(clockEvent);
                            }
                            clockConverter = clockEvent.converter();
                            return;
                        }
                    } catch (Throwable error) {
                        logger.atError().setCause(error).log();
                    }
                } else {
                    parseForWheellyMessage(line);
                }
            }
        }
        throw new InterruptedIOException("Timeout on sync");
    }

    @Override
    public void tick(long dt) throws IOException {
        long t0 = System.currentTimeMillis();
        long timeout = t0 + dt;
        long t = t0;
        // Repeat until interval timeout
        while (t < timeout) {
            // Read the robot status
            Timed<String> line = readRobotLine();
            if (line != null) {
                parseForWheellyMessage(line);
            }
            line = readCameraLine();
            if (line != null) {
                parseForCameraMessage(line);
            }
            t = System.currentTimeMillis();
        }
        simulationTime += t - t0;
    }

    /**
     * Writes a command to robot
     * Notifies the line if onWriteLine call back has been registered
     *
     * @param cmd the command
     */
    private void writeCommand(String cmd) throws IOException {
        if (onWriteLine != null) {
            onWriteLine.accept(cmd);
        }
        robotSocket.writeCommand(cmd);
    }
}
