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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.OptionalInt;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * Generates the behavior to avoid contact obstacle.
 * <p>
 * Moves the robot forward or backward depending the obstacle position.<br>
 * <code>completed</code> is generated at completion (no blocking signals).<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 *
 * @param id      the state id
 * @param onInit  the on init command
 * @param onEntry the on entry command
 * @param onExit  the on exit command
 */
public record AvoidingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                            ProcessorCommand onExit) implements ExtendedStateNode {
    public static final String FREE_POINT = "freePoint";
    public static final String SAFE_DISTANCE = "safeDistance";
    public static final String ESCAPE_DIRECTION = "escapeDirection";

    private static final Logger logger = LoggerFactory.getLogger(AvoidingState.class);
    public static final String ESCAPE_SPEED = "escapeSpeed";

    public static AvoidingState create(JsonNode root, Locator locator, String id) {
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.put(id + "." + SAFE_DISTANCE, locator.path(SAFE_DISTANCE).getNode(root).doubleValue()),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));

        return new AvoidingState(id, onInit, onEntry, onExit);
    }

    public AvoidingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public void entry(ProcessorContext context) {
        ExtendedStateNode.super.entry(context);
        remove(context, ESCAPE_DIRECTION);
        remove(context, ESCAPE_SPEED);
        remove(context, FREE_POINT);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContext ctx) {
        if (isTimeout(ctx)) {
            return TIMEOUT_RESULT;
        }
        RobotStatus status = ctx.getRobotStatus();
        int dir = status.getDirection();
        if (!status.canMoveForward()) {
            // Robot blocked forward
            if (status.canMoveBackward()) {
                // Robot can move backward
                // Set escape direction the robot direction and backward speed
                // move robot backward
                put(ctx, ESCAPE_DIRECTION, dir);
                put(ctx, ESCAPE_SPEED, -MAX_PPS);
                remove(ctx, FREE_POINT);
                logger.atDebug()
                        .setMessage("{}: move backward {} DEG")
                        .addArgument(this::id)
                        .addArgument(dir)
                        .log();
                return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(dir, -MAX_PPS));
            }
            // Robot completely blocked
            // holt robot
            logger.atWarn().setMessage("{}: Robot blocked").addArgument(this::id).log();
            return BLOCKED_RESULT;
        }
        if (!status.canMoveBackward()) {
            // Robot can move forward
            // move robot forward
            put(ctx, ESCAPE_DIRECTION, dir);
            put(ctx, ESCAPE_SPEED, MAX_PPS);
            remove(ctx, FREE_POINT);
            logger.atDebug()
                    .setMessage("{}: move forward {} DEG")
                    .addArgument(this::id)
                    .addArgument(dir)
                    .log();
            return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(dir, MAX_PPS));
        }
        // Robot not blocked
        // Get escape direction and speed
        int escapeDir = getInt(ctx, ESCAPE_DIRECTION);
        int escapeSpeed = getInt(ctx, ESCAPE_SPEED);
        Point2D freePoint = get(ctx, FREE_POINT);
        // Check if free point has been set
        if (freePoint == null) {
            // Set the location as free point
            freePoint = ctx.getRobotStatus().getLocation();
            put(ctx, FREE_POINT, freePoint);
            logger.atDebug().setMessage("{}: escaping to {} DEG from {}")
                    .addArgument(this::id)
                    .addArgument(escapeDir)
                    .addArgument(freePoint)
                    .log();
        }

        // Check for robot at safe distance
        double distance = freePoint.distance(ctx.getRobotStatus().getLocation());
        double safeDistance = getDouble(ctx, SAFE_DISTANCE);
        if (distance >= safeDistance) {
            // Halt robot at exit
            logger.atDebug()
                    .setMessage("{}: safety at {} m")
                    .addArgument(this::id)
                    .addArgument(() -> format("%.2f", distance))
                    .log();
            return COMPLETED_RESULT;
        }
        // move robot away
        return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(escapeDir, escapeSpeed));
    }
}
