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
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;
import static org.mmarini.yaml.schema.Validator.objectPropertiesRequired;
import static org.mmarini.yaml.schema.Validator.positiveInteger;

/**
 * Manages the processing threads and event generation to interface the robot
 */
public class RobotController implements RobotControllerApi {
    public static final Validator SPEC = objectPropertiesRequired(Map.of(
            "interval", positiveInteger(),
            "reactionInterval", positiveInteger(),
            "commandInterval", positiveInteger(),
            "connectionRetryInterval", positiveInteger(),
            "watchdogInterval", positiveInteger()
    ), List.of("interval",
            "reactionInterval",
            "commandInterval",
            "connectionRetryInterval",
            "watchdogInterval"));
    public static final String CONNECTING = "connecting";
    public static final String WAITING_RETRY = "waitingRetry";
    public static final String CLOSING = "closing";
    public static final String SCAN = "scan";
    public static final String CONFIGURING = "configuring";
    public static final String WAIT_COMMAND_INTERVAL = "waitCommandInterval";
    public static final String MOVE = "move";
    private static final Logger logger = LoggerFactory.getLogger(RobotController.class);

    /**
     * Returns the robot controller from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of robot controller configuration
     * @param robot   the robot api
     */
    public static RobotController create(JsonNode root, Locator locator, RobotApi robot) {
        SPEC.apply(locator).accept(root);
        long interval = locator.path("interval").getNode(root).asLong();
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong();
        long commandInterval = locator.path("commandInterval").getNode(root).asLong();
        long connectionRetryInterval = locator.path("connectionRetryInterval").getNode(root).asLong();
        long watchdogInterval = locator.path("watchdogInterval").getNode(root).asLong();
        return new RobotController(robot, interval, reactionInterval, commandInterval, connectionRetryInterval, watchdogInterval);
    }

