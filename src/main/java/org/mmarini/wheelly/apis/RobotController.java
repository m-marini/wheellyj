/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Stream;

import static java.lang.Math.round;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apps.AppYaml.loadDoubleArray;
import static org.mmarini.wheelly.apps.AppYaml.loadIntArray;

/**
 * Manages the processing threads and event generation to interface the robot.
 * <p>
 * The controller connects the application to the robot by initializing and configuring it.
 * It checks the connection and in case of disconnection repeats the connection and configuration sequence.
 * </p>
 * <p>
 * The controller starts at connecting state than:
 * <dl>
 *     <dt>connecting</dt>
 *     <dd>
 *         Try to connect to the robot.
 *         If connected it move to configuring state
 *         else move to waiting retry status
 *     </dd>
 *     <dt>configuring</dt>
 *     <dd>
 *         If controller is closed it move to closing state.
 *         Configures robot, starts the status thread, sets controller ready and moves to handleCommands state.
 *         In case of IO errors moves to closing state
 *     </dd>
 *     <dt>handleCommands</dt>
 *     <dd>
 *         If controller is closed it move to closing state.
 *         If no robot status has received move to waitingCommandInterval state.
 *         If robot sensor direction is different from the required or command timed out it sends the scan command
 *         If robot status has received and move command has required send the move command
 *         Moves to waitingCommandInterval state.
 *         In case of IO errors moves to closing state
 *     </dd>
 *     <dt>waitingCommandInterval</dt>
 *     <dd>
 *         If the watch dog localTime has elapsed close the controller and move to closing state.
 *         Waits for interval.
 *         Moves to handleScan state.
 *     </dd>
 *     <dt>closing</dt>
 *     <dd>
 *         Sets controller not ready.
 *         Closes the connection.
 *         Sets controller not connected and not closed.
 *         Moves to waitingRetry state.
 *     </dd>
 *     <dt>waitingRetry</dt>
 *     <dd>
 *         Wait for retry interval
 *         Moves to connecting state.
 *     </dd>
 * </dl>
 * </p>
 * <p>
 * The controller thread starts at start of controller and runs managing controller status while connected or not ended.
 * The status thread starts at configuration completion and runs ticking status while ready or not ended.
 * The inference thread starts at inference interval on status change in a single execution.
 * </p>
 */
