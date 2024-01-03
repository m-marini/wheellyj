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
    public static final int DEFAULT_PORT = 22;
    public static final int FLUSH_INTERVAL = 1000;
    private static final Logger logger = LoggerFactory.getLogger(Robot.class);

    /**
     * Returns the robot from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of robot spec
     */
    public static Robot create(JsonNode root, Locator locator) {
        String host = locator.path("host").getNode(root).asText();
        int port = locator.path("port").getNode(root).asInt(DEFAULT_PORT);
        long connectionTimeout = locator.path("connectionTimeout").getNode(root).asLong();
        long readTimeout = locator.path("readTimeout").getNode(root).asLong();
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong();

        Locator configCommandsLoc = locator.path("configCommands");
        String[] configCommands = !configCommandsLoc.getNode(root).isMissingNode()
                ? configCommandsLoc.elements(root).map(l -> l.getNode(root).asText()).toArray(String[]::new)
                : new String[0];
        return Robot.create(host, port,
                connectionTimeout, readTimeout, configureTimeout,
                configCommands);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost         the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configureTimeout  the configuration timeout (ms)
     * @param configCommands    the configuration commands
     */
    public static Robot create(String robotHost, int port,
                               long connectionTimeout, long readTimeout, long configureTimeout,
                               String... configCommands) {
        return new Robot(robotHost, port,
                connectionTimeout, readTimeout, configureTimeout,
                configCommands);
    }

    private final String[] configCommands;
    private final long configureTimeout;
    private final RobotSocket socket;
    private Consumer<String> onReadLine;
    private Consumer<String> onWriteLine;
    private Consumer<WheellyMotionMessage> onMotion;
    private Consumer<WheellyProxyMessage> onProxy;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellySupplyMessage> onSupply;
    private Consumer<ClockSyncEvent> onClock;
    private ClockConverter clockConverter;
    private long simulationTime;

    /**
     * Create a Robot interface
     *
     * @param host              the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configureTimeout  the configuration timeout (ms)
     * @param configCommands    the motor theta corrections
     */
    public Robot(String host, int port, long connectionTimeout, long readTimeout, long configureTimeout, String[] configCommands) {
        this.configureTimeout = configureTimeout;
        socket = new RobotSocket(host, port, connectionTimeout, readTimeout);
        this.configCommands = requireNonNull(configCommands);
        this.clockConverter = ClockConverter.identity();
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        try {
            socket.close();
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
            Timed<String> line = readLine();
            if (line != null) {
                if (line.value().equals("// " + command)) {
                    return;
                }
                parseForMessage(line);
            }
        }
        throw new InterruptedIOException(format("Timeout on configure %s", command));
    }

    @Override
    public void connect() throws IOException {
        socket.connect();
    }

    /**
     * Flushes the message for interval
     */
    private void flush() throws IOException {
        long timeout = System.currentTimeMillis() + FLUSH_INTERVAL;
        while (System.currentTimeMillis() <= timeout) {
            Timed<String> line = readLine();
            parseForMessage(line);
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
    public void move(int dir, int speed) throws IOException {
        writeCommand(format(Locale.ENGLISH, "mv %d %d", dir, speed));
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with received message.
     *
     * @param line the data line received
     */
    private void parseForMessage(Timed<String> line) {
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
     * Returns the line read from roboto connection
     * Notifies the line if onReadLine call back has been registered
     *
     * @throws IOException in case of error
     */
    private Timed<String> readLine() throws IOException {
        Timed<String> line = socket.readLine();
        logger.atDebug().setMessage("Read {}").addArgument(line).log();
        if (line != null) {
            if (onReadLine != null) {
                onReadLine.accept(line.value());
            }
        }
        return line;
    }

    @Override
    public void scan(int dir) throws IOException {
        writeCommand("sc " + dir);
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
            Timed<String> line = readLine();
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
                    parseForMessage(line);
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
            Timed<String> line = readLine();
            if (line != null) {
                parseForMessage(line);
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
        socket.writeCommand(cmd);
    }
}
