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

import static java.lang.Math.*;

/**
 * Converts action signals to robot commands and vice versa
 */
public class DLCircularActionFunction {
    public static final String NUM_ROTATIONS_ID = "numRotations";
    public static final String NUM_HEAD_ROTATIONS_ID = "numHeadRotations";
    public static final String HIDE_RADIUS_ID = "hideRadius";
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/action-func-circular-schema-0.1";
    public static final int DEFAULT_NUM_ROTATIONS = 36;
    public static final int DEFAULT_NUM_HEAD_ROTATIONS = 13;
    public static final double DEFAULT_GRID_STEP = 0.2;
    public static final double DEFAULT_HIDE_RADIUS = 0.2;
    public static final String GRID_STEP_ID = "gridStep";
    public static final Point2D ORIGIN = new Point2D.Double();
    private static final Logger logger = LoggerFactory.getLogger(DLCircularActionFunction.class);

    /**
     * Add specular points to map
     *
     * @param map        the map
     * @param p          the point
     * @param hideRadius the hide radius (m)
     */
    private static void addPoints(List<Point2D> map, Point2D p, double hideRadius) {
        if (p.distance(ORIGIN) > hideRadius) {
            map.add(p);
            if (p.getX() > 0) {
                map.add(new Point2D.Double(-p.getX(), p.getY()));
            }
            if (p.getY() > 0) {
                map.add(new Point2D.Double(p.getX(), -p.getY()));
                if (p.getX() > 0) {
                    map.add(new Point2D.Double(-p.getX(), -p.getY()));
                }
            }
        }
    }

    /**
     * Returns the deep learning circular action function
     *
     * @param numRotations     the number of robot rotations
     * @param numHeadRotations the number of head rotations
     * @param gridStep         the grid step (m)
     * @param hideRadius       the hide radius (m)
     * @param layers           the layer parameters as array of [size, distance]
     */
    private static DLActionFunction create(int numRotations, int numHeadRotations, double gridStep, double hideRadius, int[][] layers) {
        List<Point2D> indicesMap = createIndicesMap(gridStep, hideRadius, layers);
        logger.atDebug().log("DLCircularActionFunction created");
        return DLActionFunction.create(numRotations, numHeadRotations, indicesMap);
    }

    /**
     * Returns the action function from a JSON doc
     *
     * @param root    the root JSON doc
     * @param locator the locator
     */
    public static DLActionFunction create(JsonNode root, Locator locator) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int numRotations = locator.path(NUM_ROTATIONS_ID).getNode(root).asInt(DEFAULT_NUM_ROTATIONS);
        int numHeadRotations = locator.path(NUM_HEAD_ROTATIONS_ID).getNode(root).asInt(DEFAULT_NUM_HEAD_ROTATIONS);
        double gridStep = locator.path(GRID_STEP_ID).getNode(root).asDouble(DEFAULT_GRID_STEP);
        double hideRadius = locator.path(HIDE_RADIUS_ID).getNode(root).asDouble(DEFAULT_HIDE_RADIUS);
        int[][] layers = locator.path("layers").elements(root)
                .map(l -> {
                    int distance = l.path("distance").getNode(root).asInt();
                    int size = l.path("size").getNode(root).asInt();
                    return new int[]{size, distance};
                }).toArray(int[][]::new);
        return create(numRotations, numHeadRotations, gridStep, hideRadius, layers);
    }

    /**
     * Returns the map of action indices to move action target index (coordinates)
     * It maps the action indices to the move action target indices hiding the area
     * of hidden radius around the centre
     *
     * @param gridStep   the grid step (m)
     * @param hideRadius the hidden radius (m)
     * @param layers     the layer parameters as array of [size, distance]
     */
    private static List<Point2D> createIndicesMap(double gridStep, double hideRadius, int[][] layers) {
        List<Point2D> map = new ArrayList<>();
        int r = 0;
        addPoints(map, ORIGIN, hideRadius);
        for (int[] layer : layers) {
            int n = layer[0]; // Number of layers
            int h = layer[1]; // Distance of step
            r = (r + h) / h * h;
            for (int j = 0; j < n; j++) {
                // Compute number of sectors
                int m = toIntExact(round(r * PI / 2 / h));
                for (int k = 0; k < m; k++) {
                    double angle = k * PI / 2 / m;
                    Point2D p = new Point2D.Double(cos(angle) * r * gridStep, sin(angle) * r * gridStep);
                    addPoints(map, p, hideRadius);
                }
                Point2D p = new Point2D.Double(0, r * gridStep);
                addPoints(map, p, hideRadius);
                r += h;
            }
        }
        return map;
    }
}
