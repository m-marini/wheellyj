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
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Map;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;

/**
 * Generates the behavior to select the nearest label point
 * Parameters are:
 * <ul>
 *     <li><code>maxDistance</code> the maximum distance (m)</li>
 *     <li><code>safetyDistance</code> the safety distance (m)</li>
 * </ul>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>completed</code> at selection of point</li>
 *  <li><code>notFound</code> if no labeled point</li>
 * </ul>
 * </p>
 */
public record LabelPointState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                              ProcessorCommand onExit) implements ExtendedStateNode {
    public static final double MARGIN_DISTANCE = 0.1;
    private static final Logger logger = LoggerFactory.getLogger(LabelPointState.class);
    private static final String NOT_FOUND = "notFound";
    private static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of(
            NOT_FOUND, RobotCommands.idle());

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static LabelPointState create(JsonNode root, Locator locator, String id) {
        double maxDistance = locator.path("maxDistance").getNode(root).asDouble();
        double safeDistance = locator.path("safeDistance").getNode(root).asDouble();
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + ".maxDistance", maxDistance,
                        id + ".safeDistance", safeDistance
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new LabelPointState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the exploring state
     *
     * @param id      the identifier
     * @param onInit  the initialize command
     * @param onEntry the entry command
     * @param onExit  the exit command
     */
    public LabelPointState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        double safeDistance = getDouble(context, "safeDistance");
        WorldModel worldModel = context.worldModel();
        PolarMap polarMap = worldModel.polarMap();
        Point2D target = worldModel.markers()
                .values()
                .stream()
                .min((a, b) -> 0)
                .map(LabelMarker::location)
                .orElse(null);

        if (target == null) {
            logger.atDebug().log("No target found");
            remove(context, "target");
            return NOT_FOUND_RESULT;
        } else {
            Point2D center = polarMap.center();
            // Compute the safe target
            Complex dir = Complex.direction(center, target);
            Point2D nearTarget = dir.opposite().at(target, safeDistance + MARGIN_DISTANCE);
            int targetDir = dir.toIntDeg();
            put(context, "target", nearTarget);
            put(context, "direction", targetDir);
            logger.atDebug().log("Target {} {} DEG at {}cm from label",
                    nearTarget, targetDir,
                    round(target.distance(nearTarget) * 100));
            return COMPLETED_RESULT;
        }
    }
}
