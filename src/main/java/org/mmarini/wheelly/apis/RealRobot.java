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
 *//*
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
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.rx.RXFunc;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
public class RealRobot implements RobotApi {
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
        long connectionRetryInterval = locator.path("connectionRetryInterval").getNode(root).asLong();
        long readTimeout = locator.path("readTimeout").getNode(root).asLong();
        long configureTimeout = locator.path("configureTimeout").getNode(root).asLong();
        long watchDogInterval = locator.path("watchDogInterval").getNode(root).asLong();
        long watchDogTimeout = locator.path("watchDogTimeout").getNode(root).asLong();

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
                connectionRetryInterval, readTimeout, configureTimeout,
                watchDogInterval, watchDogTimeout, configCommands);
    }

    /**
     * Returns an interface to the robot
     *
     * @param robotSpec               the robot specification
     * @param robotHost               the robot host
     * @param robotPort               the robot port
     * @param cameraHost              the camera host
     * @param cameraPort              the camera port
     * @param connectionRetryInterval the connection timeout (ms)
     * @param readTimeout             the read timeout (ms)
     * @param configureTimeout        the configuration timeout (ms)
     * @param watchDogInterval        the watch dog interval (ms)
     * @param watchDogTimeout         the watch dog timeout (ms)
     * @param configCommands          the configuration commands
     */
    public static RealRobot create(RobotSpec robotSpec, String robotHost, int robotPort,
                                   String cameraHost, int cameraPort,
                                   long connectionRetryInterval, long readTimeout, long configureTimeout,
                                   long watchDogInterval, long watchDogTimeout, String... configCommands) {
        return new RealRobot(() -> new LineSocket(robotHost, robotPort, connectionRetryInterval, readTimeout),
                () -> new LineSocket(cameraHost, cameraPort, connectionRetryInterval, readTimeout),
                robotSpec, configureTimeout, watchDogInterval, watchDogTimeout, configCommands);
    }

    private final Supplier<LineSocket> robotSocketProvider;
    private final Supplier<LineSocket> cameraSocketProvider;
    private final RobotSpec robotSpec;
    private final long configureTimeout;
    private final long watchDogInterval;
    private final long watchDogTimeout;
    private final String[] configCommands;
    private final PublishProcessor<CameraEvent> cameraEvents;
    private final PublishProcessor<WheellyMessage> messages;
    private final PublishProcessor<String> writeLines;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<String> readLines;
    private final BehaviorProcessor<RobotStatusApi> states;
    private final AtomicReference<RealRobotStatus> status;

    /**
     * Creates the real robot
     *
     * @param robotSocketProvider  the robot socket provider
     * @param cameraSocketProvider the camera socket provider
     * @param robotSpec            the robot specification
     * @param configureTimeout     the configuration timeout (ms)
     * @param watchDogInterval     the watch dog interval (ms)
     * @param watchDogTimeout      the watch dog timeout (ms)
     * @param configCommands       the configuration commands
     */
    public RealRobot(Supplier<LineSocket> robotSocketProvider, Supplier<LineSocket> cameraSocketProvider, RobotSpec robotSpec, long configureTimeout, long watchDogInterval, long watchDogTimeout, String[] configCommands) {
        this.robotSocketProvider = requireNonNull(robotSocketProvider);
        this.cameraSocketProvider = requireNonNull(cameraSocketProvider);
        this.robotSpec = requireNonNull(robotSpec);
        this.configureTimeout = configureTimeout;
        this.watchDogInterval = watchDogInterval;
        this.watchDogTimeout = watchDogTimeout;
        this.configCommands = configCommands;
        this.messages = PublishProcessor.create();
        this.cameraEvents = PublishProcessor.create();
        this.writeLines = PublishProcessor.create();
        this.errors = PublishProcessor.create();
        this.readLines = PublishProcessor.create();
        this.states = BehaviorProcessor.create();
        this.status = new AtomicReference<>(new RealRobotStatus(
                false, false, false, false, false, true,
                0, 0,
                null, null, ClockConverter.identity(), null));
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        RealRobotStatus s1 = status.get();
        LineSocket robotSocket = s1.robotSocket();
        robotSocket.close();
        LineSocket cameraSocket = s1.cameraSocket();
        cameraSocket.close();
        robotSocket.waitForClose().andThen(
                        cameraSocket.waitForClose())
                .blockingAwait();
        errors.onComplete();
        readLines.onComplete();
        writeLines.onComplete();
        messages.onComplete();
        cameraEvents.onComplete();
        RealRobotStatus st = status.updateAndGet(s -> s.cameraSocket(null).robotSocket(null));
        states.onNext(st);
        states.onComplete();
    }

    /**
     * Configures robot
     */
    private void configure() {
        RealRobotStatus s0 = status.get();
        RealRobotStatus st = status.updateAndGet(RealRobotStatus::setConfiguring);
        if (!s0.configured()) {
            // if not configured
            states.onNext(st);
            // Send configure commands
            List<Single<Boolean>> list = Stream.of(configCommands)
                    .map(this::configure)
                    .toList();
            // Send all command the result is true if success
            Single<Boolean> allConfigCmd = Single.concat(list)
                    .filter(configured -> !configured)
                    .doOnNext(configured -> logger.atDebug().log("Not configured={}", configured))
                    .first(true)
                    .doOnSuccess(configured -> logger.atDebug().log("All configuration command result={}", configured));
            // Wait for synchronisation
            sync()
                    .flatMap(sync ->
                            sync
                            ? allConfigCmd
                            : Single.just(false))
                    .doOnSuccess(replied -> logger.atDebug().log("Configuration={}", replied))
                    .subscribe(this::onConfiguration);
        }
    }

    /**
     * Sends the configuration command and wait for confirmation for a maximum of configured timeout
     *
     * @param command the configuration command
     */
    private Single<Boolean> configure(String command) {
        RealRobotStatus s1 = status.get();
        LineSocket robotSocket = s1.robotSocket();
        return robotSocket.waitForConnected()
                .flatMap(ignored -> {
                    String reply = "// " + command;
                    writeCommand(command);
                    return RXFunc.findFirst(robotSocket.readLines(),
                                    l -> l.value().equals(reply),
                                    configureTimeout)
                            .doOnSuccess(replied -> logger.atDebug().log("Reply to {}={}", command, replied.value()))
                            .map(x -> true)
                            .defaultIfEmpty(false)
                            .doOnSuccess(replied -> logger.atDebug().log("Configure={} command={}", replied, command));
                });
    }

    @Override
    public void connect() {
        RealRobotStatus s = status.getAndUpdate(s1 -> s1.started(true));
        if (!s.started()) {
            createSockets();
        }
    }

    /**
     * Creates the sockets
     */
    private void createSockets() {
        logger.atDebug().log("Creating sockets ...");
        RealRobotStatus s = status.updateAndGet(s1 -> s1.startTime(System.currentTimeMillis())
                .robotSocket(robotSocketProvider.get())
                .cameraSocket(cameraSocketProvider.get())
                .setConnecting());
        states.onNext(s);
        LineSocket socket = s.robotSocket();
        socket.readLines()
                .subscribe(this::onRobotLine);
        socket.readError()
                .subscribe(this::onError);
        socket.readStatus()
                .distinctUntilChanged(s1 -> s1.connected())
                .subscribe(this::onRobotStatus);
        LineSocket socket1 = s.cameraSocket();
        socket1.readLines()
                .subscribe(this::onCameraLine);
        socket1.readError()
                .subscribe(this::onError);
        socket.connect();
        socket1.connect();
    }

    /**
     * Creates the watch dog
     */
    private void createWatchDog() {
        // Create a new watch dog
        WatchDog watchDog = new WatchDog(this::safetyCheck, watchDogInterval, watchDogTimeout);
        RealRobotStatus st = status.updateAndGet(s ->
                s.watchDog() == null
                        ? s.watchDog(watchDog)
                        : s);
        // Send new state
        states.onNext(st);
        // Check for the watch dog changed
        if (Objects.equals(watchDog, st.watchDog())) {
            // Run the watch dog
            watchDog.readAlarm()
                    .subscribe(this::onWatchDogAlarm);
        }
    }

    /**
     * Handles the configuration event
     *
     * @param configured true if configured
     */
    private void onConfiguration(boolean configured) {
        logger.atDebug().log("Configured={}", configured);
        if (configured) {
            RealRobotStatus st = status.updateAndGet(RealRobotStatus::setConfigured);
            states.onNext(st);
        } else {
            configure();
        }
    }

    /**
     * Handles robot error
     *
     * @param throwable the error
     */
    private void onError(Throwable throwable) {
        errors.onNext(throwable);
    }

    /**
     * Handles the robot data
     *
     * @param line the message line
     */
    private void onRobotLine(Timed<String> line) {
        RealRobotStatus st = status.updateAndGet(s -> s.lastActivity(line.time(TimeUnit.MILLISECONDS)));
        readLines.onNext(line.value());
        states.onNext(st);
        try {
            parseForWheellyMessage(line);
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing wheelly message");
            errors.onNext(ex);
        }
    }

    /**
     * Handles the roboto lines status event
     *
     * @param status the line status
     */
    private void onRobotStatus(LineSocketStatus status) {
        RealRobotStatus st = this.status.updateAndGet(s ->
                status.connected()
                        ? s.setConnected().lastActivity(System.currentTimeMillis())
                        : s.setUnconnected());
        states.onNext(st);
        if (st.connected()) {
            configure();
        }
    }

    /**
     * Handles the watch dog alarm
     *
     * @param safe false if roboto is not in safety state
     */
    private void onWatchDogAlarm(boolean safe) {
        // Clear watch dog
        RealRobotStatus st1 = status.updateAndGet(s -> s.watchDog(null));
        states.onNext(st1);
        // if robot in safe state does nothing
        if (!safe) {
            // reconnect if robot unsafe
            logger.atDebug().log("Watch dog activated");
            reconnect();
        }
    }

    @Override
    public boolean halt() {
        return writeCommand("ha");
    }

    @Override
    public boolean isHalt() {
        return status.get().halted();
    }

    @Override
    public boolean move(int dir, int speed) {
        return writeCommand(format(Locale.ENGLISH, "mv %d %d", dir, speed));
    }

    /**
     * Handles the camera data
     *
     * @param line the camera data line
     */
    private void onCameraLine(Timed<String> line) {
        readLines.onNext(line.value());
        try {
            parseForCameraMessage(line);
        } catch (Throwable ex) {
            logger.atError().setCause(ex).log("Error parsing camera event");
            errors.onNext(ex);
        }
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with the received message.
     *
     * @param line the data line received
     * @throws IllegalArgumentException if messages cannot be parsed
     */
    private void parseForCameraMessage(Timed<String> line) {
        CameraEvent msg = CameraEvent.create(line.value());
        cameraEvents.onNext(msg);
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with the received message.
     *
     * @param line the data line received
     * @throws IllegalArgumentException if messages cannot be parsed
     */
    private void parseForWheellyMessage(Timed<String> line) {
        RealRobotStatus s = status.get();
        Optional<WheellyMessage> msgOpt = WheellyMessage.fromLine(line, s.clockConverter());
        msgOpt.ifPresent(msg -> {
            if (msg instanceof WheellyMotionMessage motionMsg) {
                RealRobotStatus st = status.updateAndGet(s1 -> s1.halted(motionMsg.halt()));
                states.onNext(st);
            }
            messages.onNext(msg);
        });
    }

    @Override
    public Flowable<RobotStatusApi> readRobotStatus() {
        return states;
    }

    @Override
    public Flowable<CameraEvent> readCamera() {
        return cameraEvents;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    @Override
    public Flowable<WheellyMessage> readMessages() {
        return messages;
    }

    @Override
    public Flowable<String> readReadLine() {
        return readLines;
    }

    @Override
    public Flowable<String> readWriteLine() {
        return writeLines;
    }

    @Override
    public void reconnect() {
        logger.atInfo().log("Reconnecting ...");
        RealRobotStatus s1 = status.getAndUpdate(s -> s.cameraSocket(null).robotSocket(null).setUnconnected());
        states.onNext(s1);
        LineSocket robotSocket = s1.robotSocket();
        robotSocket.close();
        LineSocket cameraSocket = s1.cameraSocket();
        cameraSocket.close();
        robotSocket.waitForClose().andThen(
                        cameraSocket.waitForClose())
                .subscribe(this::createSockets);
    }

    /**
     * Returns true if the robot is working correctly
     */
    private boolean safetyCheck() {
        RealRobotStatus st = status.get();
        return !(st.connected() && System.currentTimeMillis() >= st.lastActivity() + watchDogTimeout);
    }

    @Override
    public RobotSpec robotSpec() {
        return robotSpec;
    }

    @Override
    public boolean scan(int dir) {
        return writeCommand("sc " + dir);
    }

    @Override
    public double simulationSpeed() {
        return 1;
    }

    @Override
    public long simulationTime() {
        return System.currentTimeMillis() - status.get().startTime();
    }

    /**
     * Returns the clock synchronisation process (true if success)
     */
    private Single<Boolean> sync() {
        LineSocket robotSocket = status.get().robotSocket();
        return robotSocket.waitForConnected()
                .flatMap(ignored -> {
                    long t0 = simulationTime();
                    writeCommand("ck " + t0);
                    return RXFunc.findFirst(robotSocket.readLines().observeOn(Schedulers.computation()),
                                    l -> l.value().startsWith("ck " + t0 + " "),
                                    configureTimeout)
                            .map(line -> {
                                ClockSyncEvent clockEvent = ClockSyncEvent.from(line.value(), line.time());
                                if (t0 == clockEvent.originateTimestamp()) {
                                    RealRobotStatus st = status.updateAndGet(s -> s.clockConverter(clockEvent.converter()));
                                    states.onNext(st);
                                }
                                return true;
                            })
                            .defaultIfEmpty(false);
                });
    }

    /**
     * Writes a command to robot returning true on success
     * Notifies the line if onWriteLine call back has been registered
     *
     * @param cmd the command
     */
    private boolean writeCommand(String cmd) {
        LineSocket robotSocket = status.get().robotSocket();
        boolean result = robotSocket.writeCommand(cmd);
        if (result) {
            writeLines.onNext(cmd);
        }
        return result;
    }
}
