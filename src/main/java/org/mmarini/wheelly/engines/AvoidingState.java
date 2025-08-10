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
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Optional;

import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;

/**
 * Generates the behaviour to avoid the contact obstacle.
 * <p>
 * Moves the robot forward or backward depending the obstacle position.<br>
 * <code>completed</code> is generated at completion (no blocking signals).<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 */
public class AvoidingState extends TimeOutState {

    public static final String SAFE_DISTANCE_ID = "safeDistance";
    public static final String SPEED_ID = "speed";
    public static final double SAFE_DISTANCE_GAP = 0.2;
    public static final String MAX_DISTANCE_ID = "MAX_DISTANCE";
    private static final Logger logger = LoggerFactory.getLogger(AvoidingState.class);
    private static final double DEFAULT_SAFE_DISTANCE = 0.3;
    private static final double DEFAULT_MAX_DISTANCE = 1;
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-avoid-schema-0.1";

    public static AvoidingState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double safeDistance = locator.path(SAFE_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SAFE_DISTANCE);
        double maxDistance = locator.path(MAX_DISTANCE_ID).getNode(root).asDouble(DEFAULT_MAX_DISTANCE);
        int speed = locator.path(SPEED_ID).getNode(root).asInt(MAX_PPS);
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));

        return new AvoidingState(id, onInit, onEntry, onExit, timeout, safeDistance, maxDistance, speed);
    }

    private final double safeDistance;
    private final double maxDistance;
    private final int speed;
    private Point2D contactPoint;
    private Point2D safePoint;
    private Complex contactDirection;
    private boolean frontContact;

    /**
     * Creates the abstract node
     *
     * @param id           the state identifier
     * @param onInit       the init command
     * @param onEntry      the entry command
     * @param onExit       the exit command
     * @param timeout      the timeout (ms)
     * @param safeDistance the safety distance (m)
     * @param maxDistance  the maximum distance (m)
     * @param speed        the speed (pps)
     */
    protected AvoidingState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout, double safeDistance, double maxDistance, int speed) {
        super(id, onInit, onEntry, onExit, timeout);
        this.safeDistance = safeDistance;
        this.maxDistance = maxDistance;
        this.speed = speed;
    }

    /**
     * Computes the reaction
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> computeReaction(ProcessorContextApi context) {
        RobotStatus status = context.worldModel().robotStatus();
        Complex direction = status.direction();
        Point2D robotLocation = status.location();
        if (!status.canMoveForward()) {
            // Robot blocked forward
            contactPoint = robotLocation;
            logger.atDebug().log("Avoid front contact at {}", robotLocation);
            if (status.canMoveBackward()) {
                // Robot can move backward
                // Sets the escape direction the robot direction and backward speed
                // moves the robot backward
                contactDirection = direction;
                frontContact = true;
                logger.atDebug().log("Move {} DEG at {} pps", direction, -speed);
                return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(direction, -speed));
            } else {
                // Robot completely blocked
                // holt robot
                logger.atWarn().setMessage("{}: Robot blocked").addArgument(this::id).log();
                return BLOCKED_RESULT;

            }
        } else if (!status.canMoveBackward()) {
            // Robot can move forward
            // move robot forward
            contactPoint = robotLocation;
            logger.atDebug().log("Avoid rear contact at {}", robotLocation);
            contactDirection = direction;
            frontContact = false;
            logger.atDebug().log("Move {} DEG at {} pps", direction, speed);
            return Tuple2.of(NONE_EXIT, RobotCommands.moveAndFrontScan(direction, speed));
        } else {
            return null;
        }
    }

    @Override
    public void entry(ProcessorContextApi context) {
        super.entry(context);
        contactDirection = null;
        contactPoint = null;
        safePoint = null;
        computeReaction(context);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi ctx) {
        Tuple2<String, RobotCommands> result = super.step(ctx);
        if (result != null && TIMEOUT_RESULT._1.equals(result._1)) {
            return result;
        }
        result = computeReaction(ctx);
        if (result != null) {
            return result;
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
            return COMPLETED_RESULT;
        }
        // Check for the escape direction
        if (contactDirection == null) {
            // robot in safe location without movement
            logger.atDebug()
                    .setMessage("{}: safety without any contact")
                    .addArgument(this::id)
                    .log();
            return COMPLETED_RESULT;
        }

        // Check for free point
        if (safePoint == null) {
            //  No free point set: search for it
            Optional<Point2D> target = worldModel.radarMap().findSafeTarget(
                    robotLocation,
                    frontContact
                            ? contactDirection.opposite()
                            : contactDirection,
                    safeDistance + SAFE_DISTANCE_GAP, maxDistance);
            safePoint = target.orElse(null);
        }

        Complex escapeDir;
        int escapeSpeed;
        if (safePoint == null) {
            // no free point found: move away
            escapeDir = contactDirection;
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
    }
}
