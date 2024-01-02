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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.StateNode.NONE_EXIT;

/**
 * The processor context handles the process stack and the key, value map of the process
 * and performs the state machine flow.
 * <p>
 * Is is a mutable object.
 * It tracks the current state of the robot, inference engine, and radars
 * </p>
 */
public class ProcessorContext {
    private static final Logger logger = LoggerFactory.getLogger(ProcessorContext.class);
    private final Map<String, Object> values;
    private final List<Object> stack;
    private final StateFlow flow;
    private final Random random;
    private final RobotControllerApi robot;
    private final PublishProcessor<StateNode> stateProcessor;
    private final PublishProcessor<String> triggerProcessor;
    private RobotStatus robotStatus;
    private StateNode currentNode;
    private PolarMap polarMap;
    private RadarMap radarMap;

    /**
     * Creates the processor context
     *
     * @param robot the robot controller
     * @param flow  the flow of state machine
     */
    public ProcessorContext(RobotControllerApi robot, StateFlow flow) {
        this.robot = requireNonNull(robot);
        this.flow = requireNonNull(flow);
        this.values = new HashMap<>();
        this.stack = new ArrayList<>();
        this.random = new Random();
        this.triggerProcessor = PublishProcessor.create();
        this.stateProcessor = PublishProcessor.create();
    }

    /**
     * Clears the stack
     */
    public void clearStack() {
        stack.clear();
    }

    /**
     * Clears the value map
     */
    public void clearValues() {
        values.clear();
    }

    /**
     * Returns a value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     * @param <T>          the type of value
     */
    public <T> T get(String key, T defaultValue) {
        return this.<T>getOpt(key).orElse(defaultValue);
    }

    /**
     * Returns a value by key of null if not exits
     *
     * @param key the key
     * @param <T> the type of value
     */
    public <T> T get(String key) {
        return get(key, null);
    }

    /**
     * Returns the block result or null if no contacts
     */
    public Tuple2<String, RobotCommands> getBlockResult() {
        return isFrontBlocked()
                ? isRearBlocked()
                ? StateNode.BLOCKED_RESULT : StateNode.FRONT_BLOCKED_RESULT
                : isRearBlocked()
                ? StateNode.REAR_BLOCKED_RESULT : null;
    }

    /**
     * Returns a double value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    public double getDouble(String key, double defaultValue) {
        Object value = values.get(key);
        return value != null ? ((Number) value).doubleValue() : defaultValue;
    }

    /**
     * Returns a double value by key or 0 if not exits
     *
     * @param key the key
     */
    public double getDouble(String key) {
        return getDouble(key, 0D);
    }

    /**
     * Returns the echo distance (m)
     */
    public double getEchoDistance() {
        return robotStatus.getEchoDistance();
    }

    /**
     * Returns a float value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    public float getFloat(String key, float defaultValue) {
        Object value = values.get(key);
        return value != null ? ((Number) value).floatValue() : defaultValue;
    }

    /**
     * Returns a double value by key or 0 if not exits
     *
     * @param key the key
     */
    public float getFloat(String key) {
        return getFloat(key, 0F);
    }

    /**
     * Returns an int value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    public int getInt(String key, int defaultValue) {
        Object value = values.get(key);
        return value != null ? ((Number) value).intValue() : defaultValue;
    }

    /**
     * Returns an int value by key or 0 if not exits
     *
     * @param key the key
     */
    public int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Returns a long value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    public long getLong(String key, long defaultValue) {
        Object value = values.get(key);
        return value != null ? ((Number) value).longValue() : defaultValue;
    }

    /**
     * Returns a long value by key or 0 if not exits
     *
     * @param key the key
     */
    public long getLong(String key) {
        return getLong(key, 0);
    }

    /**
     * Returns an optional value by key
     *
     * @param key the key
     */
    public <T> Optional<T> getOpt(String key) {
        return Optional.ofNullable((T) values.get(key));
    }

