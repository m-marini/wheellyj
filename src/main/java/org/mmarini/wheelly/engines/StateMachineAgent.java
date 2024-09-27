/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.StateNode.NONE_EXIT;

/**
 * State machine agent acts the robot basing on state machine flow.
 * <p>
 * <ul>
 *     <li>The <code>interval</code> parameter defines the interval (ms) between the read robot status.</li>
 *     <li>The <code>commandInterval</code> parameter defines the interval (ms) between sending output command to the robot.</li></7LU>
 *     <li>The <code>reactionInterval</code> parameter defines the interval (ms) between each state transition</li>
 * </ul>
 * </p>
 * <p>
 *     It is a mutable object
 * </p>
 */
public class StateMachineAgent implements ProcessorContext, WithIOFlowable, WithStatusFlowable, WithErrorFlowable,
        WithCommandFlowable, WithControllerFlowable {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-agent-schema-3.0";
    private static final Logger logger = LoggerFactory.getLogger(StateMachineAgent.class);

    /**
     * Returns the agent from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of agent specification in the document
     * @param robot   the robot api
     */
    public static StateMachineAgent create(JsonNode root, Locator locator, RobotControllerApi robot) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        StateFlow flow = StateFlow.create(root, locator.path("flow"));
        double minRadarDistance = locator.path("minRadarDistance").getNode(root).asDouble();
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        int numRadarSectors = locator.path("numRadarSectors").getNode(root).asInt();
        RadarMap radarMap = RadarMap.create(root, locator);
        PolarMap polarMap = PolarMap.create(numRadarSectors);
        return new StateMachineAgent(minRadarDistance, maxRadarDistance, radarMap, polarMap, robot, flow);
    }

    private final RobotControllerApi controller;
    private final double maxRadarDistance;
    private final double minRadarDistance;
    private final PublishProcessor<ProcessorContext> stepUpProcessor;
    private final PublishProcessor<String> triggerProcessor;
    private final PublishProcessor<Optional<Point2D>> targetProcessor;
    private final Map<String, Object> values;
    private final List<Object> stack;
    private final StateFlow flow;
    private final PublishProcessor<StateNode> stateProcessor;
    private PolarMap polarMap;
    private RadarMap radarMap;
    private boolean started;
    private StateNode currentNode;
    private RobotStatus robotStatus;
    private RadarMap contextRadarMap;

    /**
     * Creates the agent
     *
     * @param minRadarDistance the min radar distance (m)
     * @param maxRadarDistance the max radar distance (m)
     * @param radarMap         the radar map
     * @param polarMap         the polar map
     * @param controller       the robot api
     */
    public StateMachineAgent(double minRadarDistance, double maxRadarDistance, RadarMap radarMap, PolarMap polarMap, RobotControllerApi controller, StateFlow flow) {
        this.minRadarDistance = minRadarDistance;
        this.maxRadarDistance = maxRadarDistance;
        this.controller = requireNonNull(controller);
        this.polarMap = requireNonNull(polarMap);
        this.radarMap = requireNonNull(radarMap);
        this.flow = flow;
        this.stepUpProcessor = PublishProcessor.create();
        this.stack = new ArrayList<>();
        this.values = new HashMap<>();
        this.triggerProcessor = PublishProcessor.create();
        this.stateProcessor = PublishProcessor.create();
        this.targetProcessor = PublishProcessor.create();
    }

    @Override
    public void clearMap() {
        radarMap = radarMap.clean();
    }

    @Override
    public <T> T get(String key) {
        return (T) values.get(key);
    }

    /**
     * Returns the controller
     */
    public RobotControllerApi getController() {
        return controller;
    }

    /**
     * Returns the maximum radar distance (m)
     */
    public double getMaxRadarDistance() {
        return this.maxRadarDistance;
    }

    /**
     * Handles the inference event
     * <p>
     * Update the polar map with robot status,
     * advances one step,
     * feed the step flow
     * </p>
     *
     * @param status the robot status
     */
    private void handleInference(RobotStatus status) {
        long t0 = System.currentTimeMillis();
        logger.atDebug().log("Handle inference");
        polarMap = polarMap.update(radarMap, status.location(), status.direction(), minRadarDistance, maxRadarDistance);
        logger.atDebug().log("Polar map updated in {} ms", System.currentTimeMillis() - t0);
        robotStatus = status;
        if (!started) {
            started = true;
            initContext();
        }
        step();
        stepUpProcessor.onNext(this);
        logger.atDebug().log("Handle inference completed in {} ms", System.currentTimeMillis() - t0);
    }

    /**
     * Handles the latch event.
     * <p>
     * Stores the robot status and the radar map at latch instant
     * </p>
     *
     * @param status the status
     */
    private void handleLatch(RobotStatus status) {
        logger.atDebug().log("Latch status {}", status);
        robotStatus = status;
        contextRadarMap = radarMap;
    }

    /**
     * Handles the status event
     * <p>
     * update the radar map
     * </p>
     *
     * @param status the robot status
     */
    private void handleStatus(RobotStatus status) {
        radarMap = radarMap.update(status);
    }

    /**
     * Initializes the agent.
     * <p>
     * Initializes the robot and state machine context from the entry state
     */
    public void init() {
        controller.setOnInference(this::handleInference);
        controller.setOnLatch(this::handleLatch);
        controller.readRobotStatus().doOnNext(this::handleStatus).subscribe();
        controller.start();
    }

    /**
     * Initializes context
     */
    private void initContext() {
        // Clears the stack and the value map
        stack.clear();
        values.clear();
        // Execute on init
        ProcessorCommand onInit = flow.onInit();
        if (onInit != null) {
            onInit.execute(this);
        }

        // Initializes all states
        for (StateNode state : flow.states()) {
            state.init(this);
        }

        // Entry state
        this.currentNode = flow.entry();
        logger.debug("{}: entry", currentNode.id());
        this.currentNode.entry(this);
        stateProcessor.onNext(currentNode);
    }

    @Override
    public <T> T peek() {
        return stack.isEmpty() ? null : (T) stack.getLast();
    }

    @Override
    public PolarMap polarMap() {
        return polarMap;
    }

    @Override
    public <T> T pop() {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Missing operand");
        }
        return (T) stack.removeLast();
    }

    @Override
    public <T> ProcessorContext push(T value) {
        stack.add(value);
        return this;
    }

    @Override
    public <T> ProcessorContext put(String key, T value) {
        values.put(key, value);
        return this;
    }

    @Override
    public RadarMap radarMap() {
        return contextRadarMap;
    }

    @Override
    public Flowable<RobotStatus> readCamera() {
        return controller.readCamera();
    }

    @Override
    public Flowable<RobotCommands> readCommand() {
        return controller.readCommand();
    }

    @Override
    public Flowable<RobotStatus> readContacts() {
        return controller.readContacts();
    }

    @Override
    public Flowable<String> readControllerStatus() {
        return controller.readControllerStatus();
    }

    @Override
    public Flowable<Throwable> readErrors() {
        return controller.readErrors();
    }

    @Override
    public Flowable<RobotStatus> readMotion() {
        return controller.readMotion();
    }

    @Override
    public Flowable<RobotStatus> readProxy() {
        return controller.readProxy();
    }

    @Override
    public Flowable<String> readReadLine() {
        return controller.readReadLine();
    }

    @Override
    public Flowable<RobotStatus> readRobotStatus() {
        return controller.readRobotStatus();
    }

    /**
     * Returns the shutdown event
     */
    public Completable readShutdown() {
        return controller.readShutdown();
    }

    /**
     * Returns the state flow
     */
    public Flowable<StateNode> readState() {
        return stateProcessor;
    }

    /**
     * Returns the process context step up event
     */
    public Flowable<ProcessorContext> readStepUp() {
        return stepUpProcessor;
    }

    @Override
    public Flowable<RobotStatus> readSupply() {
        return controller.readSupply();
    }

    /**
     * Returns the flow of target points
     */
    public Flowable<Optional<Point2D>> readTargets() {
        return targetProcessor;
    }

    /**
     * Returns the trigger flow
     */
    public Flowable<String> readTriggers() {
        return triggerProcessor;
    }

    @Override
    public Flowable<String> readWriteLine() {
        return controller.readWriteLine();
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public RobotStatus robotStatus() {
        return robotStatus;
    }

    @Override
    public void setTarget(Point2D target) {
        targetProcessor.onNext(Optional.ofNullable(target));
    }

    public void shutdown() {
        controller.shutdown();
        stepUpProcessor.onComplete();
    }

    @Override
    public int stackSize() {
        return stack.size();
    }

    /**
     * Process the next transition
     */
    public void step() {
        // Process the state node
        Tuple2<String, RobotCommands> result = currentNode.step(this);
        // Execute robot command
        controller.execute(result._2);
        triggerProcessor.onNext(result._1);
        if (!NONE_EXIT.equals(result._1)) {
            //find for transition match
            Optional<StateTransition> tx = flow.transitions().stream()
                    .filter(t -> t.from().equals(currentNode.id()) && t.isTriggered(result._1))
                    .findFirst();
            tx.ifPresentOrElse(t -> {
                        // trigger the exit call back
                        logger.debug("{}: Trigger {}", currentNode.id(), result);
                        currentNode.exit(this);
                        // trigger the transition call back
                        t.activate(this);
                        // Change the state
                        currentNode = flow.getState(t.to());
                        // trigger the entry state call back
                        logger.debug("{}: entry", currentNode.id());
                        currentNode.entry(this);
                        stateProcessor.onNext(currentNode);
                    },
                    () -> logger.debug("Trigger {} - {} ignored", currentNode.id(), result._1)
            );
        }
    }
}
