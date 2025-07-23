/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * Generates the behaviour to select the path to the nearest unknown sector
 * <p>
 * Exits are:
 * <ul>
 *  <li><code>completed</code> at selection of path</li>
 *  <li><code>notFound</code> if no labeled point</li>
 * </ul>
 * Return vales:
 * <ul>
 *  <li><code>path</code>the list of points</li>
 * </ul>
 *
 * </p>
 *
 * @param id      the state id
 * @param onInit  the on init command
 * @param onEntry the on entry command
 * @param onExit  the on exit command
 */
public record FindUnknownState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                               ProcessorCommand onExit) implements ExtendedStateNode {
    public static final String PATH = "path";
    private static final String NOT_FOUND = "notFound";
    private static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of(
            NOT_FOUND, RobotCommands.idle());
    private static final Logger logger = LoggerFactory.getLogger(FindUnknownState.class);

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static FindUnknownState create(JsonNode root, Locator locator, String id) {
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new FindUnknownState(id, onInit, onEntry, onExit);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        WorldModel worldModel = context.worldModel();
        RadarMap map = worldModel.radarMap();
        RobotStatus status = worldModel.robotStatus();

        List<Point2D> path = SectorsPathFinder.createUnknownTargets(map, status.location()).find();
        Tuple2<String, RobotCommands> result = getBlockResult(context);
        if (result != null) {
            return result;
        } else if (path.isEmpty()) {
            logger.atDebug().log("No path found");
            remove(context, PATH);
            return NOT_FOUND_RESULT;
        } else {
            put(context, PATH, path);
            logger.atDebug().log("Path found");
            return COMPLETED_RESULT;
        }
    }
}