    /**
     * Returns an optional double value by key
     *
     * @param key the key
     */
    public OptionalDouble getOptDouble(String key) {
        Object v = get(key);
        return v != null ? OptionalDouble.of(((Number) v).doubleValue()) : OptionalDouble.empty();
    }

    /**
     * Returns an optional int value by key
     *
     * @param key the key
     */
    public OptionalInt getOptInt(String key) {
        Object v = get(key);
        return v != null ? OptionalInt.of(((Number) v).intValue()) : OptionalInt.empty();
    }

    /**
     * Returns an optional long value by key
     *
     * @param key the key
     */
    public OptionalLong getOptLong(String key) {
        Object v = get(key);
        return v != null ? OptionalLong.of(((Number) v).longValue()) : OptionalLong.empty();
    }

    public PolarMap getPolarMap() {
        return polarMap;
    }

    public void setPolarMap(PolarMap polarMap) {
        this.polarMap = polarMap;
    }

    public RadarMap getRadarMap() {
        return radarMap;
    }

    public void setRadarMap(RadarMap radarMap) {
        this.radarMap = radarMap;
    }

    /**
     * Returns the random generator
     */
    public Random getRandom() {
        return random;
    }

    /**
     * Returns the robot status
     */
    public RobotStatus getRobotStatus() {
        return robotStatus;
    }

    /**
     * Sets the robot status
     *
     * @param robotStatus the robot status
     */
    public void setRobotStatus(RobotStatus robotStatus) {
        this.robotStatus = robotStatus;
    }

    /**
     * Returns the stack
     */
    public List<Object> getStack() {
        return stack;
    }

    /**
     * Returns the state node by identifier of null if not exits
     *
     * @param id the state identifier
     */
    private StateNode getState(String id) {
        return flow.getState(id);
    }

    /**
     * Initializes context
     */
    public void init() {
        // Clears the stack and the value map
        clearStack();
        clearValues();
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
     * Returns true if robot has any block
     */
    public boolean isBlocked() {
        return !getRobotStatus().canMoveForward()
                || !getRobotStatus().canMoveBackward();
    }

    /**
     * Returns true if robot has front block
     */
    public boolean isFrontBlocked() {
        return !getRobotStatus().canMoveForward();
    }

    /**
     * Returns true if robot has rear block
     */
    public boolean isRearBlocked() {
        return !getRobotStatus().canMoveBackward();
    }

    /**
     * Returns the last element in the stack
     */
    public Object peek() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /**
     * Returns and remove the last element in the stack
     */
    public Object pop() {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Missing operand");
        }
        return stack.remove(stack.size() - 1);
    }

    /**
     * Returns and remove the last double value in the stack
     */
    public double popDouble() {
        return popNumber().doubleValue();
    }

    /**
     * Returns and remove the last number value in the stack
     */
    public Number popNumber() {
        Object value = pop();
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(format("Operand is not a number (%s)", value));
        }
        return (Number) value;
    }

    /**
     * Returns and remove the last string value in the stack
     */
    public String popString() {
        return pop().toString();
    }

    /**
     * Adds a value in the stack
     *
     * @param value the value
     */
    public ProcessorContext push(Object value) {
        stack.add(value);
        return this;
    }

    /**
     * Puts a value in the key, value map
     *
     * @param key   the key
     * @param value the value
     */
    public ProcessorContext put(String key, Object value) {
        values.put(key, value);
        return this;
    }

    /**
     * Returns the state flow
     */
    public Flowable<StateNode> readState() {
        return stateProcessor;
    }

    /**
     * Returns the trigger flow
     */
    public Flowable<String> readTriggers() {
        return triggerProcessor;
    }

    /**
     * Removes a key from key,value map
     *
     * @param key the key to remove
     */
    public void remove(String key) {
        values.remove(key);
    }

    /**
     * Process the next transition
     */
    public void step() {
        // Process the state node
        Tuple2<String, RobotCommands> result = currentNode.step(this);
        // Execute robot command
        robot.execute(result._2);
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
                        currentNode = getState(t.to());
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
