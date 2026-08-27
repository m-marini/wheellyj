/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apps.AppYaml.loadDoubleArray;
import static org.mmarini.wheelly.apps.AppYaml.loadIntArray;
import static org.mmarini.wheelly.rx.RXFunc.logError;

/**
 * Robot controller
 */
public class RobotController implements RobotControllerApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/controller-schema-2.0";
    private static final Logger logger = LoggerFactory.getLogger(RobotController.class);

    /**
     * Returns the robot controller from configuration
     *
     * @param root the configuration document
     * @param file the configuration file
     */
    public static RobotController create(JsonNode root, File file) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long reactionInterval = locator.path("reactionInterval").getNode(root).asLong();
        long commandInterval = locator.path("commandInterval").getNode(root).asLong();
        int[] supplyValues = loadIntArray(root, locator.path("supplyValues"));
        double[] voltages = loadDoubleArray(root, locator.path("voltages"));
        if (!(supplyValues.length == 2)) {
            throw new IllegalArgumentException(format("supplyValues must have 2 items (%d)", supplyValues.length));
        }
        if (!(voltages.length == 2)) {
            throw new IllegalArgumentException(format("voltages must have 2 items (%d)", voltages.length));
        }
        IntToDoubleFunction decodeVoltage = x -> linear(x, supplyValues[0], supplyValues[1], voltages[0], voltages[1]);
        return new RobotController(reactionInterval, commandInterval, decodeVoltage);
    }

    private final long commandInterval;
    private final long reactionInterval;
    private final IntToDoubleFunction decodeVoltage;
    private final CompletableSubject shutdownCompletable;
    private final PublishProcessor<Throwable> controllerErrors;
    private final BehaviorProcessor<RobotControllerStatusApi> controllerStatus;
    private final AtomicReference<RobotControllerStatus> status;
    private final List<Consumer<RobotStatus>> onRobotStatus;
    private final List<Consumer<RobotStatus>> onLatches;
    private final List<Consumer<RobotCommands>> onCommands;
    private final List<Consumer<RobotStatus>> onInferences;
    private RobotApi robot;

    /**
     * Creates the robot controller
     *
     * @param reactionInterval the reaction interval (ms)
     * @param commandInterval  the command interval (ms)
     * @param decodeVoltage    the decode voltage function
     */
    public RobotController(long reactionInterval, long commandInterval, IntToDoubleFunction decodeVoltage) {
        this.decodeVoltage = decodeVoltage;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.status = new AtomicReference<>(new RobotControllerStatus(
                null, RobotCommands.halt(),
                false, false, true, false,
                0, 0, 0, null));
        this.controllerStatus = BehaviorProcessor.createDefault(status.get());
        this.controllerErrors = PublishProcessor.create();
        this.shutdownCompletable = CompletableSubject.create();
        this.onRobotStatus = new ArrayList<>();
        this.onLatches = new ArrayList<>();
        this.onCommands = new ArrayList<>();
        this.onInferences = new ArrayList<>();
    }

    /**
     * Synchronises the robot actions
     */
    private void checkForSync(RobotStatus robotStatus) {
        long time = robotStatus.robotTime();
        RobotControllerStatus s = status.get();
        // Check for the command interval
        if (s.syncRequired(time)) {
            syncActions(robotStatus);
        }
    }

    @Override
    public RobotController connectRobot(RobotApi robot) {
        this.robot = requireNonNull(robot);
        RobotStatus robotStatus = RobotStatus.create(robot.robotSpec(), decodeVoltage);
        RobotControllerStatus st = this.status.updateAndGet(s -> s.robotStatus(robotStatus));
        this.controllerStatus.onNext(st);
        notifyRobotStatus(robotStatus);
        this.robot.onCamera(this::onCamera);
        this.robot.onLidar(this::onLidarMessage);
        this.robot.onContacts(this::onContactsMessage);
        this.robot.onMotion(this::onMotionMessage);
        this.robot.onSupply(this::onSupplyMessage);
        this.robot.readRobotStatus()
                .subscribeOn(Schedulers.computation())
                .distinctUntilChanged(RobotStatusApi::configured)
                .subscribe(this::onRobotConfigured,
                        logError(logger, "Error reading robot configuration status")
                );
        return this;
    }

    @Override
    public void execute(RobotCommands command) {
        // Validates the command
        int scanDeg = command.scanDirection();
        if (abs(scanDeg) > 90) {
            logger.atError().log("Wrong scan direction {}", scanDeg);
            return;
        }
        robot.scan(scanDeg);
        RobotControllerStatus prevStatus = status.getAndUpdate(s -> s.command(command));
        if (!Objects.equals(prevStatus.command(), command)) {
            // command changed
            syncActions(status.get().robotStatus());
        }
        for (Consumer<RobotCommands> callback : onCommands) {
            callback.accept(command);
        }
    }

    void notifyOnLatch(RobotStatus status) {
        for (Consumer<RobotStatus> callback : onLatches) {
            try {
                callback.accept(status);
            } catch (Throwable ex) {
                logger.atError().setCause(ex).log("Error on latch");
                controllerErrors.onNext(ex);
            }
        }
    }

    /**
     * Notify robot status
     *
     * @param robotStatus the robot status
     */
    private void notifyRobotStatus(RobotStatus robotStatus) {
        for (Consumer<RobotStatus> callback : onRobotStatus) {
            callback.accept(robotStatus);
        }
    }

    /**
     * Handles camera events
     *
     * @param cameraEvent the camera event
     */
    private void onCamera(CameraEvent cameraEvent) {
        RobotControllerStatus st = status.updateAndGet(s -> {
            RobotStatus s1 = s.robotStatus()
                    .setCameraMessage(new CorrelatedCameraEvent(cameraEvent, s.robotStatus().lidarMessage()))
                    .setSimulationTime(robot.robotTime());
            return s.robotStatus(s1);
        });
        RobotStatus robotStatus = st.robotStatus();
        notifyRobotStatus(robotStatus);
        scheduleInference(robotStatus);
        checkForSync(robotStatus);
    }

    @Override
    public void onCommand(Consumer<RobotCommands> callback) {
        onCommands.add(callback);
    }

    /**
     * Handles contacts messages
     *
     * @param message the message
     */
    private void onContactsMessage(WheellyContactsMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setContactsMessage(message)
                                .setSimulationTime(message.time())))
                .robotStatus();
        notifyRobotStatus(status);
        scheduleInference(status);
        checkForSync(status);
    }

    @Override
    public void onInference(Consumer<RobotStatus> callback) {
        this.onInferences.add(callback);
    }

    /**
     * Handles the inference completion
     */
    private void onInferenceCompletion() {
        RobotControllerStatus st1 = status.updateAndGet(RobotControllerStatus::clearInference);
        controllerStatus.onNext(st1);
    }

    /**
     * Handles the inference error
     *
     * @param e the error
     */
    private void onInferenceError(Throwable e) {
        logger.atError().setCause(e).log("Error on inference");
        controllerErrors.onNext(e);
        RobotControllerStatus st1 = status.updateAndGet(RobotControllerStatus::clearInference);
        controllerStatus.onNext(st1);
    }

    @Override
    public void onLatch(Consumer<RobotStatus> callback) {
        this.onLatches.add(callback);
    }

    /**
     * Handles lidar messages
     *
     * @param message the message
     */
    private void onLidarMessage(WheellyLidarMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setLidarMessage(message)
                                .setSimulationTime(message.time())))
                .robotStatus();
        notifyRobotStatus(status);
        scheduleInference(status);
        checkForSync(status);
    }

    /**
     * Handles motion messages
     *
     * @param message the message
     */
    private void onMotionMessage(WheellyMotionMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setMotionMessage(message)
                                .setSimulationTime(message.time())))
                .robotStatus();
        notifyRobotStatus(status);
        scheduleInference(status);
        checkForSync(status);
    }

    /**
     * Handles the robot connection
     *
     * @param status the status
     */
    private void onRobotConfigured(RobotStatusApi status) {
        logger.atDebug().log("Robot configured {}", status.configured());
        RobotControllerStatus st = this.status.updateAndGet(s -> s.ready(status.configured()));
        controllerStatus.onNext(st);
    }

    @Override
    public void onRobotStatus(Consumer<RobotStatus> callback) {
        onRobotStatus.add(callback);
    }

    /**
     * Handles supply messages
     *
     * @param message the message
     */
    private void onSupplyMessage(WheellySupplyMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setSupplyMessage(message)
                                .setSimulationTime(message.time())))
                .robotStatus();
        notifyRobotStatus(status);
        scheduleInference(status);
        checkForSync(status);
    }

    @Override
    public Flowable<RobotControllerStatusApi> readControllerStatus() {
        return controllerStatus;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return controllerErrors.mergeWith(robot.readErrors());
    }

    @Override
    public Flowable<Boolean> readReady() {
        return readControllerStatus()
                .map(RobotControllerStatusApi::ready)
                .distinctUntilChanged();
    }

    @Override
    public Completable readShutdown() {
        return shutdownCompletable;
    }

    @Override
    public void reconnect() {
        robot.reconnect();
    }

    /**
     * Schedules the inference task
     * <p>
     * Emits the latch status
     * If no inference has scheduled and reaction interval has elapsed run an inference thread emitting inference status.
     * </p>
     *
     * @param currentStatus the robot status
     */
    private void scheduleInference(RobotStatus currentStatus) {
        // Check for controller ready
        RobotControllerStatus st = status.get();
        if (st.ready()) {
            // notify latch of status
            notifyOnLatch(currentStatus);
            long time = currentStatus.robotTime();
            st = status.updateAndGet(s ->
                    s.requestInference(time, reactionInterval));
            if (st.inferenceRequested()) {
                controllerStatus.onNext(st);
                // schedule inference
                Completable.fromAction(() -> {
                            for (Consumer<RobotStatus> callback : onInferences) {
                                try {
                                    callback.accept(currentStatus);
                                } catch (Throwable ex) {
                                    logger.atError().setCause(ex).log("Error on inference function");
                                    throw ex;
                                }
                            }
                        }).subscribeOn(Schedulers.computation())
                        .subscribe(this::onInferenceCompletion,
                                this::onInferenceError);
            }
        }
    }

    @Override
    public void shutdown() {
        if (status.getAndUpdate(s -> s.started(false)).started()) {
            logger.atInfo().log("Shutting down...");
            robot.halt();
            try {
                robot.close();
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error closing robot");
            }
            robot.readRobotStatus().blockingSubscribe();
            controllerErrors.onComplete();
            RobotControllerStatus st = status.updateAndGet(s -> s.ready(false));
            controllerStatus.onNext(st);
            controllerStatus.onComplete();
            shutdownCompletable.onComplete();
            logger.atInfo().log("Shut down.");
        }
    }

    @Override
    public double simRealSpeed() {
        return robot.simulationSpeed();
    }

    @Override
    public void start() {
        if (!status.getAndUpdate(s -> s.started(true)).started()) {
            controllerStatus.onNext(status.get());
            robot.connect();
        }
    }

    /**
     * Synchronises the robot status to commands
     */
    private void syncActions(RobotStatus robotStatus) {
        long time = robotStatus.robotTime();
        //RobotControllerStatus s = status.get();
        RobotControllerStatus s = status.updateAndGet(s1 -> {
            RobotCommands cmd = s1.command();
            if (!cmd.isHalt()
                    && time > s1.commandTime() + commandInterval
                    && robotStatus.halt()) {
                logger.atDebug().log("Halt due command {} not accepted", cmd.status());
                return s1.command(RobotCommands.halt());
            }
            return s1;
        });
        RobotCommands cmd = s.command();
        // Check for commands required
        switch (cmd.status()) {
            case HALT -> {
                if (!robotStatus.halt()) {
                    robot.halt();
                }
            }
            case ROTATE -> {
                // Rotate command
                if (!robotStatus.direction().isCloseTo(cmd.rotationDirection(),
                        robotStatus.robotSpec().directionRange().toIntDeg())) {
                    robot.rotate(cmd.rotationDirection());
                }
            }
            case FORWARD -> {
                // forward command
                Point2D robotLocation = robotStatus.location();
                Point2D target = cmd.target();
                double distance = robotLocation.distance(target);
                if (distance > robot.robotSpec().targetRange()) {
                    robot.forward(target);
                }
            }
            case BACKWARD -> {
                // backward command
                Point2D robotLocation = robotStatus.location();
                Point2D target = cmd.target();
                double distance = robotLocation.distance(target);
                if (distance > robot.robotSpec().targetRange()) {
                    robot.backward(target);
                }
            }
        }
        // Check for the head direction
        int scanDeg = cmd.scanDirection();
        if (scanDeg != 0 || !cmd.isHalt()) {
            robot.scan(scanDeg);
        }
        status.updateAndGet(s1 ->
                s1.nextSyncTime(time + commandInterval));
    }
}
