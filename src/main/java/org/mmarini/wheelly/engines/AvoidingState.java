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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates the behavior to avoid contact obstacle.
 * <p>
 * Moves the robot forward or backward depending the obstacle position.<br>
 * <code>completed</code> is generated at completion (no blocking signals).<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 */
public class AvoidingState extends AbstractStateNode {
    private static final Logger logger = LoggerFactory.getLogger(AvoidingState.class);

    public static AvoidingState create(JsonNode root, Locator locator, String id) {
        BASE_STATE_SPEC.apply(locator).accept(root);
        ProcessorCommand onInit = ProcessorCommand.concat(
                loadTimeout(root, locator, id),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new AvoidingState(id, onInit, onEntry, onExit);
    }

    protected AvoidingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        super(id, onInit, onEntry, onExit);
    }

    @Override
    public void entry(ProcessorContext context) {
        super.entry(context);
        context.haltRobot();
        context.moveSensor(0);
    }

    @Override
    public String step(ProcessorContext ctx) {
        if (!isBlocked(ctx)) {
            // Halt robot at exit
            return COMPLETED_EXIT;
        }
        if (isTimeout(ctx)) {
            // Halt robot at timeout
            ctx.haltRobot();
            return TIMEOUT_EXIT;
        }
        RobotStatus status = ctx.getRobotStatus();
        int dir = status.getDirection();
        if (status.canMoveForward()) {
            // move robot forward
            ctx.moveRobot(dir, 1F);
            return null;
        }
        if (status.canMoveBackward()) {
            // move robot backward
            ctx.moveRobot(dir, -1F);
            return null;
        }
        // holt robot if blocked
        ctx.haltRobot();
        logger.warn("{}: Robot blocked", getId());
        return null;
    }
}
