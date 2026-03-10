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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
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
     * Returns the safe direction of null if blocked
     *
     * @param status the robot status
     */
    private static Complex computeSafeDirection(RobotStatus status) {
        return status.canMoveForward()
                ? status.direction()
                : status.canMoveBackward()
                ? status.direction().opposite()
                : null;
    }

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
    private Point2D safePoint;
    private Point2D contactPoint;

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
     * Computes the reaction on block conditions
     *
     * @param context the context
     * @return the state result or null if no block condition
     */
    private StateResult computeReaction(ProcessorContextApi context) {
        RobotStatus status = context.worldModel().robotStatus();
        Complex direction = status.direction();
        Point2D contactPoint = status.contactPoint();
        if (!status.canMoveForward()) {
            // forward block condition
            if (status.canMoveBackward()) {
                // Backward movement available
                // Safe location at safe distance from contact location opposite the robot direction
                logger.atDebug().log("Avoid front contact at {}", contactPoint);
                // Robot can move backward
                // moves the robot backward to safety distance scanning for the front contacts
                Point2D target = direction.opposite().at(contactPoint, safeDistance);
                return new StateResult(NONE_EXIT, RobotCommands.backward(Complex.DEG0, target));
            } else {
                // Robot completely blocked
                // halt robot
                logger.atWarn().setMessage("{}: Robot blocked").addArgument(this::id).log();
                return StateNode.blockResult(context);
            }
        } else if (!status.canMoveBackward()) {
            // backward block condition
            // forward movement available
            // Safe location at safe distance from contact location to the robot direction
            logger.atDebug().log("Avoid rear contact at {}", contactPoint);
            // Robot can move backward
            // moves the robot backward to safety distance scanning for the front contacts
            Point2D target = direction.at(contactPoint, safeDistance);
            return new StateResult(NONE_EXIT, RobotCommands.forward(Complex.DEG0, target));
        } else {
            return null;
        }
    }

    /**
     * Returns the safe point
     *
     * @param context the context
     */
    private Point2D computeSafePoint(ProcessorContextApi context) {
        RobotStatus status = context.worldModel().robotStatus();
        Complex safeDirection = computeSafeDirection(status);
        if (safeDirection == null) {
            return null;
        }
        Point2D robotLocation = status.location();
        return safeDirection.at(robotLocation, safeDistance);
    }

    @Override
    public void entry(ProcessorContextApi context) {
        super.entry(context);
        RobotStatus status = context.worldModel().robotStatus();
        safePoint = computeSafePoint(context);
        contactPoint = status.location();
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
            safePoint = computeSafePoint(ctx);
            return new StateResult(NONE_EXIT,
                    status.canMoveForward()
                            ? RobotCommands.forward(Complex.DEG0, safePoint)
                            : RobotCommands.backward(Complex.DEG0, safePoint));
        }
        // No more contacts
        // Check for robot safe location
        double contactDistance = robotLocation.distance(contactPoint);
        if (contactDistance >= safeDistance) {
            // Robot at safe distance: halt at exit
            logger.atDebug().log("Avoided contact at {} m", contactDistance);
            return StateNode.completedResult(ctx);
        }
        // TODO check for safe point changed
        /*
        // Check for free point
        if (safePoint == null) {
            //  No safe point set: search for it
            Optional<Point2D> target = worldModel.radarMap().findSafeTarget(
                    robotLocation,
                    frontContact
                            ? contactDirection.opposite()
                            : contactDirection,
                    safeDistance + SAFE_DISTANCE_GAP, maxDistance);
            safePoint = target.orElse(null);
        }

         */

        return new StateResult(NONE_EXIT,
                status.canMoveForward()
                        ? RobotCommands.forward(Complex.DEG0, safePoint)
                        : RobotCommands.backward(Complex.DEG0, safePoint));
    }

    public StateResult stepOld(ProcessorContextApi ctx) {
        // Computes reaction on block conditions
        return computeReaction(ctx);

        /*
        if (result != null) {
            return result;
        }

        return result
        // No block conditions
        // Check for the escape direction
        if (contactDirection == null) {
            // Contact disappeared after entry and before step
            // robot in a safe location without movement
            logger.atDebug()
                    .setMessage("{}: safety without any contact")
                    .addArgument(this::id)
                    .log();
            return StateNode.completedResult(ctx);
        }

        // contact disappeared
        WorldModel worldModel = ctx.worldModel();
        RobotStatus status = worldModel.robotStatus();
        Point2D robotLocation = status.location();
        // Check for the robot at safety distance
        double contactDistance = contactPoint.distance(status.location());
        if (contactDistance >= safeDistance) {
            // Robot at safe distance: halt at exit
            logger.atDebug().log("Avoided contact at {} m", contactDistance);
            return StateNode.completedResult(ctx);
        }

        // Robot not at safety distance
        // Check for free point
        if (safePoint == null) {
            //  No safe point set: search for it
            Optional<Point2D> target = worldModel.radarMap().findSafeTarget(
                    robotLocation,
                    frontContact
                            ? contactDirection.opposite()
                            : contactDirection,
                    safeDistance + SAFE_DISTANCE_GAP, maxDistance);
            safePoint = target.orElse(null);
        }
        if (safePoint == null) {
            // no safe point found: move away
            Complex escapeDir = contactDirection;
            // Compute power
            escapeSpeed = frontContact
                    ? -speed : speed;
            logger.atDebug().log("Avoiding without safe point to {} DEG at {} pps", escapeDir, escapeSpeed);
            return new StateResult(NONE_EXIT, RobotCommandsOld.moveAndFrontScan(escapeDir, escapeSpeed));
        }
        if (frontContact) {
            // escape from front contact
            escapeDir = Complex.direction(safePoint, robotLocation);
            escapeSpeed = -speed;
            logger.atDebug().log("Avoiding front contact safe point to {} DEG at {} pps", escapeDir, escapeSpeed);
            return new StateResult(NONE_EXIT, RobotCommandsOld.moveAndFrontScan(escapeDir, escapeSpeed));
        }
            // escape from rear contact
            escapeDir = Complex.direction(robotLocation, safePoint);
            escapeSpeed = speed;
            logger.atDebug().log("Avoiding rear contact to {} DEG at {} pps", escapeDir, escapeSpeed);
        return new StateResult(NONE_EXIT, RobotCommandsOld.moveAndFrontScan(escapeDir, escapeSpeed));

         */
    }
}
