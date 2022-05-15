/*
 *
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

package org.mmarini.wheelly.engines.statemachine;

import java.awt.geom.Point2D;
import java.util.*;

import static java.util.Objects.requireNonNull;

public class StateMachineContext {
    public static final String TIMEOUT_KEY = "timeout";
    public static final String STATUS_NAME_KEY = "name";
    public static final String ENTRY_TIME_KEY = "entryTime";
    public static final String OBSTACLE_KEY = "obstacle";
    public static final String TARGET_KEY = "target";

    /**
     *
     */
    public static StateMachineContext create() {
        return new StateMachineContext(new HashMap<>());
    }

    private final Map<String, Object> values;

    /**
     * @param values
     */
    protected StateMachineContext(Map<String, Object> values) {
        this.values = requireNonNull(values);
    }

    public StateMachineContext clearObstacle() {
        remove(OBSTACLE_KEY);
        return this;
    }

    public StateMachineContext clearTarget() {
        return remove(TARGET_KEY);
    }

    /**
     * @param key
     * @param <T>
     */
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) values.get(key));
    }

    public OptionalDouble getDouble(String key) {
        Number value = (Number) values.get(key);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value.doubleValue());
    }

    public double getDouble(String key, double defaultValue) {
        return getDouble(key).orElse(defaultValue);
    }

    public OptionalInt getInt(String key) {
        Number value = (Number) values.get(key);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value.intValue());
    }

    public int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    public OptionalLong getLong(String key) {
        Number value = (Number) values.get(key);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value.longValue());
    }

    public long getLong(String key, long defaultValue) {
        return getLong(key).orElse(defaultValue);
    }

    public Optional<Point2D> getObstacle() {
        return get(OBSTACLE_KEY);
    }

    public StateMachineContext setObstacle(Point2D location) {
        values.put(OBSTACLE_KEY, requireNonNull(location));
        return this;
    }

    public StateMachineContext setObstacle(Optional<Point2D> location) {
        return put(OBSTACLE_KEY, requireNonNull(location));
    }

    public Optional<String> getStatusName() {
        return get(STATUS_NAME_KEY);
    }

    public StateMachineContext setStatusName(String name) {
        values.put(STATUS_NAME_KEY, requireNonNull(name));
        return this;
    }

    public Optional<Point2D> getTarget() {
        return get(TARGET_KEY);
    }

    public StateMachineContext setTarget(Point2D location) {
        values.put(TARGET_KEY, requireNonNull(location));
        return this;
    }

    public StateMachineContext setTarget(Optional<Point2D> location) {
        return put(TARGET_KEY, requireNonNull(location));
    }

    /**
     * @param key
     * @param value
     * @param <T>
     */
    public <T> StateMachineContext put(String key, T value) {
        values.put(key, value);
        return this;
    }

    /**
     * @param key
     * @param value
     * @param <T>
     */
    public <T> StateMachineContext put(String key, Optional<T> value) {
        value.ifPresentOrElse(
                v -> this.values.put(key, v),
                () -> values.remove(key));
        return this;
    }

    /**
     * @param key
     */
    public StateMachineContext remove(String key) {
        values.remove(key);
        return this;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
