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
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apps.Yaml.loadDoubleArray;
import static org.mmarini.wheelly.apps.Yaml.loadIntArray;

/**
 * Implements the Robot interface to the real robot
 */
public class Robot implements RobotApi, WithIOCallback {
    public static final int DEFAULT_PORT = 22;
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
        int[] frontThresholds = loadIntArray(root, locator.path("frontThresholds"));
        int[] rearThresholds = loadIntArray(root, locator.path("frontThresholds"));
        int[] supplyValues = loadIntArray(root, locator.path("supplyValues"));
        double[] voltages = loadDoubleArray(root, locator.path("voltages"));

        Locator configCommandsLoc = locator.path("configCommands");
        String[] configCommands = !configCommandsLoc.getNode(root).isMissingNode()
                ? configCommandsLoc.elements(root).map(l -> l.getNode(root).asText()).toArray(String[]::new)
                : new String[0];
        return Robot.create(host, port,
                connectionTimeout, readTimeout, configureTimeout,
                frontThresholds, rearThresholds,
                supplyValues, voltages, configCommands);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotHost         the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configureTimeout  the configuration timeout (ms)
     * @param frontThresholds   the front thresholds
     * @param rearThresholds    the rear thresholds
     * @param supplyValues      the supply values
     * @param voltages          the voltage values
     * @param configCommands    the configuration commands
     */
    public static Robot create(String robotHost, int port,
                               long connectionTimeout, long readTimeout, long configureTimeout,
                               int[] frontThresholds, int[] rearThresholds,
                               int[] supplyValues, double[] voltages, String... configCommands) {
        requireNonNull(supplyValues);
        requireNonNull(voltages);
        if (!(supplyValues.length == 2)) {
            throw new IllegalArgumentException(format("supplyValues must have 2 items (%d)", supplyValues.length));
        }
        if (!(voltages.length == 2)) {
            throw new IllegalArgumentException(format("voltages must have 2 items (%d)", voltages.length));
        }
        IntToDoubleFunction decodeVoltage = x -> linear(x, supplyValues[0], supplyValues[1], voltages[0], voltages[1]);
        return new Robot(robotHost, port,
                connectionTimeout, readTimeout, configureTimeout,
                frontThresholds, rearThresholds,
                configCommands, RobotStatus.create(decodeVoltage));
    }

    /**
     * Returns the contacts code from the contact sensors signals
     * <p>
     * Return value are:
     * <ul>
     *     <li>0 - no contact</li>
     *     <li>1 - right contact</li>
     *     <li>2 - left contact</li>
     *     <li>3 - both side contacts</li>
     * </ul>
     * </p>
     *
     * @param signal the signal
     */
    private static int decodeContacts(int signal, int[] thresholds) {
        for (int i = 0; i < thresholds.length; i++) {
            if (signal < thresholds[i]) {
                return i;
            }
        }
        return thresholds.length;
    }

    private final String[] configCommands;
    private final long configureTimeout;
    private final int[] frontThresholds;
    private final int[] rearThresholds;
    private final RobotSocket socket;
    private RobotStatus status;
    private Consumer<String> onReadLine;
    private Consumer<String> onWriteLine;
    private Consumer<RobotStatus> onStatusReady;

    /**
     * Create a Robot interface
     *
     * @param host              the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configureTimeout  the configuration timeout (ms)
     * @param frontThresholds   the front thresholds
     * @param rearThresholds    the rear thresholds
     * @param configCommands    the motor theta corrections
     * @param status            the initial status
     */
    public Robot(String host, int port, long connectionTimeout, long readTimeout, long configureTimeout, int[] frontThresholds, int[] rearThresholds, String[] configCommands, RobotStatus status) {
        this.configureTimeout = configureTimeout;
        socket = new RobotSocket(host, port, connectionTimeout, readTimeout);
        this.status = status;
        this.frontThresholds = requireNonNull(frontThresholds);
        this.rearThresholds = requireNonNull(rearThresholds);
        this.configCommands = requireNonNull(configCommands);
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
                parseForStatus(line);
                if (line.value().equals("// " + command)) {
                    return;
                }
            }
        }
        throw new InterruptedIOException(format("Timeout on configure %s", command));
    }

    @Override
    public void connect() throws IOException {
        socket.connect();
    }

    /**
     * Returns the contacts code from the contact sensors signals
     *
     * @param frontSignal the front signal
     * @param rearSignal  the rear signal
     */
    private int decodeContacts(int frontSignal, int rearSignal) {
        return decodeContacts(frontSignal, frontThresholds) * 4 + decodeContacts(rearSignal, rearThresholds);
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
     * Returns the robot status updated with received data or null if no data available
     *
     * @param line the data line received
     */
    private void parseForStatus(Timed<String> line) {
        if (line.value().startsWith("st ")) {
            try {
                WheellyStatus wheellyStatus = WheellyStatus.create(line);
                int contacts = decodeContacts(wheellyStatus.getFrontSensors(), wheellyStatus.getRearSensors());
                status = status.setWheellyStatus(wheellyStatus).setContacts(contacts);
                if (onStatusReady != null) {
                    onStatusReady.accept(status);
                }
            } catch (IllegalArgumentException ex) {
                logger.atError().setCause(ex).log();
            }
        }
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
    public void reset() {
        long time = System.currentTimeMillis();
        status = status.setTime(time).setResetTime(time);
    }

    @Override
    public void scan(int dir) throws IOException {
        writeCommand("sc " + dir);
    }

    @Override
    public void setOnReadLine(Consumer<String> onReadLine) {
        this.onReadLine = onReadLine;
    }

    @Override
    public void setOnStatusReady(Consumer<RobotStatus> callback) {
        this.onStatusReady = callback;
    }

    @Override
    public void setOnWriteLine(Consumer<String> onWriteLine) {
        this.onWriteLine = onWriteLine;
    }

    /**
     * Synchronizes the local clock with the remote clock
     */
    private void sync() throws IOException {
        long now = System.currentTimeMillis();
        writeCommand("ck " + now);
        long time = System.currentTimeMillis();
        long timeout = time + configureTimeout;
        // Repeat until interval timeout
        while (time < timeout) {
            time = System.currentTimeMillis();
            // Read the robot status
            Timed<String> line = readLine();
            if (line != null) {
                parseForStatus(line);
                if (line.value().startsWith("ck " + now + " ")) {
                    try {
                        ClockSyncEvent clock = ClockSyncEvent.from(line.value(), line.time(TimeUnit.MILLISECONDS));
                        if (now == clock.getOriginateTimestamp()) {
                            clock.getRemoteOffset();
                            return;
                        }
                    } catch (Throwable error) {
                        logger.atError().setCause(error).log();
                    }
                }
            }
        }
        throw new InterruptedIOException("Timeout on sync");
    }

    @Override
    public void tick(long dt) throws IOException {
        long time = System.currentTimeMillis();
        long timeout = time + dt;
        // Repeat until interval timeout
        while (time < timeout) {
            time = System.currentTimeMillis();
            // Read the robot status
            Timed<String> line = readLine();
            if (line != null) {
                parseForStatus(line);
            }
        }
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
