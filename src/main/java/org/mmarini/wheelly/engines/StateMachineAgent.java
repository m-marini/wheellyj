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
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModellerConnector;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.StateNode.*;

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
public class StateMachineAgent implements ProcessorContextApi {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/agent-state-machine-schema-0.3";
    private static final Logger logger = LoggerFactory.getLogger(StateMachineAgent.class);

    /**
     * Returns the agent from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of agent specification in the document
     */
    public static StateMachineAgent create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        StateFlow flow = StateFlow.create(root, locator.path("flow"));
        return new StateMachineAgent(flow);
    }

    /**
     * Returns the agent from configuration
     *
     * @param file the configuration document
     */
    public static StateMachineAgent fromFile(File file) throws IOException {
        JsonNode root = Utils.fromFile(file);
        Locator locator = Locator.root();
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        StateFlow flow = StateFlow.create(root, locator.path("flow"));
        return new StateMachineAgent(flow);
    }

    private final PublishProcessor<ProcessorContextApi> stepUpProcessor;
    private final PublishProcessor<String> triggerProcessor;
    private final PublishProcessor<Optional<Point2D>> targetProcessor;
    private final PublishProcessor<List<Point2D>> pathProcessor;
    private final Map<String, Object> values;
    private final List<Object> stack;
    private final StateFlow flow;
    private final PublishProcessor<StateNode> stateProcessor;
    private StateNode currentNode;
    private WorldModellerConnector modeller;
    private WorldModel worldModel;

    /**
     * Creates the agent
     *
     * @param flow the state flow
     */
    public StateMachineAgent(StateFlow flow) {
        this.flow = flow;
        this.stepUpProcessor = PublishProcessor.create();
        this.stack = new ArrayList<>();
        this.values = new HashMap<>();
        this.triggerProcessor = PublishProcessor.create();
        this.stateProcessor = PublishProcessor.create();
        this.targetProcessor = PublishProcessor.create();
        this.pathProcessor = PublishProcessor.create();
    }

    @Override
    public void clearMap() {
        this.modeller.clearRadarMap();
    }

    /**
     * Connects the world modeller
     *
     * @param modeller the world modeller
     */
    public void connect(WorldModellerConnector modeller) {
        this.modeller = requireNonNull(modeller);
        modeller.setOnInference(this::onInference);
    }

    @Override
    public StateNode currentNode() {
        return currentNode;
    }

    @Override
    public <T> T get(String key) {
        return (T) values.get(key);
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

    /**
     * Returns the robot command by inferring the event world model
     *
     * @param worldModel the world model
     */
    private RobotCommands onInference(WorldModel worldModel) {
        this.worldModel = worldModel;
        if (this.currentNode == null) {
            initContext();
        }
        RobotCommands commands = step();
        stepUpProcessor.onNext(this);
        return commands;
    }

    @Override
    public <T> T peek() {
        return stack.isEmpty() ? null : (T) stack.getLast();
    }

    @Override
    public <T> T pop() {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Missing operand");
        }
        return (T) stack.removeLast();
    }

    @Override
    public <T> ProcessorContextApi push(T value) {
        stack.add(value);
        return this;
    }

    @Override
    public <T> ProcessorContextApi put(String key, T value) {
        values.put(key, value);
        if (key.endsWith("." + TARGET_ID)) {
            Object obj = get(key);
            if (obj instanceof Point2D target) {
                targetProcessor.onNext(Optional.ofNullable(target));
            }
        } else if (key.endsWith("." + PATH_ID)) {
            Object obj = get(key);
            if (obj instanceof List<?> path) {
                pathProcessor.onNext((List<Point2D>) path);
            }
        }
        return this;
    }

    /**
     * Returns the state flow
     */
    public Flowable<List<Point2D>> readPath() {
        return pathProcessor;
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
    public Flowable<ProcessorContextApi> readStepUp() {
        return stepUpProcessor;
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
    public void remove(String key) {
        values.remove(key);
        if (key.endsWith("." + TARGET_ID)) {
            targetProcessor.onNext(Optional.empty());
        } else if (key.endsWith("." + PATH_ID)) {
            pathProcessor.onNext(List.of());
        }
    }

    @Override
    public int stackSize() {
        return stack.size();
    }

    /**
     * Returns the robot command by processing the next transition
     */
    public RobotCommands step() {
        // Process the state node
        Tuple2<String, RobotCommands> result = currentNode.step(this);
        // Execute robot command
        String exitTag = result._1;
        RobotCommands commands = result._2;
        triggerProcessor.onNext(exitTag);
        if (!NONE_EXIT.equals(exitTag)) {
            //find for transition match
            Optional<StateTransition> tx = flow.transitions().stream()
                    .filter(t -> t.from().equals(currentNode.id()) && t.isTriggered(exitTag))
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
                    () -> logger.debug("Trigger {} - {} ignored", currentNode.id(), exitTag)
            );
        }
        return commands;
    }

    @Override
    public WorldModel worldModel() {
        return worldModel;
    }
}
