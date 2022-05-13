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

import java.awt.*;
import java.awt.geom.*;

import static java.lang.Math.PI;
import static java.lang.Math.min;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.RobotController.WARN_DISTANCE;
import static org.mmarini.wheelly.swing.Dashboard.*;

/**
 * The global map shows the locations of obstacles and the robot asset
 */
public class GlobalMap extends TopographicMap {
    public static final double ROBOT_RIGHT_BACK_X = -0.1;
    public static final double ROBOT_RIGHT_BACK_Y = 0.1;
    public static final double ROBOT_CENTER_BACK_X = -0.05;
    public static final double ROBOT_LEFT_BACK_X = -0.1;
    public static final double ROBOT_LEFT_BACK_Y = -0.1;
    public static final Color ROBOT_COLOR = Color.BLUE;
    private static final double GRID_DISTANCE = 1f;
    private static final double ROBOT_HEAD_X = 0.2;
    private final Path2D shape;

    /**
     * Creates a global map
     */
    public GlobalMap() {
        Path2D shape = new Path2D.Double();
        shape.moveTo(ROBOT_HEAD_X, 0);
        shape.lineTo(ROBOT_RIGHT_BACK_X, ROBOT_RIGHT_BACK_Y);
        shape.lineTo(ROBOT_CENTER_BACK_X, 0);
        shape.lineTo(ROBOT_LEFT_BACK_X, ROBOT_LEFT_BACK_Y);
        shape.closePath();
        this.shape = shape;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Point2D offset = getOffset();
        double maxDistance = getMaxDistance();

        Graphics2D gr = (Graphics2D) g.create(0, 0, size.width, size.height);
        gr.translate(size.width / 2, size.height / 2);
        int minSize = min(size.width, size.height);
        double scale = minSize / maxDistance / 2;
        gr.scale(scale, scale);

        gr.rotate(-PI / 2);
        AffineTransform oldTr = gr.getTransform();
        gr.translate(-offset.getX(), -offset.getY());

        paintGrid(gr);
        paintMap(gr);
        gr.setTransform(oldTr);
        gr.rotate(getDirection());
        paintRobot(gr);
    }

    @Override
    protected void paintGrid(Graphics2D gr) {
        gr.setColor(GRID);
        gr.setStroke(THIN_STROKE);
        Point2D offset = getOffset();
        double maxDistance = getMaxDistance();
        double minX = offset.getX() - maxDistance;
        double maxX = offset.getX() + maxDistance;
        double minY = offset.getY() - maxDistance;
        double maxY = offset.getY() + maxDistance;

        for (double x = minX; x <= maxX; x += GRID_DISTANCE) {
            gr.draw(new Line2D.Double(x, minY, x, maxY));
        }
        for (double y = minY; y <= maxY; y += GRID_DISTANCE) {
            gr.draw(new Line2D.Double(minX, y, maxX, y));
        }

        gr.setColor(INFO_COLOR);
        gr.draw(
                new Ellipse2D.Double(-INFO_DISTANCE + offset.getX(), -INFO_DISTANCE + offset.getY(),
                        INFO_DISTANCE * 2, INFO_DISTANCE * 2)
        );

        gr.setColor(WARN_COLOR);
        gr.draw(
                new Ellipse2D.Double(-WARN_DISTANCE + offset.getX(), -WARN_DISTANCE + offset.getY(),
                        WARN_DISTANCE * 2, WARN_DISTANCE * 2)
        );

        gr.setColor(STOP_COLOR);
        gr.draw(
                new Ellipse2D.Double(-STOP_DISTANCE + offset.getX(), -STOP_DISTANCE + offset.getY(),
                        STOP_DISTANCE * 2, STOP_DISTANCE * 2)
        );
    }

    /**
     * Paints the robot icons
     *
     * @param gr the graphic environment
     */
    private void paintRobot(Graphics2D gr) {
        gr.setColor(ROBOT_COLOR);
        gr.fill(shape);
    }
}
