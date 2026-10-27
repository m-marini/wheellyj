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

package org.mmarini.wheelly.control;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.mmarini.wheelly.robots.*;
import org.mmarini.wheelly.utils.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntToDoubleFunction;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.rx.RXFunc.logError;
import static org.mmarini.wheelly.utils.AppYaml.loadDoubleArray;
import static org.mmarini.wheelly.utils.AppYaml.loadIntArray;
import static org.mmarini.wheelly.utils.Utils.linear;


/**
 * Controls the execution loop, event pipelines, and status monitoring for a robot instance.
 * <p>
 * This class coordinates real-time data flow by ingestive processing of high-frequency
 * sensor telemetry and dispatching operational commands. It integrates visual computer vision
 * payloads from the <b>ArUco marker image recognition system</b>, correlating detected
 * marker landmarks with concurrent LiDAR readings to feed the underlying AI inference engine.
 * It relies on internal reactive processors to broadcast status changes, computational errors,
 * and system lifecycles.
 * </p>
 */
public class RobotController implements ControllerApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/controller-schema-2.0";
    public static final String REACTION_INTERVAL_ID = "reactionInterval";
    public static final String COMMAND_INTERVAL_ID = "commandInterval";
    public static final String SUPPLY_VALUES_ID = "supplyValues";
    public static final String VOLTAGES_ID = "voltages";
    private static final Logger logger = LoggerFactory.getLogger(RobotController.class);

    /**
     * Instantiates a robot controller parsed from a JSON configuration tree.
     * <p>
     * This method validates the provided node tree against {@link #SCHEMA_NAME}
     * before initialising interval thresholds and calculating the linear voltage conversion parameters.
     * </p>
     *
     * @param root the root node containing the configuration structure
     * @param file the source file used for context mapping
     * @return a fully constructed robot controller instance
     * @throws IllegalArgumentException if the configuration array lengths fail structural constraints
     */
    public static RobotController create(JsonNode root, File file) {
        Locator locator = Locator.root();
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long reactionInterval = locator.path(REACTION_INTERVAL_ID).getNode(root).asLong();
        long commandInterval = locator.path(COMMAND_INTERVAL_ID).getNode(root).asLong();
        int[] supplyValues = loadIntArray(root, locator.path(SUPPLY_VALUES_ID));
        double[] voltages = loadDoubleArray(root, locator.path(VOLTAGES_ID));
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
    private final BehaviorProcessor<ControllerStatusApi> controllerStatus;
    private final AtomicReference<RobotControllerStatus> status;
    private Consumer<RobotStatus> onRobotStatus;
    private Consumer<RobotStatus> onLatch;
    private Consumer<RobotStatus> onInferences;
    private RobotApi robot;
    private Consumer<HeadStatus> onHeadStatus;
    private Consumer<MotionStatus> onMotionStatus;

    /**
     * Initialises a new robot controller instance with structural configurations.
     *
     * @param reactionInterval the interval threshold for inference reaction times (ms)
     * @param commandInterval  the interval threshold for hardware command dispatching (ms)
     * @param decodeVoltage    the mathematical function mapping raw inputs to analogue voltage values
     */
    public RobotController(long reactionInterval, long commandInterval, IntToDoubleFunction decodeVoltage) {
        this.decodeVoltage = decodeVoltage;
        this.reactionInterval = reactionInterval;
        this.commandInterval = commandInterval;
        this.status = new AtomicReference<>(new RobotControllerStatus(
                MotionStatus.halt(), HeadStatus.lookStraight(), null,
                false, true, false,
                0,
                MotionStatus.halt(), HeadStatus.lookStraight(), 0, 0));
        this.controllerStatus = BehaviorProcessor.createDefault(status.get());
        this.controllerErrors = PublishProcessor.create();
        this.shutdownCompletable = CompletableSubject.create();
    }

    @Override
    public void addOnRobotStatus(Consumer<RobotStatus> callback) {
        this.onRobotStatus = onRobotStatus == null ? callback : onRobotStatus.andThen(callback);
    }

    /**
     * Synchronises internal movement states and head alignment routines across hardware components.
     */
    private void checkForSync() {
        syncMotionStatus();
        syncHeadStatus();
    }

    @Override
    public void headStatus(HeadStatus headStatus) {
        status.updateAndGet(s ->
                s.headStatus(headStatus));
        syncHeadStatus();
        if (onHeadStatus != null) {
            onHeadStatus.accept(headStatus);
        }
    }

    @Override
    public RobotController init(RobotApi robot) {
        this.robot = requireNonNull(robot);
        RobotStatus robotStatus = RobotStatus.create(robot.robotSpec(), decodeVoltage);
        RobotControllerStatus st = this.status.updateAndGet(s -> s.robotStatus(robotStatus));
        this.controllerStatus.onNext(st);
        if (onRobotStatus != null) {
            onRobotStatus.accept(robotStatus);
        }
        this.robot.onCamera(this::onCamera);
        this.robot.addOnLidar(this::onLidarMessage);
        this.robot.addOnContacts(this::onContactsMessage);
        this.robot.addOnMotion(this::onMotionMessage);
        this.robot.addOnSupply(this::onSupplyMessage);
        this.robot.readRobotStatus()
                .subscribeOn(Schedulers.io())
                .distinctUntilChanged(RobotStatusApi::configured)
                .subscribe(this::onRobotConfigured,
                        logError(logger, "Error reading robot configuration status")
                );
        return this;
    }

    @Override
    public void motionStatus(MotionStatus motionStatus) {
        status.updateAndGet(s ->
                s.motionStatus(motionStatus));
        syncMotionStatus();
        if (onMotionStatus != null) {
            onMotionStatus.accept(motionStatus);
        }
    }

    /**
     * Processes inbound frames capturing spatial video metadata and detection data
     * from the ArUco marker image recognition system.
     * <p>
     * This method correlates the visual marker positions with the concurrent LiDAR telemetry
     * to refine spatial awareness before triggering the next inference cycle.
     * </p>
     *
     * @param cameraEvent the raw event payload containing the recognised ArUco marker details
     */
    private void onCamera(CameraEvent cameraEvent) {
        RobotControllerStatus st = status.updateAndGet(s -> {
            RobotStatus s1 = s.robotStatus()
                    .setCameraMessage(new CorrelatedCameraEvent(cameraEvent, s.robotStatus().lidarMessage()));
            return s.robotStatus(s1);
        });
        RobotStatus robotStatus = st.robotStatus();
        if (onRobotStatus != null) {
            onRobotStatus.accept(robotStatus);
        }
        scheduleInference(robotStatus);
    }

    /**
     * Processes telemetry changes captured by tactile sensors on the mechanical assembly.
     *
     * @param message the payload tracking localised collision contact states
     */
    private void onContactsMessage(WheellyContactsMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setContactsMessage(message)))
                .robotStatus();
        if (onRobotStatus != null) {
            onRobotStatus.accept(status);
        }
        scheduleInference(status);
    }

    @Override
    public RobotController onHeadStatus(Consumer<HeadStatus> callback) {
        onHeadStatus = onHeadStatus == null ? callback : callback.andThen(onHeadStatus);
        return this;
    }

    @Override
    public void onInference(Consumer<RobotStatus> callback) {
        requireNonNull(callback);
        onInferences = onInferences == null ? callback : onInferences.andThen(callback);
    }

    @Override
    public void onLatch(Consumer<RobotStatus> callback) {
        requireNonNull(callback);
        onLatch = onLatch == null ? callback : onLatch.andThen(callback);
    }

    /**
     * Processes telemetry frames received from the onboard LiDAR sensor assembly.
     * <p>
     * This method updates the current spatial telemetry state, broadcasts the revised snapshot
     * to active robot status consumers, schedules a new inference pass based on the updated environment,
     * and performs a hardware synchronisation check.
     * </p>
     *
     * @param message the inbound message payload containing raw distance data from the LiDAR scan
     */
    private void onLidarMessage(WheellyLidarMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setLidarMessage(message)))
                .robotStatus();
        if (onRobotStatus != null) {
            onRobotStatus.accept(status);
        }
        scheduleInference(status);
        checkForSync();
    }

    /**
     * Processes telemetry updates received from the robot's motion control system and odometry sensors.
     * <p>
     * This method updates the internal movement state within the current snapshot, broadcasts
     * the revised status to active consumers, schedules a subsequent inference execution pass,
     * and performs a hardware synchronisation check.
     * </p>
     *
     * @param message the inbound message payload containing locomotion and encoder data
     */
    private void onMotionMessage(WheellyMotionMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setMotionMessage(message)))
                .robotStatus();
        if (onRobotStatus != null) {
            onRobotStatus.accept(status);
        }
        scheduleInference(status);
        checkForSync();
    }

    @Override
    public RobotController onMotionStatus(Consumer<MotionStatus> callback) {
        onMotionStatus = onMotionStatus == null ? callback : callback.andThen(onMotionStatus);
        return this;
    }
    /**
     * Processes the final closure of the robot connection and manages the subsystem termination.
     * <p>
     * This method flags the controller as no longer ready, finalises the error and status
     * reactive processing pipelines by emitting completion signals, triggers the final shutdown
     * completable, and logs the successful finalisation.
     * </p>
     */
    private void onRobotClosed() {
        RobotControllerStatus st = status.updateAndGet(s -> s.ready(false));
        controllerErrors.onComplete();
        controllerStatus.onNext(st);
        controllerStatus.onComplete();
        shutdownCompletable.onComplete();
        logger.atInfo().log("Controller shut down.");
    }

    /**
     * Processes configuration status changes received from the robot hardware.
     * <p>
     * This method updates the internal state to reflect whether the robot has been fully
     * and correctly configured, and subsequently broadcasts the revised operational state
     * to all active status consumers.
     * </p>
     *
     * @param status the inbound status payload representing the robot's current configuration state
     */
    private void onRobotConfigured(RobotStatusApi status) {
        logger.atDebug().log("Robot configured {}", status.configured());
        RobotControllerStatus st = this.status.updateAndGet(s -> s.ready(status.configured()));
        controllerStatus.onNext(st);
    }

    /**
     * Processes electrical telemetry updates received from the robot's power supply subsystem.
     * <p>
     * This method updates the internal battery and power supply metrics within the current snapshot,
     * broadcasts the revised status to active consumers, and schedules a subsequent inference
     * execution pass based on the new power state.
     * </p>
     *
     * @param message the inbound message payload containing voltage and power supply diagnostics
     */
    private void onSupplyMessage(WheellySupplyMessage message) {
        RobotStatus status = this.status.updateAndGet(st ->
                        st.robotStatus(st.robotStatus()
                                .setSupplyMessage(message)))
                .robotStatus();
        if (onRobotStatus != null) {
            onRobotStatus.accept(status);
        }
        scheduleInference(status);
    }

    @Override
    public Flowable<ControllerStatusApi> readControllerStatus() {
        return controllerStatus;
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return controllerErrors.mergeWith(robot.readErrors());
    }

    @Override
    public Flowable<Boolean> readReady() {
        return readControllerStatus()
                .map(ControllerStatusApi::ready)
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
     * Schedules and dispatches the AI and vision inference tasks.
     * <p>
     * If no inference routine is currently active and the configured reaction interval
     * threshold has elapsed, this method triggers the latch callback, updates the state
     * processors, fires the inference engine pipelines, and subsequently clears the inference flag.
     * </p>
     *
     * @param currentStatus the current robot telemetry snapshot to analyse
     */
    private void scheduleInference(RobotStatus currentStatus) {
        // Check for controller ready
        long time = currentStatus.robotTime();
        RobotControllerStatus prevStatus = status.getAndUpdate(s ->
                s.requestInference(time, reactionInterval)
        );
        if (prevStatus.isInferenceReady(time, reactionInterval)) {
            if (onLatch != null) {
                onLatch.accept(currentStatus);
            }
            controllerStatus.onNext(status.get());
            if (onInferences != null) {
                onInferences.accept(currentStatus);
            }
            RobotControllerStatus st = status.updateAndGet(RobotControllerStatus::clearInference);
            controllerStatus.onNext(st);
        }
    }

    @Override
    public void shutdown() {
        if (status.getAndUpdate(s -> s.started(false)).started()) {
            logger.atInfo().log("Shutting down controller...");
            robot.halt();
            try {
                robot.close();
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error shuttingDown robot");
            }
            robot.readRobotStatus()
                    .ignoreElements()
                    .subscribe(this::onRobotClosed);
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
     * Synchronises the robot's physical head and sensor orientation commands with the current state.
     * <p>
     * Evaluates the timestamp of the latest LiDAR message. If the designated command interval
     * has expired, it updates the tracked head registration time and issues the appropriate hardware
     * command (such as scanning or tracking targets) via the robot API.
     * </p>
     */
    private void syncHeadStatus() {
        RobotControllerStatus s = status.get();
        WheellyLidarMessage wheellyLidarMessage = s.robotStatus().lidarMessage();
        long time = wheellyLidarMessage.time();
        if (time > s.lastHeadTime() + commandInterval) {
            HeadStatus headStatus = status.updateAndGet(s1 ->
                            s1.registerHeadStatus(time))
                    .headStatus();
            switch (headStatus.status()) {
                case FIX_DIRECTION -> robot.scan(headStatus.direction().toIntDeg());
                case FRONT_TRACK -> robot.track(true, headStatus.target());
                case REAR_TRACK -> robot.track(false, headStatus.target());
            }
        }
    }

    /**
     * Synchronises the robot's locomotive and motion control commands with the current state.
     * <p>
     * Evaluates the timestamp of the latest movement telemetry message.
     * If the command interval expires or the head alignment changes,
     * it updates the locomotion registration time and triggers the
     * corresponding movement action via the robot API.
     * </p>
     */
    private void syncMotionStatus() {
        RobotControllerStatus s = status.get();
        WheellyMotionMessage msg = s.robotStatus().motionMessage();
        long time = msg.time();
        if (time > s.lastMotionTime() + commandInterval
                || !s.headStatus().equals(s.lastHeadStatus())) {
            MotionStatus motionStatus = status.updateAndGet(s1 ->
                            s1.registerMotionStatus(time))
                    .motionStatus();
            switch (motionStatus.status()) {
                case HALT -> robot.halt();
                case FORWARD -> robot.move(true, motionStatus.target());
                case BACKWARD -> robot.move(false, motionStatus.target());
                case ROTATE -> robot.rotate(motionStatus.targetDir().toIntDeg());
            }
        }
    }
}
