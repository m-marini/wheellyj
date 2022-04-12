/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import org.mmarini.Tuple2;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;

import static java.awt.Color.*;
import static java.lang.Math.*;
import static org.mmarini.wheelly.swing.Dashboard.*;

/**
 * The radar component shows the locations of obstacles relative to the robot asset
 */
public class Radar extends TopographicMap {
    private static final double GRID_DISTANCE = 1f;
    private static final double GRID_DISTANCE1 = 0.1f;

    /**
     * @param angle    the angle
     * @param distance the distance
     */
    private static Point2D computeLocation(int angle, double distance) {
        double rads = toRadians(angle);
        double x = cos(rads) * distance;
        double y = sin(rads) * distance;
        return new Point2D.Double(x, y);
    }

    /**
     * Creates the radar map
     */
    public Radar() {
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Point2D offset = getOffset();
        double maxDistance = getMaxDistance();
        double direction = getDirection();

        Graphics2D gr = (Graphics2D) g.create(0, 0, size.width, size.height);
        gr.translate(size.width / 2, size.height / 2);
        int minSize = min(size.width, size.height);
        double scale = minSize / maxDistance / 2;
        gr.scale(scale, scale);

        paintGrid(gr);
        gr.rotate(-PI / 2);
        gr.rotate(-direction);
        gr.translate(-offset.getX(), -offset.getY());
        paintMap(gr);
    }

    @Override
    protected void paintGrid(Graphics2D gr) {
        gr.setColor(GRID);
        gr.setStroke(new BasicStroke(0));
        double maxDistance = getMaxDistance();
        gr.draw(
                new Ellipse2D.Double(-maxDistance, -maxDistance, maxDistance * 2, maxDistance * 2)
        );

        for (double distance = GRID_DISTANCE1; distance <= maxDistance; distance += GRID_DISTANCE1) {
            Color color = distance > INFO_DISTANCE ? GRID
                    : distance > WARN_DISTANCE ? INFO_COLOR
                    : distance > STOP_DISTANCE ? WARN_COLOR : STOP_COLOR;
            gr.setColor(color);
            gr.draw(
                    new Ellipse2D.Double(-distance, -distance, distance * 2, distance * 2)
            );
        }
        gr.setColor(GRID);
        for (double distance = GRID_DISTANCE; distance <= maxDistance; distance += GRID_DISTANCE) {
            gr.draw(
                    new Ellipse2D.Double(-distance, -distance, distance * 2, distance * 2)
            );
        }


        for (int angle = -180; angle < 180; angle += 15) {
            gr.draw(new Line2D.Double(
                    computeLocation(angle, GRID_DISTANCE1),
                    computeLocation(angle, round(maxDistance)))
            );
        }
    }
}
