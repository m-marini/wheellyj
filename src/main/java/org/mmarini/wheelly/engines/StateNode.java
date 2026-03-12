/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Locator;

import java.util.List;

import static org.mmarini.wheelly.engines.StateResult.*;
import static org.mmarini.yaml.Utils.createObject;

/**
 * The state machine node generates a simple behaviour of robot
 */
public interface StateNode {
    /**
     * Returns the block result
     *
     * @param context the process context
     */
    static StateResult blockResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? BLOCKED_NONE_RESULT : BLOCKED_RESULT;
    }

    /**
     * Returns the block result
     *
     * @param context the process context
     */
    static StateResult noneHaltResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? NONE_RESULT : NONE_HALT_RESULT;
    }

    /**
     * Returns the completed result
     *
     * @param context the process context
     */
    static StateResult completedResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? COMPLETED_NONE_RESULT : COMPLETED_RESULT;
    }

    /**
     * Returns the state node from yaml document
     *
     * @param root    the root document
     * @param locator the state node locator
     * @param id      the node id
     */
    static StateNode createNode(JsonNode root, Locator locator, String id) {
        return createObject(root, locator, new Object[]{id}, new Class[]{String.class});
    }

    /**
     * Returns the map of state nodes from yaml document
     *
     * @param root    the root document
     * @param locator the state node locator
     */
    static List<StateNode> createNodes(JsonNode root, Locator locator) {
        return locator.propertyNames(root)
                .mapToObj((key, value) -> createNode(root, value, key))
                .toList();
    }

    /**
     * Returns the front block result
     *
     * @param context the process context
     */
    static StateResult frontBlockResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? FRONT_BLOCKED_NONE_RESULT : FRONT_BLOCKED_RESULT;
    }

    /**
     * Returns the rear block result
     *
     * @param context the process context
     */
    static StateResult rearBlockResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? REAR_BLOCKED_NONE_RESULT : REAR_BLOCKED_RESULT;
    }

    /**
     * Returns the completed result
     *
     * @param context the process context
     */
    static StateResult timeoutResult(ProcessorContextApi context) {
        return context.worldModel().robotStatus().halt() ? TIMEOUT_NONE_RESULT : StateResult.TIMEOUT_RESULT;
    }

    /**
     * Returns the elapsed localTime (ms) from state entry localTime
     *
     * @param context the processor context
     */
    long elapsedTime(ProcessorContextApi context);

    /**
     * Processes the entry of state node
     *
     * @param context the processor context
     */
    void entry(ProcessorContextApi context);

    /**
     * Returns the state entry localTime (ms)
     *
     * @param context the processor context
     */
    long entryTime(ProcessorContextApi context);

    /**
     * Processes the exit of state node
     *
     * @param context the processor context
     */
    void exit(ProcessorContextApi context);

    /**
     * Returns the state identifier
     */
    String id();

    /**
     * Initializes state node
     *
     * @param context the processor context
     */
    void init(ProcessorContextApi context);

    /**
     * Processes the interaction and returns the exit key and the robot command
     *
     * @param context the processor context
     */
    StateResult step(ProcessorContextApi context);
}
