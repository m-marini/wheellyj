/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.rx.RXFunc;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

public class RealRobot implements RobotApi, WithIOCallback {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/real-robot-schema-0.1";
    public static final int DEFAULT_ROBOT_PORT = 22;
    public static final int DEFAULT_CAMERA_PORT = 8100;
    private static final Logger logger = LoggerFactory.getLogger(RealRobot.class);

    /**
     * Returns the robot from configuration
     *
     * @param root the configuration document
     * @param file the configuration file
     */
    public static RealRobot create(JsonNode root, File file) {
        Locator locator = Locator.root();
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        String robotHost = locator.path("robotHost").getNode(root).asText();
        int robotPort = locator.path("robotPort").getNode(root).asInt(DEFAULT_ROBOT_PORT);
        String cameraHost = locator.path("cameraHost").getNode(root).asText();
        int cameraPort = locator.path("cameraPort").getNode(root).asInt(DEFAULT_CAMERA_PORT);
        long connectionTimeout = locator.path("connectionTimeout").getNode(root).asLong();
        long readTimeout = locator.path("readTimeout").getNode(root).asLong();
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong();

        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        int receptiveAngle = locator.path("sensorReceptiveAngle").getNode(root).asInt();
        RobotSpec robotSpec = new RobotSpec(maxRadarDistance, Complex.fromDeg(receptiveAngle), contactRadius);

        Locator configCommandsLoc = locator.path("configCommands");
        String[] configCommands = !configCommandsLoc.getNode(root).isMissingNode()
                ? configCommandsLoc.elements(root).map(l -> l.getNode(root).asText()).toArray(String[]::new)
                : new String[0];

        return create(robotSpec, robotHost, robotPort,
                cameraHost, cameraPort,
                connectionTimeout, readTimeout, configureTimeout,
                configCommands);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotSpec         the robot specification
     * @param robotHost         the robot host
     * @param robotPort         the robot port
     * @param cameraHost        the camera host
     * @param cameraPort        the camera port
     * @param connectionTimeout the connection timeout (ms)
     * @param readTimeout       the read timeout (ms)
     * @param configureTimeout  the configuration timeout (ms)
     * @param configCommands    the configuration commands
     */
    public static RealRobot create(RobotSpec robotSpec, String robotHost, int robotPort,
                                   String cameraHost, int cameraPort,
                                   long connectionTimeout, long readTimeout, long configureTimeout,
                                   String... configCommands) {
        AsyncLineSocket robotSocket1 = new AsyncLineSocket(robotHost, robotPort, connectionTimeout, readTimeout);
        AsyncLineSocket cameraSocket1 = new AsyncLineSocket(cameraHost, cameraPort, connectionTimeout, readTimeout);
        return new RealRobot(robotSpec, robotSocket1, cameraSocket1,
                configureTimeout,
                configCommands);
    }

    private final AsyncLineSocket robotSocket;
    private final AsyncLineSocket cameraSocket;
    private final RobotSpec robotSpec;
    private final long configureTimeout;
    private final String[] configCommands;
    private Consumer<WheellyProxyMessage> onProxy;
    private Consumer<CameraEvent> onCamera;
    private Consumer<ClockSyncEvent> onClock;
    private Consumer<WheellyContactsMessage> onContacts;
    private Consumer<WheellyMotionMessage> onMotion;
    private Consumer<String> onReadLine;
    private Consumer<String> onWriteLine;
    private long startTime;
    private long simulationTime;
    private Consumer<WheellySupplyMessage> onSupply;
    private ClockConverter clockConverter;
    private boolean halted;

    /**
     * Creates the real robot
     *
     * @param robotSpec        the robot specification
     * @param robotSocket      the robot socket
     * @param cameraSocket     the camera socket
     * @param configureTimeout the configuration timeout (ms)
     * @param configCommands   the configuration commands
     */
    public RealRobot(RobotSpec robotSpec, AsyncLineSocket robotSocket, AsyncLineSocket cameraSocket, long configureTimeout, String[] configCommands) {
        this.robotSocket = requireNonNull(robotSocket);
        this.cameraSocket = requireNonNull(cameraSocket);
        this.robotSpec = requireNonNull(robotSpec);
        this.configureTimeout = configureTimeout;
        this.configCommands = configCommands;
        this.clockConverter = ClockConverter.identity();
        this.halted = true;

        robotSocket.readLines()
                .observeOn(Schedulers.io())
                .doOnNext(this::onRobotLine)
                .subscribe();
        cameraSocket.readLines()
                .observeOn(Schedulers.io())
                .doOnNext(this::onCameraLine)
                .subscribe();
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
        robotSocket.readClose()
                .concatWith(cameraSocket.readClose())
                .blockingAwait();
    }

    /**
     * Sends the configuration command and wait for confirmation for a maximum of configured timeout
     *
     * @param command the configuration command
     * @throws IOException in case of error
     */
    private void configure(String command) throws IOException {
        String reply = "// " + command;
        writeCommand(command);
        Timed<String> line = RXFunc.findFirst(robotSocket.readLines().observeOn(Schedulers.io()),
                        l -> l.value().equals(reply),
                        configureTimeout)
                .blockingGet();
        if (line == null) {
            throw new InterruptedIOException(format("Timeout on configure %s", command));
        }
    }

    @Override
    public void configure() throws IOException {
        sync();
        for (String cmd : configCommands) {
            configure(cmd);
        }
    }

    @Override
    public void connect() {
        startTime = System.currentTimeMillis();
        robotSocket.connect();
        cameraSocket.connect();
    }

    @Override
    public void halt() throws IOException {
        writeCommand("ha");
    }

    @Override
    public boolean isHalt() {
        return halted;
    }

    @Override
    public void move(Complex dir, int speed) throws IOException {
        writeCommand(format(Locale.ENGLISH, "mv %d %d", dir.toIntDeg(), speed));
    }

    /**
     * Handles the camera data
     *
     * @param line the camera data line
     */
    private void onCameraLine(Timed<String> line) {
        if (onReadLine != null) {
            onReadLine.accept(line.value());
        }
        parseForCameraMessage(line);
    }

    /**
     * Handles the robot data
     *
     * @param line the message line
     */
    private void onRobotLine(Timed<String> line) {
        if (onReadLine != null) {
            onReadLine.accept(line.value());
        }
        parseForWheellyMessage(line);
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with the received message.
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
     * The robot status is updated with the received message.
     *
     * @param line the data line received
     */
    private void parseForWheellyMessage(Timed<String> line) {
        WheellyMessage.fromLine(line, clockConverter).ifPresent(msg -> {
            switch (msg) {
                case WheellyMotionMessage motionMsg -> {
                    if (onMotion != null) {
                        onMotion.accept(motionMsg);
                    }
                    this.halted = motionMsg.halt();
                }
                case WheellyContactsMessage contactsMsg -> {
                    if (onContacts != null) {
                        onContacts.accept(contactsMsg);
                    }
                }
                case WheellyProxyMessage proxyMsg -> {
                    if (onProxy != null) {
                        onProxy.accept(proxyMsg);
                    }
                }
                case WheellySupplyMessage supplyMsg -> {
                    if (onSupply != null) {
                        onSupply.accept(supplyMsg);
                    }
                }
                default -> {
                }
            }
        });
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
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
        long t0 = simulationTime;
        writeCommand("ck " + t0);
        Timed<String> line = RXFunc.findFirst(robotSocket.readLines().observeOn(Schedulers.computation()),
                        l -> l.value().startsWith("ck " + t0 + " "),
                        configureTimeout)
                .blockingGet();
        if (line == null) {
            throw new InterruptedIOException("Timeout on sync");
        }
        ClockSyncEvent clockEvent = ClockSyncEvent.from(line.value(), line.time());
        if (t0 == clockEvent.originateTimestamp()) {
            if (onClock != null) {
                onClock.accept(clockEvent);
            }
            clockConverter = clockEvent.converter();
        }
    }

    @Override
    public void tick(long dt) throws IOException {
        try {
            Thread.sleep(dt);
        } catch (InterruptedException e) {
            logger.atError().setCause(e).log("Process interrupted");
        }
        simulationTime = System.currentTimeMillis() - startTime;
    }

    /**
     * Writes a command to robot
     * Notifies the line if onWriteLine call back has been registered
     *
     * @param cmd the command
     */
    private void writeCommand(String cmd) {
        robotSocket.readConnected()
                .observeOn(Schedulers.io())
                .map(result -> {
                    if (onWriteLine != null) {
                        onWriteLine.accept(cmd);
                    }
                    robotSocket.writeCommand(cmd);
                    return cmd;
                })
                .blockingGet();
    }
}
