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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.List;
import java.util.stream.Collectors;

import static org.mmarini.yaml.Utils.DYNAMIC_OBJECT;
import static org.mmarini.yaml.Utils.createObject;

/**
 * The state machine node generates a simple behavior of robot
 */
public interface StateNode {
    String TIMEOUT_EXIT = "timeout";
    String BLOCKED_EXIT = "blocked";
    String COMPLETED_EXIT = "completed";
    String NONE_EXIT = "none";
    Tuple2<String, RobotCommands> TIMEOUT_RESULT = Tuple2.of(TIMEOUT_EXIT, RobotCommands.idle());
    Tuple2<String, RobotCommands> BLOCKED_RESULT = Tuple2.of(BLOCKED_EXIT, RobotCommands.idle());
    Tuple2<String, RobotCommands> NONE_RESULT = Tuple2.of(NONE_EXIT, RobotCommands.none());
    Tuple2<String, RobotCommands> COMPLETED_RESULT = Tuple2.of(COMPLETED_EXIT, RobotCommands.idle());

    Validator STATE_NODES = Validator.objectAdditionalProperties(DYNAMIC_OBJECT);

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
        STATE_NODES.apply(locator).accept(root);
        return locator.propertyNames(root)
                .map(t -> createNode(root, t._2, t._1))
                .collect(Collectors.toList());
    }

    /**
     * Processes the entry of state node
     *
     * @param context the processor context
     */
    void entry(ProcessorContext context);


    /**
     * Processes the exit of state node
     *
     * @param context the processor context
     */
    void exit(ProcessorContext context);

    /**
     * Returns the elapsed time (ms) from state entry time
     *
     * @param context the processor context
     */
    long getElapsedTime(ProcessorContext context);

    /**
     * Returns the state entry time (ms)
     *
     * @param context the processor context
     */
    long getEntryTime(ProcessorContext context);

    String getId();

    /**
     * Initializes state node
     *
     * @param context the processor context
     */
    void init(ProcessorContext context);

    /**
     * Returns true if the state has timed out
     *
     * @param context the processor context
     */
    boolean isTimeout(ProcessorContext context);

    /**
     * Processes the interaction and returns the exit key and the robot command
     *
     * @param context the processor context
     */
    Tuple2<String, RobotCommands> step(ProcessorContext context);
}
