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

import org.mmarini.wheelly.apis.WorldModel;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.OptionalLong;

import static java.lang.String.format;

/**
 * The processor context handles interaction between states and robot
 * agent the process stack and the key, value map of the process
 * and performs the state machine flow.
 * <p>
 * Is is a mutable object.
 * It tracks the current state of the robot, inference engine, and radars
 * </p>
 */
public interface ProcessorContextApi {

    /**
     * Clears the radar map
     */
    void clearMap();

    /**
     * Returns the current state node
     */
    StateNode currentNode();

    /**
     * Returns a value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     * @param <T>          the type of value
     */
    default <T> T get(String key, T defaultValue) {
        return this.<T>getOpt(key).orElse(defaultValue);
    }

    /**
     * Returns a value by key of null if not exits
     *
     * @param key the key
     * @param <T> the type of value
     */
    <T> T get(String key);

    /**
     * Returns a double value by key or 0 if not exits
     *
     * @param key the key
     */
    default double getDouble(String key) {
        return getDouble(key, 0D);
    }

    /**
     * Returns a double value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    default double getDouble(String key, double defaultValue) {
        return switch (get(key)) {
            case Number n -> n.doubleValue();
            case null, default -> defaultValue;
        };
    }

    /**
     * Returns a double value by key or 0 if not exits
     *
     * @param key the key
     */
    default float getFloat(String key) {
        return getFloat(key, 0F);
    }

    /**
     * Returns a float value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    default float getFloat(String key, float defaultValue) {
        return switch (get(key)) {
            case Number n -> n.floatValue();
            case null, default -> defaultValue;
        };
    }

    /**
     * Returns an int value by key or 0 if not exits
     *
     * @param key the key
     */
    default int getInt(String key) {
        return getInt(key, 0);
    }

    /**
     * Returns an int value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    default int getInt(String key, int defaultValue) {
        return switch (get(key)) {
            case Number n -> n.intValue();
            case null, default -> defaultValue;
        };
    }

    /**
     * Returns a long value by key or 0 if not exits
     *
     * @param key the key
     */
    default long getLong(String key) {
        return getLong(key, 0);
    }

    /**
     * Returns a long value by key
     *
     * @param key          the key
     * @param defaultValue the default value if not exits
     */
    default long getLong(String key, long defaultValue) {
        return switch (get(key)) {
            case Number n -> n.longValue();
            case null, default -> defaultValue;
        };
    }

    /**
     * Returns an optional value by key
     *
     * @param key the key
     */
    default <T> Optional<T> getOpt(String key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * Returns an optional long by key
     *
     * @param key the key
     */
    default OptionalLong getOptLong(String key) {
        return switch (get(key)) {
            case Number n -> OptionalLong.of(n.longValue());
            case null, default -> OptionalLong.empty();
        };
    }

    /**
     * Returns the last element in the stack
     *
     * @param <T> the element type
     */
    <T> T peek();

    /**
     * Returns and remove the last element in the stack
     *
     * @param <T> the element type
     */
    <T> T pop();

    /**
     * Returns and remove the last double value in the stack
     */
    default double popDouble() {
        return popNumber().doubleValue();
    }

    /**
     * Returns and remove the last number value in the stack
     */
    default Number popNumber() {
        Object value = pop();
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(format("Operand is not a number (%s)", value));
        }
        return (Number) value;
    }

    /**
     * Returns and remove the last string value in the stack
     */
    default String popString() {
        return pop().toString();
    }

    /**
     * Adds a value in the stack
     *
     * @param value the value
     * @param <T>   the element type
     */
    <T> ProcessorContextApi push(T value);

    /**
     * Puts a value in the key, value map
     *
     * @param key   the key
     * @param value the value
     * @param <T>   the element type
     */
    <T> ProcessorContextApi put(String key, T value);

    /**
     * Removes a key from key,value map
     *
     * @param key the key to remove
     */
    void remove(String key);

    /**
     * Sets the target point
     *
     * @param target the target point
     */
    void setTarget(Point2D target);

    /**
     * Returns the number of stackl elements
     */
    int stackSize();

    /**
     * Returns the current world model
     */
    WorldModel worldModel();
}
