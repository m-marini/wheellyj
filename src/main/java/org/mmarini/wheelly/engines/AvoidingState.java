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
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static java.lang.String.format;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;
import static org.mmarini.yaml.schema.Validator.positiveNumber;

/**
 * Generates the behavior to avoid contact obstacle.
 * <p>
 * Moves the robot forward or backward depending the obstacle position.<br>
 * <code>completed</code> is generated at completion (no blocking signals).<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 */
public class AvoidingState extends AbstractStateNode {
    public static final String FREE_POINT = "freePoint";
    public static final String SAFE_DISTANCE = "safeDistance";
    public static final String ESCAPE_DIRECTION = "escapeDirection";
    public static final Validator STATE_SPEC = Validator.objectPropertiesRequired(Map.of(
                    "safeDistance", positiveNumber()
            ), List.of(
                    "safeDistance")
    );

    private static final Logger logger = LoggerFactory.getLogger(AvoidingState.class);

    public static AvoidingState create(JsonNode root, Locator locator, String id) {
        BASE_STATE_SPEC.apply(locator).accept(root);
        STATE_SPEC.apply(locator).accept(root);
        ProcessorCommand onInit = ProcessorCommand.concat(
                loadTimeout(root, locator, id),
                ProcessorCommand.put(id + "." + SAFE_DISTANCE, locator.path(SAFE_DISTANCE).getNode(root).doubleValue()),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));

        return new AvoidingState(id, onInit, onEntry, onExit);
    }

    static OptionalInt escapeDir(ProcessorContext context) {
        RobotStatus status = context.getRobotStatus();
        int robotDir = status.getDirection();
        return !status.canMoveForward() ?
                status.canMoveBackward() ?
                        OptionalInt.of(normalizeDegAngle(robotDir + 180))
                        : OptionalInt.empty()
                : OptionalInt.of(robotDir);
    }

    protected AvoidingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        super(id, onInit, onEntry, onExit);
    }

    @Override
    public void entry(ProcessorContext context) {
        super.entry(context);
        escapeDir(context).ifPresentOrElse(dir -> put(context, ESCAPE_DIRECTION, dir),
                () -> remove(context, ESCAPE_DIRECTION));
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
                // move robot backward
                int escapeDir = normalizeDegAngle(dir + 180);
                put(ctx, ESCAPE_DIRECTION, escapeDir);
                remove(ctx, FREE_POINT);
                logger.atDebug()
                        .setMessage("{}: move backward {} DEG")
                        .addArgument(this::getId)
                        .addArgument(dir)
                        .log();
                return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(dir, -MAX_PPS));
            }
            // Robot completely blocked
            // holt robot
            logger.atWarn().setMessage("{}: Robot blocked").addArgument(this::getId).log();
            return BLOCKED_RESULT;
        }
        if (!status.canMoveBackward()) {
            // Robot can move forward
            // move robot forward
            put(ctx, ESCAPE_DIRECTION, dir);
            remove(ctx, FREE_POINT);
            logger.atDebug()
                    .setMessage("{}: move forward {} DEG")
                    .addArgument(this::getId)
                    .addArgument(dir)
                    .log();
            return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(dir, MAX_PPS));
        }
        // Robot not blocked
        Point2D freePoint = get(ctx, FREE_POINT);
        int escapeDir = getInt(ctx, ESCAPE_DIRECTION);
        if (freePoint == null) {
            freePoint = ctx.getRobotStatus().getLocation();
            put(ctx, FREE_POINT, freePoint);
            logger.atDebug().setMessage("{}: escaping to {} DEG from {}")
                    .addArgument(this::getId)
                    .addArgument(escapeDir)
                    .addArgument(freePoint)
                    .log();
        }
        double distance = freePoint.distance(ctx.getRobotStatus().getLocation());
        double safeDistance = getDouble(ctx, SAFE_DISTANCE);
        if (distance >= safeDistance) {
            // Halt robot at exit
            logger.atDebug()
                    .setMessage("{}: safety at {} m")
                    .addArgument(this::getId)
                    .addArgument(() -> format("%.2f", distance))
                    .log();
            return COMPLETED_RESULT;
        }
        // move robot away
        return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(escapeDir, MAX_PPS));
    }
}