public class RobotController implements RobotControllerApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/controller-schema-1.0";
    public static final String CONNECTING = "connecting";
    public static final String WAITING_RETRY = "waitingRetry";
    public static final String CLOSING = "closing";
    public static final String HANDLING_COMMANDS = "handlingCommands";
    public static final String CONFIGURING = "configuring";
    public static final String WAITING_COMMAND_INTERVAL = "waitingCommandInterval";
    public static final long MIN_SYNC_INTERVAL = 3;
    private static final Logger logger = LoggerFactory.getLogger(RobotController.class);

    /**
     * Returns the robot controller from configuration
     *
     * @param root the configuration document
     * @param file the configuration file
     */
    public static RobotController create(JsonNode root, File file) {
        Locator locator = Locator.root();
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long interval = locator.path("interval").getNode(root).asLong();
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong();
        long commandInterval = locator.path("commandInterval").getNode(root).asLong();
        long connectionRetryInterval = locator.path("connectionRetryInterval").getNode(root).asLong();
        long watchdogInterval = locator.path("watchdogInterval").getNode(root).asLong();
        double simSpeed = locator.path("simulationSpeed").getNode(root).asDouble(1);
        int[] supplyValues = loadIntArray(root, locator.path("supplyValues"));
        double[] voltages = loadDoubleArray(root, locator.path("voltages"));
        if (!(supplyValues.length == 2)) {
            throw new IllegalArgumentException(format("supplyValues must have 2 items (%d)", supplyValues.length));
        }
        if (!(voltages.length == 2)) {
            throw new IllegalArgumentException(format("voltages must have 2 items (%d)", voltages.length));
        }
        IntToDoubleFunction decodeVoltage = x -> linear(x, supplyValues[0], supplyValues[1], voltages[0], voltages[1]);
        return new RobotController(interval, reactionInterval, commandInterval, connectionRetryInterval, watchdogInterval, simSpeed, decodeVoltage);
    }

    private final long commandInterval;
    private final long connectionRetryInterval;
    private final long interval;
    private final long reactionInterval;
    private final long watchdogInterval;
    private final double simSpeed;
    private final PublishProcessor<RobotCommands> commandsProcessor;
    private final PublishProcessor<RobotStatus> contactsProcessor;
    private final PublishProcessor<String> controllerStatusProcessor;
    private final PublishProcessor<Throwable> errorsProcessor;
    private final PublishProcessor<RobotStatus> inferencesProcessor;
    private final PublishProcessor<RobotStatus> motionProcessor;
    private final PublishProcessor<RobotStatus> proxyProcessor;
    private final PublishProcessor<String> readLinesProcessor;
    private final CompletableSubject shutdownCompletable;
    private final Flowable<RobotStatus> statusFlow;
    private final PublishProcessor<RobotStatus> supplyProcessor;
    private final PublishProcessor<String> writeLinesProcessor;
    private final PublishProcessor<RobotStatus> cameraProcessor;
    private final IntToDoubleFunction decodeVoltage;
    private RobotApi robot;
    private boolean close;
    private boolean connected;
    private boolean end;
    private boolean isInference;
    private boolean isReady;
    private boolean isStarted;
    private long lastInference;
    private long lastRobotMoveTimestamp;
    private long lastSensorMoveTimestamp;
    private long lastTick;
    private double simRealSpeed;
    private boolean isRunningStatus;
    private RobotCommands lastMoveCommand;
    private RobotCommands moveCommand;
    private Consumer<RobotStatus> onInference;
    private Consumer<RobotStatus> onLatch;
    private Complex prevSensorDir;
    private RobotStatus robotStatus;
    private Complex sensorDir;
    private Runnable statusTransition;

    /**
     * Creates the robot controller
     *
     * @param interval                the tick interval (ms)
     * @param reactionInterval        the minimum reaction interval of inference engine (ms)
     * @param commandInterval         the interval between command send (ms)
     * @param connectionRetryInterval the connection retry interval (ms)
     * @param watchdogInterval        the watch dog interval (ms)
     * @param simSpeed                the simulation speed
     * @param decodeVoltage           the decode voltage function
     */
    public RobotController(long interval, long reactionInterval, long commandInterval, long connectionRetryInterval,
                           long watchdogInterval, double simSpeed, IntToDoubleFunction decodeVoltage) {
        this.interval = interval;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.connectionRetryInterval = connectionRetryInterval;
        this.watchdogInterval = watchdogInterval;
        this.simSpeed = simSpeed;
        this.simRealSpeed = simSpeed;
        this.end = false;
        this.prevSensorDir = Complex.DEG0;
        this.sensorDir = Complex.DEG0;
        this.motionProcessor = PublishProcessor.create();
        this.contactsProcessor = PublishProcessor.create();
        this.proxyProcessor = PublishProcessor.create();
        this.cameraProcessor = PublishProcessor.create();
        this.supplyProcessor = PublishProcessor.create();
        this.controllerStatusProcessor = PublishProcessor.create();
        this.commandsProcessor = PublishProcessor.create();
        this.errorsProcessor = PublishProcessor.create();
        this.readLinesProcessor = PublishProcessor.create();
        this.writeLinesProcessor = PublishProcessor.create();
        this.inferencesProcessor = PublishProcessor.create();
        this.shutdownCompletable = CompletableSubject.create();
        this.statusFlow = Flowable.merge(readMotion(), readContacts(), readProxy(), readSupply());
        this.decodeVoltage = requireNonNull(decodeVoltage);
        setStatusTransition(this::connecting, CONNECTING);
    }

    @Override
    public RobotController connectRobot(RobotApi robot) {
        this.robot = requireNonNull(robot);
        robot.setOnMotion(this::handleMotion);
        robot.setOnProxy(this::handleProxy);
        robot.setOnContacts(this::handleContacts);
        robot.setOnSupply(this::handleSupply);
        robot.setOnCamera(this::handleCamera);
        this.robotStatus = RobotStatus.create(robot.robotSpec(), decodeVoltage);
        return this;
    }

    /**
     * Handles the closing status.
     * Closes the connection to the robot
     */
    private void closing() {
        logger.atDebug().setMessage("Closing").log();
        isReady = false;
        try {
            robot.close();
        } catch (IOException ex) {
            sendError(ex);
        }
        connected = false;
        close = false;
        setStatusTransition(this::waitingRetry, WAITING_RETRY);
    }

    /**
     * Handles the configuring status.
     * Configures the robot.
     */
    void configuring() {
        logger.atDebug().setMessage("Configuring").log();
        if (close) {
            setStatusTransition(this::closing, CLOSING);
        }
        try {
            robot.configure();
            isReady = true;
            lastTick = robot.simulationTime();
            Schedulers.io().scheduleDirect(this::runStatusThread);
            setStatusTransition(this::handleCommands, HANDLING_COMMANDS);
        } catch (Exception ex) {
            sendError(ex);
            setStatusTransition(this::closing, CLOSING);
        }
    }

    /**
     * Handles connecting status.
     * Connects to the robot
     */
    void connecting() {
        logger.atDebug().setMessage("Connecting").log();
        try {
            robot.connect();
            setStatusTransition(this::configuring, CONFIGURING);
            connected = true;
        } catch (Exception ex) {
            sendError(ex);
            setStatusTransition(this::waitingRetry, WAITING_RETRY);
        }
    }

    @Override
    public void execute(RobotCommands command) {
        // Validates the command
        if (command.halt() || command.move()) {
            if (command.move()
                    && !(command.speed() >= -MAX_PPS && command.speed() <= MAX_PPS)) {
                logger.atError().setMessage("Wrong move command {}").addArgument(command).log();
            } else {
                moveCommand = command.clearScan();
            }
        }
        if (command.scan()) {
            if (command.scanDirection().y() >= 0) {
                sensorDir = command.scanDirection();
            } else {
                logger.atError().log("Wrong scan direction {}", command.scanDirection());
            }
        }
        commandsProcessor.onNext(command);
    }

    /**
     * Handles camera event
     *
     * @param cameraEvent the camera event
     */
    private void handleCamera(CameraEvent cameraEvent) {
        robotStatus = robotStatus.setCameraMessage(cameraEvent)
                .setSimulationTime(robot.simulationTime());
        cameraProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    /**
     * Handles commands
     */
    void handleCommands() {
        if (close) {
            setStatusTransition(this::closing, CLOSING);
            return;
        }
        RobotStatus status = robotStatus;
        if (status != null) {
            long time = robot.simulationTime();
            Complex dir = sensorDir;
            try {
                logger.atDebug().log("Scan dir={}, prevSensorDir={}", dir, prevSensorDir);
                // Checks for scan command required
                if (!dir.equals(this.prevSensorDir)
                        || !dir.equals(Complex.DEG0) && time >= lastSensorMoveTimestamp + commandInterval) {
                    logger.atDebug().log("Scan {}", dir);
                    robot.scan(dir);
                    lastSensorMoveTimestamp = time;
                    prevSensorDir = dir;
                }
                // Checks for move command required
                RobotCommands cmd = this.moveCommand;
                if (cmd != null) {
                    // Checks for move command required
                    if (!cmd.equals(lastMoveCommand)
                            || !cmd.halt() && (time >= lastRobotMoveTimestamp + commandInterval) || robot.isHalt()) {
                        if (cmd.halt()) {
                            robot.halt();
                        } else {
                            robot.move(cmd.moveDirection(), cmd.speed());
                        }
                        this.lastRobotMoveTimestamp = time;
                        lastMoveCommand = cmd;
                    }
                }
            } catch (Exception ex) {
                sendError(ex);
                setStatusTransition(this::closing, CLOSING);
                return;
            }
            setStatusTransition(this::waitingCommandInterval, WAITING_COMMAND_INTERVAL);
        }
    }

    /**
     * Handles contacts event
     *
     * @param msg contacts message
     */
    private void handleContacts(WheellyContactsMessage msg) {
        robotStatus = robotStatus.setContactsMessage(msg).setSimulationTime(robot.simulationTime());
        contactsProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    /**
     * Handles movement event
     *
     * @param msg the motion message
     */
    private void handleMotion(WheellyMotionMessage msg) {
        logger.atDebug().log("handleMotion");
        robotStatus = robotStatus.setMotionMessage(msg).setSimulationTime(robot.simulationTime());
        motionProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    /**
     * Handles proxy event
     *
     * @param msg the proxy message
     */
    private void handleProxy(WheellyProxyMessage msg) {
        robotStatus = robotStatus.setProxyMessage(msg).setSimulationTime(robot.simulationTime());
        proxyProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    /**
     * Handles supply event
     *
     * @param msg the robot status
     */
    private void handleSupply(WheellySupplyMessage msg) {
        robotStatus = robotStatus.setSupplyMessage(msg).setSimulationTime(robot.simulationTime());
        supplyProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    @Override
    public Flowable<RobotStatus> readCamera() {
        return cameraProcessor;
    }

    @Override
    public Flowable<RobotCommands> readCommand() {
        return commandsProcessor;
    }

    @Override
    public Flowable<RobotStatus> readContacts() {
        return contactsProcessor;
    }

    @Override
    public Flowable<String> readControllerStatus() {
        return controllerStatusProcessor;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return errorsProcessor;
    }

    @Override
    public Flowable<RobotStatus> readInference() {
        return inferencesProcessor;
    }

    @Override
    public Flowable<RobotStatus> readMotion() {
        return motionProcessor;
    }

    @Override
    public Flowable<RobotStatus> readProxy() {
        return proxyProcessor;
    }

    @Override
    public Flowable<String> readReadLine() {
        return readLinesProcessor;
    }

    @Override
    public Flowable<RobotStatus> readRobotStatus() {
        return statusFlow;
    }

    @Override
    public Completable readShutdown() {
        return shutdownCompletable;
    }

    @Override
    public Flowable<RobotStatus> readSupply() {
        return supplyProcessor;
    }

    @Override
    public Flowable<String> readWriteLine() {
        return writeLinesProcessor;
    }

    /**
     * Manages the controller performing the control process
     */
    private void runControlProcess() {
        logger.atInfo().setMessage("Control process started").log();
        if (robot instanceof WithIOCallback) {
            ((WithIOCallback) robot).setOnReadLine(readLinesProcessor::onNext);
            ((WithIOCallback) robot).setOnWriteLine(writeLinesProcessor::onNext);
        }
        // Loops till the controller is running
        while (!end || connected || isInference || isRunningStatus) {
            stepUp();
        }
        logger.atInfo().setMessage("Control process ended").log();
        Stream.of(motionProcessor, proxyProcessor, supplyProcessor, contactsProcessor,
                controllerStatusProcessor, errorsProcessor,
                readLinesProcessor,
                writeLinesProcessor,
                inferencesProcessor).forEach(PublishProcessor::onComplete);
        shutdownCompletable.onComplete();
    }

    private void runStatusThread() {
        logger.atDebug().setMessage("Status process started").log();
        long prev = System.currentTimeMillis();
        lastTick = robot.simulationTime();
        long startTime = prev;
        long startSimTime = lastTick;
        this.isRunningStatus = true;
        while (!end && isReady) {
            try {
                // Advance clock of the interval
                tick();
                // Computes the real elapsed localTime and robot elapsed localTime
                long t0 = System.currentTimeMillis();
                long robotT0 = robot.simulationTime();
                this.simRealSpeed = (double) (robotT0 - startSimTime) / (t0 - startTime);
                long robotElapsed = robotT0 - lastTick;
                long realElapsed = t0 - prev;
                prev = t0;
                lastTick = robotT0;
                long expectedElapsed = round(robotElapsed / simSpeed);
                long waitTime = expectedElapsed - realElapsed - MIN_SYNC_INTERVAL;

                logger.atDebug().log("Robot elapsed {} ms", robotElapsed);
                logger.atDebug().log("Real elapsed {} ms", realElapsed);
                logger.atDebug().log("Expected elapsed {} ms", expectedElapsed);
                logger.atDebug().log("Wait {} ms", waitTime);
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error on status thread");
                break;
            }
        }
        this.isRunningStatus = false;
        logger.atInfo().setMessage("Status process ended").log();
    }

    /**
     * Schedules the inference task
     * <p>
     * Emits the latch status
     * If no inference has scheduled and reaction interval has elapsed run an inference thread emitting inference status.
     * </p>
     *
     * @param status the robot status
     */
    private void scheduleInference(RobotStatus status) {
        if (isReady) {
            long time = robot.simulationTime();
            if (onLatch != null) {
                try {
                    onLatch.accept(status);
                } catch (Throwable ex) {
                    sendError(ex);
                    logger.atError().setCause(ex).log("Error on latch");
                }
            }
            if (time >= lastInference + reactionInterval) {
                lastInference = time;
                isInference = true;
                inferencesProcessor.onNext(status);
                if (onInference != null) {
                    try {
                        onInference.accept(status);
                    } catch (Throwable ex) {
                        sendError(ex);
                        logger.atError().setCause(ex).log("Error on inference");
                    }
                }
                isInference = false;
            }
        }
    }

    private void sendError(Throwable ex) {
        errorsProcessor.onNext(ex);
    }

    @Override
    public void setOnInference(Consumer<RobotStatus> callback) {
        onInference = callback;
    }

    @Override
    public void setOnLatch(Consumer<RobotStatus> onLatch) {
        this.onLatch = onLatch;
    }

    private void setStatusTransition(Runnable trans, String signal) {
        this.statusTransition = trans;
        controllerStatusProcessor.onNext(signal);
    }

    @Override
    public void shutdown() {
        if (isStarted) {
            execute(RobotCommands.haltCommand());
            logger.atInfo().log("Shutting down...");
            end = true;
            close = true;
            setStatusTransition(this::closing, CLOSING);
        } else {
            shutdownCompletable.onComplete();
        }
    }

    @Override
    public double simRealSpeed() {
        return simRealSpeed;
    }

    @Override
    public synchronized void start() {
        requireNonNull(robot);
        if (!isStarted) {
            isStarted = true;
            setStatusTransition(this::connecting, CONNECTING);
            // Starts the controller thread
            Schedulers.io().scheduleDirect(this::runControlProcess);
        }
    }

    /**
     * Steps up
     */
    void stepUp() {
        statusTransition.run();
    }

    /**
     * Ticks the robot for interval localTime to handle communications
     *
     * @throws IOException in case of error
     */
    void tick() throws IOException {
        logger.atDebug().setMessage("Tick {}").addArgument(interval).log();
        try {
            robot.tick(interval);
        } catch (IOException e) {
            close = true;
            sendError(e);
            throw e;
        }
    }

    /**
     * Waiting the command interval
     */
    private void waitingCommandInterval() {
        logger.atDebug().log("Waiting for command {} ms", commandInterval);
        long time = robot.simulationTime();
        if (time >= lastTick + watchdogInterval) {
            logger.atError().log("No signals");
            sendError(new IOException("No signals"));
            close = true;
        }
        if (close) {
            setStatusTransition(this::closing, CLOSING);
        } else {
//            long waitTime = round(commandInterval / simSpeed);
            long waitTime = round(commandInterval / simRealSpeed);
            if (waitTime >= 1) {
                try {
                    logger.atDebug().log("Sleep thread for {} ms", waitTime);
                    Thread.sleep(waitTime);
                } catch (InterruptedException ex) {
                    sendError(ex);
                }
            }
            setStatusTransition(this::handleCommands, HANDLING_COMMANDS);
        }
    }

    /**
     * Waiting retry connect
     */
    private void waitingRetry() {
        logger.atDebug().setMessage("Waiting retry {} ms").addArgument(connectionRetryInterval).log();
        try {
            Thread.sleep(round(connectionRetryInterval / simRealSpeed));
        } catch (InterruptedException ex) {
            sendError(ex);
        }
        if (!end) {
            setStatusTransition(this::connecting, CONNECTING);
        }
    }
}
