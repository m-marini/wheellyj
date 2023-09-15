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

/**
 * Generates the behavior to halt the robot
 * <p>
 * Stops the robot and moves the sensor if required.<br>
 * <code>blocked</code> is generated at contact sensors signals.<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 */

public class HaltState extends AbstractStateNode {

    /**
     * Returns the halt state from configuration
     *
     * @param root    the configuration dcument
     * @param locator the locator of halt sensor
     * @param id      the status identifier
     */
    public static HaltState create(JsonNode root, Locator locator, String id) {
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        ProcessorCommand onInit = ProcessorCommand.concat(
                loadTimeout(root, locator, id),
                loadAutoScanOnInit(root, locator, id),
                ProcessorCommand.create(root, locator.path("onInit")));
        return new HaltState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the halt state
     *
     * @param id      the identifier
     * @param onInit  the initialization command
     * @param onEntry the entry command
     * @param onExit  eht exit command
     */
    protected HaltState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        super(id, onInit, onEntry, onExit);
    }

    @Override
    public void entry(ProcessorContext context) {
        super.entry(context);
        entryAutoScan(context);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContext ctx) {
        if (isTimeout(ctx)) {
            return TIMEOUT_RESULT;
        }
        if (isBlocked(ctx)) {
            return BLOCKED_RESULT;
        }
        return tickAutoScan(ctx);
    }
}
