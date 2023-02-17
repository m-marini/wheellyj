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
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * State machine agent acts the robot basing on state machine flow.
 * <p>
 * The main method is <code>tick</code> which for the duration of the reaction time checks the I/O to the robot.<br>
 * <ul>
 *     <li>The <code>interval</code> parameter defines the interval (ms) between the read robot status.</li>
 *     <li>The <code>commandInterval</code> parameter defines the interval (ms) between sending output command to the robot.</li></7LU>
 *     <li>The <code>reactionInterval</code> parameter defines the interval (ms) between each state transition</li>
 * </ul>
 * </p>
 */
public class StateMachineAgent implements WithIOCallback, WithStatusCallback, WithErrorCallback {
    public static final Validator AGENT_SPEC = objectPropertiesRequired(Map.of(
            "flow", StateFlow.STATE_FLOW_SPEC,
            "numRadarSectors", integer(minimum(2)),
            "minRadarDistance", positiveNumber(),
            "maxRadarDistance", positiveNumber()
    ), List.of(
            "flow",
            "numRadarSectors", "minRadarDistance", "maxRadarDistance"
    ));

    /**
     * Returns the agent from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of agent specification in the document
     * @param robot   the robot api
     */
    public static StateMachineAgent create(JsonNode root, Locator locator, RobotControllerApi robot) {
        AGENT_SPEC.apply(locator).accept(root);
        StateFlow flow = StateFlow.create(root, locator.path("flow"));
        double minRadarDistance = locator.path("minRadarDistance").getNode(root).asDouble();
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        int numRadarSectors = locator.path("numRadarSectors").getNode(root).asInt();
        RadarMap radarMap = RadarMap.create(root, locator);
        PolarMap polarMap = PolarMap.create(numRadarSectors);
        return new StateMachineAgent(minRadarDistance, maxRadarDistance, radarMap, polarMap, robot, new ProcessorContext(robot, flow));
    }

    private final RobotControllerApi controller;
    private final ProcessorContext context;
    private final double maxRadarDistance;
    private final double minRadarDistance;
    private PolarMap polarMap;
    private RadarMap radarMap;
    private Consumer<ProcessorContext> onStepUp;
    private boolean started;
    private Consumer<RobotStatus> onStatusReady;

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
        if (onStepUp != null) {
            onStepUp.accept(context);
        }
    }

    private void handleLatch(RobotStatus status) {
        context.setRobotStatus(status);
        context.setRadarMap(radarMap);
    }

    private void handleStatus(RobotStatus status) {
        radarMap = radarMap.update(status);
        if (onStatusReady != null) {
            onStatusReady.accept(status);
        }
    }

    /**
     * Initializes the agent.
     * <p>
     * Initializes the robot and state machine context from the entry state
     */
    public void init() {
        controller.setOnInference(this::handleInference);
        controller.setOnStatusReady(this::handleStatus);
        controller.setOnLatch(this::handleLatch);
        controller.start();
    }

    public Completable readShutdown() {
        return controller.readShutdown();
    }

    public void setOnCommand(Consumer<RobotCommands> callback) {
        controller.setOnCommand(callback);
    }

    @Override
    public void setOnError(Consumer<Throwable> callback) {
        controller.setOnError(callback);
    }

    @Override
    public void setOnReadLine(Consumer<String> callback) {
        controller.setOnReadLine(callback);
    }

    @Override
    public void setOnStatusReady(Consumer<RobotStatus> callback) {
        onStatusReady = callback;
    }

    public void setOnStepUp(Consumer<ProcessorContext> callback) {
        this.onStepUp = callback;
    }

    @Override
    public void setOnWriteLine(Consumer<String> callback) {
        controller.setOnWriteLine(callback);
    }

    public void shutdown() {
        controller.shutdown();
    }
}
