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
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
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
    private static final Logger logger = LoggerFactory.getLogger(RobotController.class);
    private static final RobotCommand HALT_COMMAND = new RobotCommand() {
    };

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
    Runnable statusTransition;
    private boolean isStarted;
    private RobotCommand moveCommand;
    private int sensorDir;
    private boolean end;
    private RobotStatus robotStatus;
    private int prevSensorDir;
    private long lastSensorMoveTimestamp;
    private RobotCommand lastMoveCommand;
    private long lastRobotMoveTimestamp;
    private boolean close;
    private Consumer<Throwable> onError;
    private Consumer<RobotStatus> onStatusReady;
    private boolean isReady;
    private long lastInference;
    private Consumer<RobotStatus> onInference;
    private boolean isInference;
    private boolean connected;
    private long lastTick;
    private Consumer<RobotStatus> onLatch;

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
        this.statusTransition = this::connecting;
        shutdownCompletable = CompletableSubject.create();
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
        statusTransition = this::waitingRetry;
    }

    /**
     * Handles the configuring status.
     * Configures the robot.
     */
    void configuring() {
        logger.atDebug().setMessage("Configuring").log();
        if (close) {
            statusTransition = this::closing;
        }
        try {
            robot.configure();
            statusTransition = this::handleScan;
            isReady = true;
            lastTick = System.currentTimeMillis();
            Schedulers.io().scheduleDirect(this::runStatusThread);
        } catch (Exception ex) {
            sendError(ex);
            statusTransition = this::closing;
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
            statusTransition = this::configuring;
            connected = true;
        } catch (Exception ex) {
            sendError(ex);
            statusTransition = this::waitingRetry;
        }
    }

    @Override
    public RobotApi getRobot() {
        return robot;
    }

    @Override
    public void haltRobot() {
        moveCommand = HALT_COMMAND;
    }

    /**
     * Handles move robot status
     */
    private void handleMove() {
        logger.atDebug().setMessage("Handle move").log();
        if (close) {
            statusTransition = this::closing;
        }
        try {
            RobotStatus status = robotStatus;
            RobotCommand cmd = this.moveCommand;
            if (status != null && cmd != null) {
                long time = status.getTime();
                // Checks for move command required
                if (!cmd.equals(lastMoveCommand)
                        || !cmd.equals(HALT_COMMAND) && time >= lastRobotMoveTimestamp + commandInterval) {
                    if (cmd.equals(HALT_COMMAND)) {
                        robot.halt();
                    } else {
                        MoveCommand moveCmd = (MoveCommand) cmd;
                        robot.move(moveCmd.direction, moveCmd.speed);
                    }
                    this.lastRobotMoveTimestamp = time;
                    lastMoveCommand = cmd;
                }
            }
            statusTransition = this::waitingCommandInterval;
        } catch (Exception ex) {
            sendError(ex);
            statusTransition = this::closing;
        }
    }

    private void handleRobotStatus(RobotStatus status) {
        robotStatus = status;
        sendStatus(status);
        long time = status.getTime();
        if (time > lastInference + reactionInterval && !isInference) {
            lastInference = time;
            isInference = true;
            if (onLatch != null) {
                onLatch.accept(status);
            }
            Schedulers.computation().scheduleDirect(() -> {
                logger.atDebug().setMessage("Inference process started").log();
                if (onInference != null) {
                    try {
                        onInference.accept(status);
                    } catch (Throwable ex) {
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
            statusTransition = this::closing;
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
                statusTransition = this::handleMove;
            } else {
                statusTransition = this::waitingCommandInterval;
            }
        } catch (Exception ex) {
            sendError(ex);
            statusTransition = this::closing;
        }
    }

    @Override
    public void moveRobot(int direction, int speed) {
        moveCommand = new MoveCommand(direction, speed);
    }

    @Override
    public void moveSensor(int direction) {
        sensorDir = direction;
    }

    @Override
    public Completable readShutdown() {
        return shutdownCompletable;
    }

    private void runControlProcess() {
        logger.atInfo().setMessage("Control process started").log();
        while (!(end && !connected)) {
            stepUp();
        }
        logger.atInfo().setMessage("Control process ended").log();
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
        if (onError != null) {
            try {
                onError.accept(ex);
            } catch (Throwable ex1) {
                logger.atError().setCause(ex1).log();
            }
        }
    }

    private void sendStatus(RobotStatus status) {
        if (onStatusReady != null) {
            try {
                onStatusReady.accept(status);
            } catch (Throwable ex) {
                logger.atError().setCause(ex).log();
            }
        }
    }

    @Override
    public void setOnError(Consumer<Throwable> callback) {
        onError = callback;
    }

    @Override
    public void setOnInference(Consumer<RobotStatus> callback) {
        onInference = callback;
    }

    @Override
    public void setOnLatch(Consumer<RobotStatus> onLatch) {
        this.onLatch = onLatch;
    }

    @Override
    public void setOnReadLine(Consumer<String> callback) {
        if (robot instanceof WithIOCallback) {
            ((WithIOCallback) robot).setOnReadLine(callback);
        }
    }

    @Override
    public void setOnStatusReady(Consumer<RobotStatus> callback) {
        onStatusReady = callback;
    }

    @Override
    public void setOnWriteLine(Consumer<String> callback) {
        if (robot instanceof WithIOCallback) {
            ((WithIOCallback) robot).setOnWriteLine(callback);
        }
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
            statusTransition = this::closing;
        } else {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException ex) {
                sendError(ex);
            }
            statusTransition = this::handleScan;
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
        statusTransition = this::connecting;
    }

    interface RobotCommand {
    }

    static class MoveCommand implements RobotCommand {
        public final int direction;
        public final int speed;

        MoveCommand(int direction, int speed) {
            this.direction = direction;
            this.speed = speed;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MoveCommand that = (MoveCommand) o;
            return direction == that.direction && speed == that.speed;
        }

        @Override
        public int hashCode() {
            return Objects.hash(direction, speed);
        }
    }
}
