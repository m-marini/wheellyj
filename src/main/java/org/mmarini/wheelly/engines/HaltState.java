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
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates the behaviour to haltCommand the robot
 * <p>
 * Stops the robot and moves the sensor if required.<br>
 * <code>blocked</code> is generated at contact sensors signals.<br>
 * <code>frontBlocked</code> is generated at contact sensors signals.<br>
 * <code>rearBlocked</code> is generated at contact sensors signals.<br>
 * <code>blocked</code> is generated at contact sensors signals.<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 */

public class HaltState extends TimeOutState {

    private static final Logger logger = LoggerFactory.getLogger(HaltState.class);
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-halt-schema-0.1";
    public static final String TIMEOUT_ID = "TIMEOUT";

    /**
     * Returns the haltCommand state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of haltCommand sensor
     * @param id      the status identifier
     */
    public static HaltState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong();
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        return new HaltState(id, onInit, onEntry, onExit, timeout);
    }

    /**
     * Creates the haltCommand state
     *
     * @param id      the identifier
     * @param onInit  the initialisation command
     * @param onEntry the entry command
     * @param onExit  the exit command
     * @param timeout the timeout (ms)
     */
    public HaltState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout) {
        super(id, onInit, onEntry, onExit, timeout);
        logger.atDebug().log("Created {}", id);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi ctx) {
        Tuple2<String, RobotCommands> result = super.step(ctx);
        if (result != null) {
            return result;
        }
        return NONE_HALT_RESULT;
    }
}
