/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;

import static org.mmarini.wheelly.apis.RobotSpec.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.engines.StateResult.*;

/**
 * Generates the behaviour to move robot through path
 * <p>
 * Turns the sensor front<br>
 * Moves through path locations.<br/>
 * <p>
 * Parameters are:
 * <ul>
 *     <li><code>timeout</code> the timeout interval (ms) </li>
 *     <li><code>path</code> the list of path locations </li>
 *     <li><code>power</code> the move power (pps) </li>
 *     <li><code>approachDistance</code> the approach distance (m) </li>
 * </ul>
 * </p>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>completed</code> is generated when the target is reached</li>
 *  <li><code>notFound</code> is generated when the path is not found or not free</li>
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>frontBlocked</code> is generated at contact sensors signals</li>
 *  <li><code>rearBlocked</code> is generated at contact sensors signals</li>
 *  <li><code>timeout</code> is generated at timeout</li>
 * </ul>
 * </p>
 * <p>
 * Variables are:
 * <ul>
 *  <li><code>targetIndex</code> integer of current target</li>
 * </ul>
 * </p>
 */
public class MovePathState extends TimeOutState {
    public static final String TIMEOUT_ID = "timeout";
    public static final String PATH_ID = "path";
    private static final Logger logger = LoggerFactory.getLogger(MovePathState.class);
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-move-path-schema-0.1";

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static MovePathState create(JsonNode root, Locator locator, String id) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        List<Point2D> path = locator.path(PATH_ID).elements(root)
                .<Point2D>map(pointLocator -> {
                    double[] coords = pointLocator.elements(root)
                            .mapToDouble(coordLoc ->
                                    coordLoc.getNode(root).asDouble())
                            .toArray();
                    return new Point2D.Double(coords[0], coords[1]);
                })
                .toList();
        return new MovePathState(id, onInit, onEntry, onExit, timeout, path.isEmpty() ? null : path);
    }

    private final List<Point2D> defaultPath;
    private int targetIndex;
    private List<Point2D> path;

    /**
     * Create the abstract node
     *
     * @param id               the node identifier
     * @param onInit           the initialisation command or null if none
     * @param onEntry          the entry command or null if none
     * @param onExit           the exit command or null if none
     * @param timeout          the timeout (ms)
     * @param defaultPath      the default path
     */
    public MovePathState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout, List<Point2D> defaultPath) {
        super(id, onInit, onEntry, onExit, timeout);
        this.defaultPath = defaultPath;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        super.entry(context);
        targetIndex = 0;
        path = get(context, PATH_ID, defaultPath);
        context.path(path);
        if (path != null && !path.isEmpty()) {
            context.target(path.getLast());
        }
    }

    /**
     * Moves robot to current path location
     *
     * @param context the context
     */
    private StateResult move(ProcessorContextApi context) {
        if (path == null) {
            context.path(null).target(null);
            return notFound();
        }
        WorldModel worldModel = context.worldModel();
        RobotStatus robotStatus = worldModel.robotStatus();
        Point2D robotLocation = robotStatus.location();
        Point2D target = path.get(targetIndex);
        double distance = robotLocation.distance(target);
        logger.atDebug().log("Target distance {}", distance);
        if (distance <= robotStatus.robotSpec().targetRange() + DISTANCE_PER_PULSE) {
            // Target reached goto next trajectory target
            logger.atDebug().log("Target reached");
            return nextLocation(context);
        }
        return new StateResult(NONE_EXIT, RobotCommands.forward(target));
    }

    /**
     * Sets the next location
     *
     * @param context the context
     */
    private StateResult nextLocation(ProcessorContextApi context) {
        // Check for newxt target
        if (++targetIndex >= path.size()) {
            // path completed
            context.path(null).target(null);
            logger.atDebug().log("Completed");
            return completed();
        }
        Point2D target = path.get(targetIndex);
        logger.atDebug().log("Move to {}", target);
        return new StateResult(NONE_EXIT, RobotCommands.forward(target));
    }

    @Override
    public StateResult step(ProcessorContextApi context) {
        StateResult result = super.step(context);
        return result != null
                // Halt the robot and move forward the sensor at block
                ? result
                : move(context);

    }
}
