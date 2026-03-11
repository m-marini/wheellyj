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
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;

import static org.mmarini.wheelly.engines.StateResult.*;

/**
 * Generates the behaviour to avoid the contact obstacle.
 * <p>
 * Moves the robot forward or backward depending the obstacle position.<br>
 * <code>completed</code> is generated at completion (no blocking signals).<br>
 * <code>timeout</code> is generated at timeout.
 * <code>blocked</code> is generated blocking state (no move possible).<br>
 * </p>
 */
public class AvoidingState extends TimeOutState {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-avoid-schema-0.1";

    public static final String SAFE_DISTANCE_ID = "safeDistance";
    public static final String MAX_DISTANCE_ID = "maxDistance";
    public static final double DEFAULT_SAFE_DISTANCE = 0.3;
    public static final double DEFAULT_MAX_DISTANCE = 1;

    private static final Logger logger = LoggerFactory.getLogger(AvoidingState.class);

    /**
     * Returns the avoid state from JSON node
     *
     * @param root    the JSON node
     * @param locator the definition document locator
     * @param id      the node identifier
     */
    public static AvoidingState create(JsonNode root, Locator locator, String id) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double safeDistance = locator.path(SAFE_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SAFE_DISTANCE);
        double maxDistance = locator.path(MAX_DISTANCE_ID).getNode(root).asDouble(DEFAULT_MAX_DISTANCE);
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new AvoidingState(id, onInit, onEntry, onExit, timeout, safeDistance, maxDistance);
    }

    private final double safeDistance;
    private final double maxDistance;
    private Point2D contactPoint;
    private Complex contactDirection;
    private boolean forwardEscape;

    /**
     * Creates the abstract node
     *
     * @param id           the state identifier
     * @param onInit       the init command
     * @param onEntry      the entry command
     * @param onExit       the exit command
     * @param timeout      the timeout (ms)
     * @param safeDistance the safety distance (m)
     * @param maxDistance  the maximum distance for radar map search (m)
     */
    protected AvoidingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout, double safeDistance, double maxDistance) {
        super(id, onInit, onEntry, onExit, timeout);
        this.safeDistance = safeDistance;
        this.maxDistance = maxDistance;
    }

    /**
     * Returns the safe point when contact (opposite the robot direction)
     *
     * @param ctx the processor context
     */
    private Point2D computeContactSafePoint(ProcessorContextApi ctx) {
        return (forwardEscape ? contactDirection : contactDirection.opposite())
                .at(contactPoint, safeDistance
                        + ctx.worldModel().robotStatus().robotSpec().targetRange());
    }

    /**
     * Returns the safe point
     *
     * @param ctx the processor context
     */
    private Point2D computeSafePoint(ProcessorContextApi ctx) {
        // heck for
        // Check for free point
        //  No safe point set: search for it in the map
        WorldModel worldModel = ctx.worldModel();
        /*
        Point2D targetPoint = worldModel.radarMap().findSafeTarget(
                        contactPoint,
                        forwardEscape
                                ? contactDirection
                                : contactDirection.opposite(),
                        safeDistance + worldModel.robotStatus().robotSpec().targetRange(),
                        maxDistance)
                .orElse(null);

         */
        Point2D targetPoint = worldModel.radarMap().findSafeTarget(
                        contactPoint,
                        forwardEscape
                                ? contactDirection
                                : contactDirection.opposite(),
                        safeDistance + worldModel.robotStatus().robotSpec().targetRange(),
                        maxDistance)
                .orElse(null);


        return targetPoint != null
                ? targetPoint
                // no safe point in the map
                : computeContactSafePoint(ctx);
    }

    @Override
    public void entry(ProcessorContextApi context) {
        super.entry(context);
        RobotStatus status = context.worldModel().robotStatus();
        context.target(null);
        contactPoint = status.location();
        contactDirection = status.direction();
        forwardEscape = status.canMoveForward();
    }

    @Override
    public StateResult step(ProcessorContextApi ctx) {
        // Default behaviours for timeout or roboto completely blocked
        StateResult result = super.step(ctx);
        if (result != null &&
                (result.exitCode().equals(TIMEOUT_EXIT)
                        || result.exitCode().equals(BLOCKED_EXIT))) {
            return result;
        }
        // if exists any contacts compute and go to safe point
        RobotStatus status = ctx.worldModel().robotStatus();
        Point2D robotLocation = status.location();
        if (!status.canMoveBackward() || !status.canMoveForward()) {
            contactPoint = robotLocation;
            contactDirection = status.direction();
            forwardEscape = status.canMoveForward();
            Point2D targetPoint = computeContactSafePoint(ctx);
            ctx.target(targetPoint);
            return new StateResult(NONE_EXIT,
                    forwardEscape
                            ? RobotCommands.forward(Complex.DEG0, targetPoint)
                            : RobotCommands.backward(Complex.DEG0, targetPoint));
        }
        // No contacts
        // Check for robot safe location
        double contactDistance = robotLocation.distance(contactPoint);
        if (contactDistance >= safeDistance) {
            // Robot at safe distance: halt at exit
            logger.atDebug().log("Avoided contact at {} m", contactDistance);
            return StateNode.completedResult(ctx);
        }
        // Compute the new safe point
        Point2D targetPoint = computeSafePoint(ctx);
        ctx.target(targetPoint);
        return new StateResult(NONE_EXIT,
                forwardEscape
                        ? RobotCommands.forward(Complex.DEG0, targetPoint)
                        : RobotCommands.backward(Complex.DEG0, targetPoint));
    }
}
