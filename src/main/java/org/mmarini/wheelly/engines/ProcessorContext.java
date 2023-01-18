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

import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.clip;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * The processor context handles the process stack and the key, value map of the process
 * and performs the state machine flow
 */
public class ProcessorContext {
    private static final Logger logger = LoggerFactory.getLogger(ProcessorContext.class);
    private final Map<String, Object> values;
    private final List<Object> stack;
    private final StateFlow flow;
    private final Random random;
    private RobotStatus robotStatus;
    private StateNode currentNode;
    private int sensorDirection;
    private int robotDirection;
    private boolean halt;
    private double speed;
    private PolarMap polarMap;

    /**
     * Creates the processor context
     *
     * @param flow the flow of state machine
     */
    public ProcessorContext(StateFlow flow) {
        this.flow = requireNonNull(flow);
        this.values = new HashMap<>();
        this.stack = new ArrayList<>();
        this.random = new Random();
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

    /**
     * Returns the random generator
     */
    public Random getRandom() {
        return random;
    }

    /**
     * Returns the robot direction set by the process
     */
    public int getRobotDirection() {
        return robotDirection;
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
     * Returns the sensor direction set by the process
     */
    public int getSensorDirection() {
        return sensorDirection;
    }

    /**
     * Returns the robot speed set by the process
     */
    public double getSpeed() {
        return speed;
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
     * Halt the robot
     */
    public void haltRobot() {
        halt = true;
    }

    /**
     * Initializes context
     */
    public void init() {
        // Clears the stack and the value map
        clearStack();
        clearValues();
        halt = true;
        sensorDirection = 0;
        // Execute on init
        ProcessorCommand onInit = flow.getOnInit();
        if (onInit != null) {
            onInit.execute(this);
        }

        // Initializes all states
        for (StateNode state : flow.getStates()) {
            state.init(this);
        }

        // Entry state
        this.currentNode = flow.getEntry();
        logger.debug("{}: entry", currentNode.getId());
        this.currentNode.entry(this);
    }

    /**
     * Returns true if the process halted the robot
     */
    public boolean isHalt() {
        return halt;
    }

    /**
     * Moves the robot
     *
     * @param direction direction (DEG)
     * @param speed     speed
     */
    public void moveRobot(int direction, double speed) {
        this.robotDirection = normalizeDegAngle(direction);
        this.speed = clip(speed, -1, 1);
        this.halt = false;
    }

    /**
     * Move the sensor
     *
     * @param direction direction (DEG)
     */
    public void moveSensor(int direction) {
        sensorDirection = clip(normalizeDegAngle(direction), -90, 90);
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
        String result = currentNode.step(this);
        if (result != null) {
            //find for transition match
            Optional<StateTransition> tx = flow.getTransitions().stream()
                    .filter(t -> t.getFrom().equals(currentNode.getId()) && t.isTriggered(result))
                    .findFirst();
            tx.ifPresentOrElse(t -> {
                        // trigger the exit call back
                        logger.debug("{}: Trigger {}", currentNode.getId(), result);
                        currentNode.exit(this);
                        // trigger the transition call back
                        t.activate(this);
                        // Change the state
                        currentNode = getState(t.getTo());
                        // trigger the entry state call back
                        logger.debug("{}: entry", currentNode.getId());
                        currentNode.entry(this);
                    },
                    () -> logger.debug("Trigger {} - {} ignored", currentNode.getId(), result)
            );
        }
    }
}
