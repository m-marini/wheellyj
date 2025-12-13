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

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;

/**
 * The circular obstacle
 *
 * @param centre the centre of obstacle
 * @param radius the radius of obstacle (m)
 * @param label  the obstacle label or null if not labelled
 */
public record Obstacle(Point2D centre, double radius, String label) {
    public static final double DEFAULT_OBSTACLE_RADIUS = 0.1;

    /**
     * Returns the obstacle
     *
     * @param x      the abscissa (m)
     * @param y      the ordinate (m)
     * @param radius the radius (m)
     * @param label  the obstacle label or null if not labelled
     */
    public static Obstacle create(double x, double y, double radius, String label) {
        return new Obstacle(new Point2D.Double(x, y), radius, label);
    }

    /**
     * Create the obstacle
     *
     * @param centre the centre
     * @param radius the radius (m)
     * @param label  the obstacle label or null if not labelled
     */
    public Obstacle(Point2D centre, double radius, String label) {
        this.centre = requireNonNull(centre);
        this.radius = radius;
        this.label = label;
    }
}
