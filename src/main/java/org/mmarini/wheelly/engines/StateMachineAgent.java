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
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.apps.Yaml;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * State machine agent acts the robot basing on state machine flow.
 * <p>
 * The main method is <code>tick</code> which for the duration of the reaction localTime checks the I/O to the robot.<br>
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
public class StateMachineAgent implements WithIOFlowable, WithStatusFlowable, WithErrorFlowable, WithCommandFlowable, WithControllerFlowable {
    public static final String STATE_AGENT_SCHEMA_YML = "https://mmarini.org/wheelly/state-agent-schema-0.9";
    private static final Logger logger = LoggerFactory.getLogger(StateMachineAgent.class);

    /**
     * Returns the agent from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of agent specification in the document
     * @param robot   the robot api
     */
    public static StateMachineAgent create(JsonNode root, Locator locator, RobotControllerApi robot) {
        StateFlow flow = StateFlow.create(root, locator.path("flow"));
        double minRadarDistance = locator.path("minRadarDistance").getNode(root).asDouble();
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        int numRadarSectors = locator.path("numRadarSectors").getNode(root).asInt();
        RadarMap radarMap = RadarMap.create(root, locator);
        PolarMap polarMap = PolarMap.create(numRadarSectors);
        return new StateMachineAgent(minRadarDistance, maxRadarDistance, radarMap, polarMap, robot, new ProcessorContext(robot, flow));
    }

    /**
     * Returns the state machine agent
     *
     * @param config     the root document
     * @param locator    the configuration locator
     * @param controller the controller
     */
    public static StateMachineAgent fromConfig(JsonNode config, Locator locator, RobotControllerApi controller) {
        return Yaml.fromConfig(config, locator, STATE_AGENT_SCHEMA_YML, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    private final RobotControllerApi controller;
    private final ProcessorContext context;
    private final double maxRadarDistance;
    private final double minRadarDistance;
    private final PublishProcessor<ProcessorContext> stepUpProcessor;
    private PolarMap polarMap;
    private RadarMap radarMap;
    private boolean started;

    /**
     * Creates the agent
     *
     * @param minRadarDistance the min radar distance (m)
     * @param maxRadarDistance the max radar distance (m)
     * @param radarMap         the radar map
     * @param polarMap         the polar map
     * @param controller       the robot api
     * @param context          the processor context
     */
    public StateMachineAgent(double minRadarDistance, double maxRadarDistance, RadarMap radarMap, PolarMap polarMap, RobotControllerApi controller, ProcessorContext context) {
        this.minRadarDistance = minRadarDistance;
        this.maxRadarDistance = maxRadarDistance;
        this.controller = requireNonNull(controller);
        this.context = requireNonNull(context);
        this.polarMap = requireNonNull(polarMap);
        this.radarMap = requireNonNull(radarMap);
        this.stepUpProcessor = PublishProcessor.create();
    }

    /**
     * Returns the current context
     */
    public ProcessorContext getContext() {
        return context;
    }

    public RobotControllerApi getController() {
        return controller;
    }

    public double getMaxRadarDistance() {
        return this.maxRadarDistance;
    }

    private void handleInference(RobotStatus status) {
        polarMap = polarMap.update(radarMap, status.getLocation(), status.getDirection(), minRadarDistance, maxRadarDistance);
        context.setPolarMap(polarMap);
        if (!started) {
            started = true;
            context.init();
        }
        context.step();
        stepUpProcessor.onNext(context);
    }

    private void handleLatch(RobotStatus status) {
        logger.atDebug().log("Latch status {}", status);
        context.setRobotStatus(status);
        context.setRadarMap(radarMap);

    }

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

    public Completable readShutdown() {
        return controller.readShutdown();
    }

    public Flowable<ProcessorContext> readStepUp() {
        return stepUpProcessor;
    }

    @Override
    public Flowable<RobotStatus> readSupply() {
        return controller.readSupply();
    }

    @Override
    public Flowable<String> readWriteLine() {
        return controller.readWriteLine();
    }

    public void shutdown() {
        controller.shutdown();
        stepUpProcessor.onComplete();
    }
}
