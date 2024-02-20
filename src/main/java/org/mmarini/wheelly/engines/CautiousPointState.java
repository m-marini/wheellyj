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
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Generates the behavior to select the cautious point
 * Parameters are:
 * <ul>
 *     <li><code>maxDistance</code> the maximum distance (m)</li>
 * </ul>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>completed</code> at selection of point</li>
 * </ul>
 * </p>
 */
public record CautiousPointState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                                 ProcessorCommand onExit) implements ExtendedStateNode {
    private static final Logger logger = LoggerFactory.getLogger(CautiousPointState.class);

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static CautiousPointState create(JsonNode root, Locator locator, String id) {
        double maxDistance = locator.path("maxDistance").getNode(root).asDouble();
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + ".maxDistance", maxDistance
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new CautiousPointState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the exploring state
     *
     * @param id      the identifier
     * @param onInit  the initialize command
     * @param onEntry the entry command
     * @param onExit  the exit command
     */
    public CautiousPointState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContext context) {
        double maxDistance = getDouble(context, "maxDistance");
        PolarMap polarMap = context.polarMap();
        Point2D target = polarMap.safeCentroid(maxDistance);

        logger.atDebug().log("Target {}", target);
        put(context, "target", target);
        return COMPLETED_RESULT;
    }
}
