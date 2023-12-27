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
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Stream;

import static java.lang.Math.round;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apps.Yaml.loadDoubleArray;
import static org.mmarini.wheelly.apps.Yaml.loadIntArray;

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
 *         If the watch dog time has elapsed close the controller and move to closing state.
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
    public static final String CONNECTING = "connecting";
    public static final String WAITING_RETRY = "waitingRetry";
    public static final String CLOSING = "closing";
    public static final String HANDLING_COMMANDS = "handlingCommands";
    public static final String CONFIGURING = "configuring";
    public static final String WAITING_COMMAND_INTERVAL = "waitingCommandInterval";
    private static final Logger logger = LoggerFactory.getLogger(RobotController.class);

    /**
     * Returns the robot controller from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of robot controller configuration
     * @param robot   the robot api
     */
    public static RobotController create(JsonNode root, Locator locator, RobotApi robot) {
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
        return new RobotController(robot, interval, reactionInterval, commandInterval, connectionRetryInterval, watchdogInterval, simSpeed, decodeVoltage);
    }

    private final long commandInterval;
    private final long connectionRetryInterval;
    private final RobotApi robot;
    private final long interval;
    private final long reactionInterval;
    private final CompletableSubject shutdownCompletable;
    private final long watchdogInterval;
    private final PublishProcessor<RobotStatus> inferencesProcessor;
    private final PublishProcessor<RobotStatus> motionProcessor;
    private final PublishProcessor<RobotStatus> proxyProcessor;
    private final PublishProcessor<RobotStatus> contactsProcessor;
    private final PublishProcessor<RobotStatus> supplyProcessor;
    private final PublishProcessor<String> controllerStatusProcessor;
    private final PublishProcessor<RobotCommands> commandsProcessor;
    private final PublishProcessor<Throwable> errorsProcessor;
    private final PublishProcessor<String> readLinesProcessor;
    private final PublishProcessor<String> writeLinesProcessor;
    private final Flowable<RobotStatus> statusFlow;
    private final double simSpeed;
    private Runnable statusTransition;
    private boolean isStarted;
    private RobotCommands moveCommand;
    private int sensorDir;
    private boolean end;
    private RobotStatus robotStatus;
    private int prevSensorDir;
    private long lastSensorMoveTimestamp;
    private RobotCommands lastMoveCommand;
    private long lastRobotMoveTimestamp;
    private boolean close;
    private boolean isReady;
    private long lastInference;
    private Consumer<RobotStatus> onInference;
    private Consumer<RobotStatus> onLatch;
    private boolean isInference;
    private boolean connected;
    private long lastTick;

    /**
     * Creates the robot controller
     *
     * @param robot                   the robot api
     * @param interval                the tick interval (ms)
     * @param reactionInterval        the minimum reaction interval of inference engine (ms)
     * @param commandInterval         the interval between command send (ms)
     * @param connectionRetryInterval the connection retry interval (ms)
     * @param watchdogInterval        the watch dog interval (ms)
     * @param simSpeed                the simulation speed
     * @param decodeVoltage           the decode voltage function
     */
    public RobotController(RobotApi robot, long interval, long reactionInterval, long commandInterval, long connectionRetryInterval, long watchdogInterval, double simSpeed, IntToDoubleFunction decodeVoltage) {
        this.robot = requireNonNull(robot);
        this.interval = interval;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.connectionRetryInterval = connectionRetryInterval;
        this.watchdogInterval = watchdogInterval;
        this.simSpeed = simSpeed;
        this.end = false;
        this.motionProcessor = PublishProcessor.create();
        this.contactsProcessor = PublishProcessor.create();
        this.proxyProcessor = PublishProcessor.create();
        this.supplyProcessor = PublishProcessor.create();
        this.controllerStatusProcessor = PublishProcessor.create();
        this.commandsProcessor = PublishProcessor.create();
        this.errorsProcessor = PublishProcessor.create();
        this.readLinesProcessor = PublishProcessor.create();
        this.writeLinesProcessor = PublishProcessor.create();
        this.inferencesProcessor = PublishProcessor.create();
        this.shutdownCompletable = CompletableSubject.create();
        this.robotStatus = RobotStatus.create(decodeVoltage);
        this.statusFlow = Flowable.merge(readMotion(), readContacts(), readProxy(), readSupply());
        setStatusTransition(this::connecting, CONNECTING);
        robot.setOnClock(this::handleClock);
        robot.setOnMotion(this::handleMotion);
        robot.setOnProxy(this::handleProxy);
        robot.setOnContacts(this::handleContacts);
        robot.setOnSupply(this::handleSupply);
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
            lastTick = System.currentTimeMillis();
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
        if (command.isHalt() || command.isMove()) {
            if (command.isMove() && !(command.moveDirection >= -180 && command.moveDirection <= 179
                    && command.speed >= -MAX_PPS && command.speed <= MAX_PPS)) {
                logger.atError().setMessage("Wrong move command {}").addArgument(command).log();
            } else {
                moveCommand = command.clearScan();
            }
        }
        if (command.isScan()) {
            if (command.scanDirection >= -90 && command.scanDirection <= 90) {
                sensorDir = command.scanDirection;
            } else {
                logger.atError().setMessage("Wrong scan direction {}").addArgument(command.scanDirection).log();
            }
        }
        commandsProcessor.onNext(command);
    }

    @Override
    public RobotApi getRobot() {
        return robot;
    }


    /**
     * Handles clock event
     *
     * @param event clock event
     */
    private void handleClock(ClockSyncEvent event) {
        robotStatus = robotStatus.setClock(event);
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
            long time = status.getLocalTime();
            int dir = sensorDir;
            try {
                logger.atDebug().log("Scan dir={}, prevSensorDir={}", dir, prevSensorDir);
                // Checks for scan command required
                if (dir != this.prevSensorDir
                        || dir != 0 && time >= lastSensorMoveTimestamp + round(commandInterval / simSpeed)) {
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
                            || !cmd.isHalt() && time >= lastRobotMoveTimestamp + round(commandInterval / simSpeed)) {
                        if (cmd.isHalt()) {
                            robot.halt();
                        } else {
                            robot.move(cmd.moveDirection, cmd.speed);
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
        robotStatus = robotStatus.setContactsMessage(msg);
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
        robotStatus = robotStatus.setMotionMessage(msg);
        motionProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    /**
     * Handles proxy event
     *
     * @param msg the proxy message
     */
    private void handleProxy(WheellyProxyMessage msg) {
        robotStatus = robotStatus.setProxyMessage(msg);
        proxyProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
    }

    /**
     * Handles supply event
     *
     * @param msg the robot status
     */
    private void handleSupply(WheellySupplyMessage msg) {
        robotStatus = robotStatus.setSupplyMessage(msg);
        supplyProcessor.onNext(robotStatus);
        scheduleInference(robotStatus);
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
        while (!end || connected) {
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
        while (!end && isReady) {
            try {
                tick();
            } catch (IOException e) {
                break;
            }
        }
        logger.atDebug().setMessage("Status process ended").log();
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
            long time = System.currentTimeMillis();
            logger.atDebug().log("scheduleInference {}", isInference);
            long t0 = System.currentTimeMillis();
            logger.atDebug().log("status remote={}, dt {}", status.getRemoteTime(), t0 - status.getLocalTime());
            if (time >= lastInference + round(reactionInterval / simSpeed) && !isInference) {
                lastInference = time;
                if (onLatch != null) {
                    try {
                        onLatch.accept(status);
                    } catch (Throwable ex) {
                        sendError(ex);
                        logger.atError().setCause(ex).log();
                    }
                }
                isInference = true;
                Schedulers.computation().scheduleDirect(() -> {
                    logger.atDebug().setMessage("Inference process started").log();
                    inferencesProcessor.onNext(status);
                    if (onInference != null) {
                        try {
                            onInference.accept(status);
                        } catch (Throwable ex) {
                            sendError(ex);
                            logger.atError().setCause(ex).log();
                        }
                    }
                    isInference = false;
                    logger.atDebug().setMessage("Inference process ended").log();
                });
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
        execute(RobotCommands.halt());
        logger.atInfo().log("Shutting down...");
        end = true;
        close = true;
    }

    @Override
    public synchronized void start() {
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
     * Ticks the robot for interval time to handle communications
     *
     * @throws IOException in case of error
     */
    void tick() throws IOException {
        logger.atDebug().setMessage("Tick {}").addArgument(interval).log();
        try {
            robot.tick(interval);
            lastTick = System.currentTimeMillis();
        } catch (IOException e) {
            close = true;
            sendError(e);
            throw e;
        }
    }

    /**
     * Waiting command interval
     */
    private void waitingCommandInterval() {
        logger.atDebug().setMessage("Waiting {}").addArgument(interval).log();
        long time = System.currentTimeMillis();
        if (time >= lastTick + round(watchdogInterval / simSpeed)) {
            logger.atError().log("No signals");
            sendError(new IOException("No signals"));
            close = true;
        }
        if (close) {
            setStatusTransition(this::closing, CLOSING);
        } else {
            try {
                Thread.sleep(round(interval / simSpeed));
            } catch (InterruptedException ex) {
                sendError(ex);
            }
            setStatusTransition(this::handleCommands, HANDLING_COMMANDS);
        }
    }

    /**
     * Waiting retry connect
     */
    private void waitingRetry() {
        logger.atDebug().setMessage("Waiting retry {} ms").addArgument(connectionRetryInterval).log();
        if (!end) {
            try {
                Thread.sleep(round(connectionRetryInterval / simSpeed));
            } catch (InterruptedException ex) {
                sendError(ex);
            }
        }
        setStatusTransition(this::connecting, CONNECTING);
    }
}