    private final long commandInterval;
    private final long connectionRetryInterval;
    private final RobotApi robot;
    private final long interval;
    private final long reactionInterval;
    private final CompletableSubject shutdownCompletable;
    private final long watchdogInterval;
    private final PublishProcessor<RobotStatus> robotStatusProcessor;
    private final PublishProcessor<RobotStatus> inferencesProcessor;
    private final PublishProcessor<String> controllerStatusProcessor;
    private final PublishProcessor<RobotCommands> commandsProcessor;
    private final PublishProcessor<Throwable> errorsProcessor;
    private final PublishProcessor<String> readLinesProcessor;
    private final PublishProcessor<String> writeLinesProcessor;
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
     */
    public RobotController(RobotApi robot, long interval, long reactionInterval, long commandInterval, long connectionRetryInterval, long watchdogInterval) {
        this.robot = requireNonNull(robot);
        this.interval = interval;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.connectionRetryInterval = connectionRetryInterval;
        this.watchdogInterval = watchdogInterval;
        this.end = false;
        this.robotStatusProcessor = PublishProcessor.create();
        this.controllerStatusProcessor = PublishProcessor.create();
        this.commandsProcessor = PublishProcessor.create();
        this.errorsProcessor = PublishProcessor.create();
        this.readLinesProcessor = PublishProcessor.create();
        this.writeLinesProcessor = PublishProcessor.create();
        this.inferencesProcessor = PublishProcessor.create();
        this.shutdownCompletable = CompletableSubject.create();
        setStatusTransition(this::connecting, CONNECTING);
        robot.setOnStatusReady(this::handleRobotStatus);
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
            setStatusTransition(this::handleScan, SCAN);
            isReady = true;
            lastTick = System.currentTimeMillis();
            Schedulers.io().scheduleDirect(this::runStatusThread);
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
     * Handles move robot status
     */
    private void handleMove() {
        logger.atDebug().setMessage("Handle move").log();
        if (close) {
            setStatusTransition(this::closing, CLOSING);
        }
        try {
            RobotStatus status = robotStatus;
            RobotCommands cmd = this.moveCommand;
            if (status != null && cmd != null) {
                long time = status.getTime();
                // Checks for move command required
                if (!cmd.equals(lastMoveCommand)
                        || !cmd.isHalt() && time >= lastRobotMoveTimestamp + commandInterval) {
                    if (cmd.isHalt()) {
                        robot.halt();
                    } else {
                        robot.move(cmd.moveDirection, cmd.speed);
                    }
                    this.lastRobotMoveTimestamp = time;
                    lastMoveCommand = cmd;
                }
            }
            setStatusTransition(this::waitingCommandInterval, WAIT_COMMAND_INTERVAL);
        } catch (Exception ex) {
            sendError(ex);
            setStatusTransition(this::closing, CLOSING);
        }
    }

    private void handleRobotStatus(RobotStatus status) {
        robotStatus = status;
        sendStatus(status);
        long time = status.getTime();
        if (time > lastInference + reactionInterval && !isInference) {
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

    /**
     * Handles sensor scan status
     */
    void handleScan() {
        logger.atDebug().setMessage("Handle scan").log();
        if (close) {
            setStatusTransition(this::closing, CLOSING);
        }
        try {
            RobotStatus status = robotStatus;
            if (status != null) {
                long time = status.getTime();
                int dir = sensorDir;
                // Checks for scan command required
                if (dir != this.prevSensorDir
                        || dir != 0 && time >= lastSensorMoveTimestamp + commandInterval) {
                    robot.scan(dir);
                    lastSensorMoveTimestamp = time;
                    prevSensorDir = dir;
                }
                setStatusTransition(this::handleMove, MOVE);
            } else {
                setStatusTransition(this::waitingCommandInterval, WAIT_COMMAND_INTERVAL);
            }
        } catch (Exception ex) {
            sendError(ex);
            setStatusTransition(this::closing, CLOSING);
        }
    }

    @Override
    public Flowable<RobotCommands> readCommand() {
        return commandsProcessor;
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
    public Flowable<String> readReadLine() {
        return readLinesProcessor;
    }

    @Override
    public Flowable<RobotStatus> readRobotStatus() {
        return robotStatusProcessor;
    }

    @Override
    public Completable readShutdown() {
        return shutdownCompletable;
    }

    @Override
    public Flowable<String> readWriteLine() {
        return writeLinesProcessor;
    }

    private void runControlProcess() {
        logger.atInfo().setMessage("Control process started").log();
        if (robot instanceof WithIOCallback) {
            ((WithIOCallback) robot).setOnReadLine(readLinesProcessor::onNext);
            ((WithIOCallback) robot).setOnWriteLine(writeLinesProcessor::onNext);
        }
        while (!(end && !connected)) {
            stepUp();
        }
        logger.atInfo().setMessage("Control process ended").log();
        robotStatusProcessor.onComplete();
        controllerStatusProcessor.onComplete();
        errorsProcessor.onComplete();
        readLinesProcessor.onComplete();
        writeLinesProcessor.onComplete();
        inferencesProcessor.onComplete();
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

    private void sendError(Throwable ex) {
        errorsProcessor.onNext(ex);
    }

    private void sendStatus(RobotStatus status) {
        robotStatusProcessor.onNext(status);
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
        logger.atInfo().log("Shutting down...");
        end = true;
        close = true;
    }

    @Override
    public synchronized void start() {
        if (!isStarted) {
            isStarted = true;
            Schedulers.io().scheduleDirect(this::runControlProcess);
        }
    }

    /**
     * Steps up
     */
    void stepUp() {
        statusTransition.run();
    }

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
        if (time >= lastTick + watchdogInterval) {
            logger.atError().log("No signals");
            sendError(new IOException("No signals"));
            close = true;
        }
        if (close) {
            setStatusTransition(this::closing, CLOSING);
        } else {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ex) {
                sendError(ex);
            }
            setStatusTransition(this::handleScan, SCAN);
        }
    }

    /**
     * Waiting retry connect
     */
    private void waitingRetry() {
        logger.atDebug().setMessage("Waiting retry {} ms").addArgument(connectionRetryInterval).log();
        if (!end) {
            try {
                Thread.sleep(connectionRetryInterval);
            } catch (InterruptedException ex) {
                sendError(ex);
            }
        }
        setStatusTransition(this::connecting, CONNECTING);
    }
}
