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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apps.AppYaml.loadDoubleArray;
import static org.mmarini.wheelly.apps.AppYaml.loadIntArray;

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
    private final BehaviorProcessor<RobotStatus> statusMessages;
    private final PublishProcessor<Throwable> controllerErrors;
    private final PublishProcessor<RobotCommands> commands;
    private final BehaviorProcessor<RobotControllerStatusApi> controllerStatus;
    private final AtomicReference<RobotControllerStatus> status;
    private RobotApi robot;
    private Consumer<RobotStatus> onInference;
    private Consumer<RobotStatus> onLatch;

    public RobotController(long reactionInterval, long commandInterval, IntToDoubleFunction decodeVoltage) {
        this.decodeVoltage = decodeVoltage;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.status = new AtomicReference<>(new RobotControllerStatus(
                null, false, false, true, false, null, null,
                0, 0, 0, 0, 0));
        this.statusMessages = BehaviorProcessor.create();
        this.controllerStatus = BehaviorProcessor.createDefault(status.get());
        this.commands = PublishProcessor.create();
        this.controllerErrors = PublishProcessor.create();
        this.shutdownCompletable = CompletableSubject.create();
    }

    @Override
    public RobotController connectRobot(RobotApi robot) {
        this.robot = requireNonNull(robot);
        RobotStatus robotStatus = RobotStatus.create(robot.robotSpec(), decodeVoltage);
        RobotControllerStatus st = this.status.updateAndGet(s -> s.robotStatus(robotStatus));
        this.controllerStatus.onNext(st);
        statusMessages.onNext(robotStatus);
        this.robot.readCamera()
                .subscribe(this::onCamera);
        this.robot.readLidar()
                .subscribe(this::onLidarMessage);
        this.robot.readMotion()
                .subscribe(this::onMotionMessage);
        this.robot.readSupply()
                .subscribe(this::onSupplyMessage);
        this.robot.readContacts()
                .subscribe(this::onContactsMessage);
        this.robot.readRobotStatus()
                .distinctUntilChanged(RobotStatusApi::configured)
                .subscribe(this::onRobotConfigured);
        return this;
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
                    .setSimulationTime(robot.simulationTime());
            return s.robotStatus(s1);
        });
        RobotStatus robotStatus = st.robotStatus();
        statusMessages.onNext(robotStatus);
        scheduleInference(robotStatus);
        syncActions(robotStatus);
    }

    @Override
    public void execute(RobotCommands command) {
        // Validates the command
        if (command.halt() || command.move()) {
            if (command.move()
                    && !(command.speed() >= -MAX_PPS && command.speed() <= MAX_PPS)) {
                logger.atError().setMessage("Wrong move command {}").addArgument(command).log();
            } else {
                status.updateAndGet(s -> s.moveCommand(command.clearScan()));
            }
        }
        if (command.scan()) {
            int scanDeg = command.scanDirection().toIntDeg();
            if (abs(scanDeg) <= 90) {
                status.updateAndGet(s -> s.sensorDir(scanDeg));
            } else {
                logger.atError().log("Wrong scan direction {}", scanDeg);
            }
        }
        commands.onNext(command);
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
                                .setSimulationTime(message.simulationTime())))
                .robotStatus();
        statusMessages.onNext(status);
        scheduleInference(status);
        syncActions(status);
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
                                .setSimulationTime(message.simulationTime())))
                .robotStatus();
        statusMessages.onNext(status);
        scheduleInference(status);
        syncActions(status);
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
        controllerErrors.onNext(e);
        RobotControllerStatus st1 = status.updateAndGet(RobotControllerStatus::clearInference);
        controllerStatus.onNext(st1);
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
                                .setSimulationTime(message.simulationTime())))
                .robotStatus();
        statusMessages.onNext(status);
        scheduleInference(status);
        syncActions(status);
    }

    /**
     * Handles the roboto connection
     *
     * @param status the status
     */
    private void onRobotConfigured(RobotStatusApi status) {
        logger.atDebug().log("Robot robotConfigured {}", status.configured());
        RobotControllerStatus st = this.status.updateAndGet(s -> s.ready(status.configured()));
        controllerStatus.onNext(st);
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
                                .setSimulationTime(message.simulationTime())))
                .robotStatus();
        statusMessages.onNext(status);
        scheduleInference(status);
        syncActions(status);
    }

    @Override
    public Flowable<RobotCommands> readCommand() {
        return commands;
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
    public Flowable<RobotStatus> readRobotStatus() {
        return statusMessages;
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
            if (onLatch != null) {
                try {
                    onLatch.accept(currentStatus);
                } catch (Throwable ex) {
                    controllerErrors.onNext(ex);
                }
            }
            if (onInference != null) {
                long time = currentStatus.simulationTime();
                st = status.updateAndGet(s ->
                        s.requestInference(time, reactionInterval));
                if (st.inferenceRequested()) {
                    controllerStatus.onNext(st);
                    // schedule inference
                    Completable.fromAction(() -> {
                                try {
                                    onInference.accept(currentStatus);
                                } catch (Throwable ex) {
                                    logger.atError().setCause(ex).log("Error on inference function");
                                    throw ex;
                                }
                            }).subscribeOn(Schedulers.computation())
                            .subscribe(this::onInferenceCompletion,
                                    this::onInferenceError);
                }
            }
        }
    }

    @Override
    public void setOnInference(Consumer<RobotStatus> callback) {
        this.onInference = callback;
    }

    @Override
    public void setOnLatch(Consumer<RobotStatus> callback) {
        this.onLatch = callback;
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
            robot.readMotion().blockingSubscribe();
            statusMessages.onComplete();
            controllerErrors.onComplete();
            commands.onComplete();
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
     * Synchronises the robot actions
     */
    private void syncActions(RobotStatus robotStatus) {
        long time = robotStatus.simulationTime();
        RobotControllerStatus s = status.get();
        RobotCommands cmd = s.moveCommand();
        // Checks for move command required
        if (cmd != null) {
            // Checks for move command required
            if (!cmd.equals(s.lastMoveCommand())
                    // command changed from last sent command
                    || !cmd.halt() && (time >= s.lastRobotMoveTimestamp() + commandInterval)
                    // or move command and the command interval timed out
                    || cmd.halt() && !robot.isHalt()) {
                // or robot is not halt
                if (cmd.halt()) {
                    robot.halt();
                } else {
                    robot.move(cmd.moveDirection().toIntDeg(), cmd.speed());
                }
                status.updateAndGet(s1 ->
                        s1.lastRobotMoveTimestamp(time).lastMoveCommand(cmd)
                );
            }
        }
        // Checks for scan command required
        int dir = s.sensorDir();
        if (dir != s.prevSensorDir()
                || dir != 0 && time >= s.lastSensorMoveTimestamp() + commandInterval) {
            robot.scan(dir);
            status.updateAndGet(s1 ->
                    s1.lastSensorMoveTimestamp(time)
                            .prevSensorDir(dir));
        }

    }
}
