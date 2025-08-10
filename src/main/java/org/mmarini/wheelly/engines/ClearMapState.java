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
 * Generates the behavior to clear map
 * <p>
 * Clear the map.<br>
 * <code>completed</code> is generated at completion
 * </p>
 */

public class ClearMapState extends AbstractStateNode {

    private static final Logger logger = LoggerFactory.getLogger(ClearMapState.class);
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-clear-map-schema-0.1";

    /**
     * Returns the haltCommand state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of haltCommand sensor
     * @param id      the status identifier
     */
    public static ClearMapState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        ProcessorCommand onInit = ProcessorCommand.concat(ProcessorCommand.create(root, locator.path("onInit")));
        return new ClearMapState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the haltCommand state
     *
     * @param id      the identifier
     * @param onInit  the initialization command
     * @param onEntry the entry command
     * @param onExit  eht exit command
     */
    public ClearMapState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        super(id, onInit, onEntry, onExit);
        logger.atDebug().log("Created");
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi ctx) {
        // Clear the map
        logger.atDebug().log("Clearing map ...");
        ctx.clearMap();
        Tuple2<String, RobotCommands> result = super.step(ctx);
        if (result == null) {
            result = COMPLETED_RESULT;
        }
        return result;
    }
}
