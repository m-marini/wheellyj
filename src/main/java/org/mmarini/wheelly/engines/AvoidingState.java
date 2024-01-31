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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;

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

    public static final String SAFE_POINT = "safePoint";
    public static final String SAFE_DISTANCE = "safeDistance";
    public static final String CONTACT_DIRECTION = "contactDirection";
    public static final String SPEED = "speed";
    public static final String CONTACT_POINT = "contactPoint";
    public static final String FRONT_CONTACT = "frontContact";
    public static final double SAFE_DISTANCE_GAP = 0.2;
    public static final String MAX_DISTANCE = "MAX_DISTANCE";
    private static final Logger logger = LoggerFactory.getLogger(AvoidingState.class);
    private static final double DEFAULT_SAFE_DISTANCE = 0.3;
    private static final double DEFAULT_MAX_DISTANCE = 1;

    public static AvoidingState create(JsonNode root, Locator locator, String id) {
        double safeDistance = locator.path(SAFE_DISTANCE).getNode(root).asDouble(DEFAULT_SAFE_DISTANCE);
        double maxDistance = locator.path(MAX_DISTANCE).getNode(root).asDouble(DEFAULT_MAX_DISTANCE);
        int speed = locator.path(SPEED).getNode(root).asInt(MAX_PPS);
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.put(id + "." + SAFE_DISTANCE, safeDistance),
                ProcessorCommand.put(id + "." + MAX_DISTANCE, maxDistance),
                ProcessorCommand.put(id + "." + SPEED, speed),
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

    /**
     * Computes the reaction
     *
     * @param context the context
     */
    private Optional<Tuple2<String, RobotCommands>> computeReaction(ProcessorContext context) {
        RobotStatus status = context.robotStatus();
        Complex direction = status.direction();
        int speed = getInt(context, SPEED);
        Point2D robotLocation = status.location();
        if (!status.canMoveForward()) {
            // Robot blocked forward
            put(context, CONTACT_POINT, robotLocation);
            logger.atDebug().log("Avoid front contact at {}", robotLocation);
            if (status.canMoveBackward()) {
                // Robot can move backward
                // Set escape direction the robot direction and backward speed
                // move robot backward
                put(context, CONTACT_DIRECTION, direction);
                put(context, FRONT_CONTACT, true);
                logger.atDebug().log("Move {} DEG at {} pps", direction, -speed);
                return Optional.of(Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(direction, -speed)));
            } else {
                // Robot completely blocked
                // holt robot
                logger.atWarn().setMessage("{}: Robot blocked").addArgument(this::id).log();
                return Optional.of(BLOCKED_RESULT);

            }
        } else if (!status.canMoveBackward()) {
            // Robot can move forward
            // move robot forward
            put(context, CONTACT_POINT, robotLocation);
            logger.atDebug().log("Avoid rear contact at {}", robotLocation);

            put(context, CONTACT_DIRECTION, direction);
            put(context, FRONT_CONTACT, false);
            logger.atDebug().log("Move {} DEG at {} pps", direction, speed);
            return Optional.of(Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(direction, speed)));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void entry(ProcessorContext context) {
        ExtendedStateNode.super.entry(context);
        remove(context, CONTACT_DIRECTION);
        remove(context, CONTACT_POINT);
        remove(context, SAFE_POINT);
        computeReaction(context);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContext ctx) {
        if (isTimeout(ctx)) {
            return TIMEOUT_RESULT;
        }
        return computeReaction(ctx).orElseGet(() -> {
            // contact disappeared
            RobotStatus status = ctx.robotStatus();
            Point2D robotLocation = status.location();
            Point2D contactPoint = get(ctx, CONTACT_POINT);
            double safeDistance = getDouble(ctx, SAFE_DISTANCE);
            // Check for robot at safe distance
            double contactDistance = contactPoint.distance(status.location());
            if (contactDistance >= safeDistance) {
                // Robot at safe distance: halt at exit
                logger.atDebug().log("Avoided contact at {} m", contactDistance);
                return COMPLETED_RESULT;
            }
            // Check for escape direction
            Complex contactsDir = get(ctx, CONTACT_DIRECTION);
            if (contactsDir == null) {
                // robot in safe location without movement
                logger.atDebug()
                        .setMessage("{}: safety without any contact")
                        .addArgument(this::id)
                        .log();
                return COMPLETED_RESULT;
            }

            // Check for free point
            boolean frontContact = this.<Boolean>get(ctx, FRONT_CONTACT);
            Point2D safePoint = get(ctx, SAFE_POINT);
            if (safePoint == null) {
                //  No free point set: search for it
                double maxDistance = getDouble(ctx, MAX_DISTANCE);
                Optional<Point2D> target = ctx.radarMap().findSafeTarget(
                        robotLocation,
                        frontContact
                                ? contactsDir.opposite()
                                : contactsDir,
                        safeDistance + SAFE_DISTANCE_GAP, maxDistance);
                target.ifPresent(p -> {
                    put(ctx, SAFE_POINT, p);
                    logger.atDebug().log("Safe point at {}", p);
                });
                safePoint = target.orElse(null);
            }

            Complex escapeDir;
            int escapeSpeed;
            int speed = getInt(ctx, SPEED);
            if (safePoint == null) {
                // no free point found: move away
                escapeDir = contactsDir;
                // Compute speed
                escapeSpeed = frontContact
                        ? -speed : speed;
                logger.atDebug().log("Avoiding without safe point to {} DEG at {} pps", escapeDir, escapeSpeed);
            } else if (frontContact) {
                // escape from front contact
                escapeDir = Complex.direction(safePoint, robotLocation);
                escapeSpeed = -speed;
                logger.atDebug().log("Avoiding front contact safe point to {} DEG at {} pps", escapeDir, escapeSpeed);
            } else {
                // escape from rear contact
                escapeDir = Complex.direction(robotLocation, safePoint);
                escapeSpeed = speed;
                logger.atDebug().log("Avoiding rear contact to {} DEG at {} pps", escapeDir, escapeSpeed);
            }
            return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(escapeDir, escapeSpeed));
        });
    }
}
