/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts action signals to robot commands and vice versa
 */
public class DLMapActionFunction {
    public static final String NUM_ROTATIONS_ID = "numRotations";
    public static final String NUM_HEAD_ROTATIONS_ID = "numHeadRotations";
    public static final String GRID_SIZE_ID = "gridSize";
    public static final String GRID_STEP_ID = "gridStep";
    public static final String HIDE_RADIUS_ID = "hideRadius";
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/action-func-map-schema-0.1";
    public static final int DEFAULT_NUM_ROTATIONS = 36;
    public static final int DEFAULT_NUM_HEAD_ROTATIONS = 13;
    public static final int DEFAULT_GRID_SIZE = 31;
    public static final double DEFAULT_GRID_STEP = 0.2;
    public static final double DEFAULT_HIDE_RADIUS = 0.2;
    private static final Logger logger = LoggerFactory.getLogger(DLMapActionFunction.class);

    /**
     * Returns the rl actin function from a JSON doc
     *
     * @param root    the root JSON doc
     * @param locator the locator
     */
    public static DLActionFunction create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int numRotations = locator.path(NUM_ROTATIONS_ID).getNode(root).asInt(DEFAULT_NUM_ROTATIONS);
        int numHeadRotation = locator.path(NUM_HEAD_ROTATIONS_ID).getNode(root).asInt(DEFAULT_NUM_HEAD_ROTATIONS);
        int gridSize = locator.path(GRID_SIZE_ID).getNode(root).asInt(DEFAULT_GRID_SIZE);
        double gridStep = locator.path(GRID_STEP_ID).getNode(root).asDouble(DEFAULT_GRID_STEP);
        double hideRadius = locator.path(HIDE_RADIUS_ID).getNode(root).asDouble(DEFAULT_HIDE_RADIUS);
        return create(numRotations, numHeadRotation, gridSize, gridStep, hideRadius);
    }

    /**
     * Returns the deep learning action function
     *
     * @param numRotations     the number of robot rotations
     * @param numHeadRotations the number of head rotations
     * @param gridSize         the grid size (number of points along dimensions)
     * @param gridStep         the grid step (m)
     * @param hideRadius       the hide radius (m)
     */
    public static DLActionFunction create(int numRotations, int numHeadRotations, int gridSize, double gridStep, double hideRadius) {
        List<Point2D> indicesMap = createIndicesMap(gridSize, gridStep, hideRadius);
        logger.atDebug().log("Action function created");
        return DLActionFunction.create(numRotations, numHeadRotations, indicesMap);
    }

    /**
     * Returns the map of action indices to move action target index (coordinates)
     * It maps the action indices to the move action target indices hiding the area
     * of hidden radius around the centre
     *
     * @param gridSize   the grid size (number of points along dimensions)
     * @param gridStep   the grid step (m)
     * @param hideRadius the hidden radius (m)
     */
    static List<Point2D> createIndicesMap(int gridSize, double gridStep, double hideRadius) {
        List<Point2D> map = new ArrayList<>();
        int o = (gridSize - 1) / 2;
        double hideRadius2 = hideRadius * hideRadius;
        for (int i = 0; i < gridSize; i++) {
            double y = (i - o) * gridStep;
            for (int j = 0; j < gridSize; j++) {
                double x = (j - o) * gridStep;
                if (x * x + y * y > hideRadius2) {
                    map.add(new Point2D.Double(x, y));
                }
            }
        }
        return map;
    }
}
