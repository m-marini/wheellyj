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

package org.mmarini.wheelly.engines;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Abstract state node
 */
public abstract class AbstractStateNode implements StateNode {
    private final String id;
    private final ProcessorCommand onInit;
    private final ProcessorCommand onEntry;
    private final ProcessorCommand onExit;
    private long entryTime;

    /**
     * Creates the abstract node
     *
     * @param id      the state identifier
     * @param onInit  the init command
     * @param onEntry the entry command
     * @param onExit  the exit command
     */
    protected AbstractStateNode(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public long elapsedTime(ProcessorContextApi context) {
        return context.worldModel().robotStatus().simulationTime() - entryTime;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        this.entryTime = context.worldModel().robotStatus().simulationTime();
        ProcessorCommand onEntry = onEntry();
        if (onEntry != null) {
            onEntry.execute(context);
        }
    }

    @Override
    public long entryTime(ProcessorContextApi context) {
        return entryTime;
    }

    @Override
    public void exit(ProcessorContextApi context) {
        ProcessorCommand onExit = onExit();
        if (onExit != null) {
            onExit.execute(context);
        }
    }

    /**
     * Returns the value by node key
     *
     * @param context the processor context
     * @param key     the node key
     * @param <T>     the type of value
     */
    public <T> T get(ProcessorContextApi context, String key) {
        return context.get(format("%s.%s", id(), key));
    }

    /**
     * Returns the value by node key
     *
     * @param context      the processor context
     * @param key          the node key
     * @param defaultValue the default value
     * @param <T>          the type of value
     */
    public <T> T get(ProcessorContextApi context, String key, T defaultValue) {
        return context.get(format("%s.%s", id(), key), defaultValue);
    }

    /**
     * Returns the double value by node key or 0 if not exits
     *
     * @param context the processor context
     * @param key     the node key
     */
    public double getDouble(ProcessorContextApi context, String key) {
        return context.getDouble(format("%s.%s", id(), key));
    }

    /**
     * Returns the double value by node key or 0 if not exits
     *
     * @param context      the processor context
     * @param key          the node key
     * @param defaultValue the default value
     */
    public double getDouble(ProcessorContextApi context, String key, double defaultValue) {
        return context.getDouble(format("%s.%s", id(), key), defaultValue);
    }

    /**
     * Returns the int value by node key or 0 if not exits
     *
     * @param context the processor context
     * @param key     the node key
     */
    public int getInt(ProcessorContextApi context, String key) {
        return context.getInt(format("%s.%s", id(), key));
    }

    /**
     * Returns the int value by node key or 0 if not exits
     *
     * @param context      the processor context
     * @param key          the node key
     * @param defaultValue the default value
     */
    public int getInt(ProcessorContextApi context, String key, int defaultValue) {
        return context.getInt(format("%s.%s", id(), key), defaultValue);
    }

    /**
     * Returns the long value by node key or 0 if not exits
     *
     * @param context the processor context
     * @param key     the node key
     */
    public long getLong(ProcessorContextApi context, String key) {
        return context.getLong(format("%s.%s", id(), key));
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void init(ProcessorContextApi context) {
        ProcessorCommand onInit = onInit();
        if (onInit != null) {
            onInit.execute(context);
        }
    }

    /**
     * Returns the entry command or null if none
     */
    public ProcessorCommand onEntry() {
        return onEntry;
    }

    /**
     * Returns the exit command or null if none
     */
    public ProcessorCommand onExit() {
        return onExit;
    }

    /**
     * Returns the initialisation command or null if none
     */
    public ProcessorCommand onInit() {
        return onInit;
    }

    /**
     * Put a value in the context prefixed by node id
     *
     * @param context the processor context
     * @param key     the key
     * @param value   the value
     */
    public void put(ProcessorContextApi context, String key, Object value) {
        context.put(format("%s.%s", id(), key), value);
    }

    /**
     * Remove a value in the context prefixed by node id
     *
     * @param context the processor context
     * @param key     the key
     */
    public void remove(ProcessorContextApi context, String key) {
        context.remove(format("%s.%s", id(), key));
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        RobotStatus status = context.worldModel().robotStatus();
        return !status.canMoveForward()
                ? !status.canMoveBackward()
                ? StateNode.BLOCKED_RESULT : StateNode.FRONT_BLOCKED_RESULT
                : !status.canMoveBackward()
                ? StateNode.REAR_BLOCKED_RESULT : null;
    }
}
