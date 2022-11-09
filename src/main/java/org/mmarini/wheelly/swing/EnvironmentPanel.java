/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.swing;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.MapSector;
import org.mmarini.wheelly.apis.RadarMap;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.toRadians;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The canvas with environment display
 */
public class EnvironmentPanel extends JComponent {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    private static final float DEFAULT_WORLD_SIZE = 11;
    private static final float DEFAULT_SCALE = 800 / DEFAULT_WORLD_SIZE;
    private static final float GRID_SIZE = 1f;
    private static final float ROBOT_WIDTH = 0.18f;
    private static final float ROBOT_LENGTH = 0.26f;
    private static final float ROBOT_W_BEVEL = 0.06f;
    private static final float ROBOT_L_BEVEL = 0.05f;
    private static final float OBSTACLE_SIZE = 0.2f;
    private static final float SENSOR_LENGTH = 3f;

    private static final Color ROBOT_COLOR = new Color(255, 255, 0);
    private static final Color GRID_COLOR = new Color(50, 50, 50);
    private static final Color OBSTACLE_COLOR = new Color(255, 0, 0);
    private static final Color OBSTACLE_PHANTOM_COLOR = new Color(128, 128, 128);
    private static final Color HUD_BACKGROUND_COLOR = new Color(32, 32, 32);
    private static final Color SENSOR_COLOR = new Color(200, 0, 0);
    private static final Color EMPTY_COLOR = new Color(64, 64, 64, 128);
    private static final Color FILLED_COLOR = new Color(200, 0, 0, 128);

    private static final float[][] ROBOT_POINTS = {
            {ROBOT_WIDTH / 2 - ROBOT_W_BEVEL, ROBOT_LENGTH / 2},
            {ROBOT_WIDTH / 2, ROBOT_LENGTH / 2 - ROBOT_L_BEVEL},
            {ROBOT_WIDTH / 2, -ROBOT_LENGTH / 2},
            {-ROBOT_WIDTH / 2, -ROBOT_LENGTH / 2},
            {-ROBOT_WIDTH / 2, ROBOT_LENGTH / 2 - ROBOT_L_BEVEL},
            {-ROBOT_WIDTH / 2 + ROBOT_W_BEVEL, ROBOT_LENGTH / 2}
    };
    private static final Shape ROBOT_SHAPE = createShape(ROBOT_POINTS);
    private static final float[][] SENSOR_POINTS = {
            {0, 0},
            {0, SENSOR_LENGTH}
    };
    private static final Shape SENSOR_SHAPE = createShape(SENSOR_POINTS);
    private static final Shape GRID_SHAPE = createGridShape();
    private static final Shape OBSTACLE_SHAPE = new Rectangle2D.Float(
            -OBSTACLE_SIZE / 2, -OBSTACLE_SIZE / 2,
            OBSTACLE_SIZE, OBSTACLE_SIZE);
    private static final int HUD_WIDTH = 200;

    /**
     * Returns the transformation to draw in a world location
     *
     * @param location location in world coordinate
     */
    private static AffineTransform at(Point2D location) {
        return AffineTransform.getTranslateInstance(location.getX(), location.getY());
    }

    /**
     * Returns the transformation to draw the CW rotated shape in a world location
     *
     * @param location  location in world coordinate
     * @param direction direction in RAD
     */
    private static AffineTransform at(Point2D location, double direction) {
        AffineTransform tr = AffineTransform.getTranslateInstance(location.getX(), location.getY());
        tr.rotate(-direction);
        return tr;
    }

    /**
     * Returns the transformation to draw the CW rotated shape in a world location
     *
     * @param location  location in world coordinate
     * @param direction direction in DEG
     */
    private static AffineTransform at(Point2D location, int direction) {
        return at(location, toRadians(direction));
    }

    private static Shape createGridShape() {
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(0, -DEFAULT_WORLD_SIZE);
        shape.lineTo(0, DEFAULT_WORLD_SIZE);
        shape.moveTo(-DEFAULT_WORLD_SIZE, 0);
        shape.lineTo(DEFAULT_WORLD_SIZE, 0);
        for (float s = GRID_SIZE; s <= DEFAULT_WORLD_SIZE; s += GRID_SIZE) {
            shape.moveTo(s, -DEFAULT_WORLD_SIZE);
            shape.lineTo(s, DEFAULT_WORLD_SIZE);
            shape.moveTo(-s, -DEFAULT_WORLD_SIZE);
            shape.lineTo(-s, DEFAULT_WORLD_SIZE);
            shape.moveTo(-DEFAULT_WORLD_SIZE, s);
            shape.lineTo(DEFAULT_WORLD_SIZE, s);
            shape.moveTo(-DEFAULT_WORLD_SIZE, -s);
            shape.lineTo(DEFAULT_WORLD_SIZE, -s);
        }
        return shape;
    }

    private static Shape createShape(float[][] points) {
        Path2D.Float shape = new Path2D.Float();
        shape.moveTo(points[0][0], points[0][1]);
        for (int i = 1; i < points.length; i++) {
            shape.lineTo(points[i][0], points[i][1]);
        }
        return shape;
    }

    /**
     * Returns the string representation of a time interval
     *
     * @param interval the time interval in millis
     */
    private static String strDate(long interval) {
        long decSec = (interval + 50) / 100;
        long mm = decSec / 600;
        decSec %= 600;
        long hh = mm / 60;
        mm %= 60;
        long gg = hh / 24;
        hh %= 24;
        StringBuilder result = new StringBuilder();
        if (gg > 0) {
            result.append(gg).append("g ");
        }
        if (hh > 0 || gg > 0) {
            result.append(format("%02dh ", hh));
        }
        if (mm > 0 || hh > 0 || gg > 0) {
            result.append(format("%02d' ", mm));
        }
        result.append(format("%04.1f\" ", decSec / 10f));
        return result.toString();
    }

    private Point2D robotLocation;
    private int robotDirection;
    private int sensorDirection;
    private Point2D centerLocation;
    private float scale;
    private Point2D obstacleLocation;
    private List<Point2D> obstacleMap;
    private int contacts;
    private float distance;
    private float reward;
    private boolean canMoveForward;
    private boolean canMoveBacward;
    private long time;
    private float timeRatio;
    private List<Tuple2<Point2D, Color>> radarMap;

    public EnvironmentPanel() {
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        robotLocation = new Point2D.Float();
        scale = DEFAULT_SCALE;
        centerLocation = new Point2D.Float();
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        result.scale(scale, -scale);
        result.translate(-centerLocation.getX(), -centerLocation.getY());
        return result;
    }

    /**
     * Draws the grid
     *
     * @param gr the graphic context
     */
    private void drawGrid(Graphics2D gr) {
        gr.setStroke(BORDER_STROKE);
        gr.setColor(GRID_COLOR);
        gr.draw(GRID_SHAPE);
    }

    private void drawHUD(Graphics g) {
        g.setColor(HUD_BACKGROUND_COLOR);
        FontMetrics fm = getFontMetrics(getFont());
        g.fillRect(0, 0, HUD_WIDTH, 6 * fm.getHeight());
        g.setColor(getForeground());
        drawLine(g, format("Time     %s %.1fx", strDate(time), timeRatio), 0, Color.GREEN);
        drawLine(g, format("Reward   %.2f", reward), 1, Color.GREEN);
        drawLine(g, format("Distance %.2f m", distance), 2, Color.GREEN);
        drawLine(g, format("Contacts %x", contacts), 3, Color.GREEN);
        if (!canMoveForward) {
            drawLine(g, "FORWARD  STOP", 4, Color.RED);
        }
        if (!canMoveBacward) {
            drawLine(g, "BACKWARD STOP", 5, Color.RED);
        }
    }

    private void drawLine(Graphics g, String text, int row, Color color) {
        FontMetrics fm = getFontMetrics(getFont());
        int y = fm.getHeight() * (row + 1);
        g.setColor(color);
        g.drawString(text, 0, y);
    }

    /**
     * Draws the map
     *
     * @param gr the graphic context
     */
    private void drawMap(Graphics2D gr) {
        if (obstacleMap != null) {
            AffineTransform base = gr.getTransform();
            gr.setStroke(BORDER_STROKE);
            for (Point2D obstacle : obstacleMap) {
                gr.setTransform(base);
                drawObstacle(gr, obstacle, OBSTACLE_PHANTOM_COLOR);
            }
        }
    }

    /**
     * Draws an obstacle
     *
     * @param gr       the graphic context
     * @param location the location
     * @param color    the color
     */
    private void drawObstacle(Graphics2D gr, Point2D location, Color color) {
        if (location != null) {
            gr.transform(at(location));
            gr.setColor(color);
            gr.setStroke(BORDER_STROKE);
            gr.fill(OBSTACLE_SHAPE);
        }
    }

    private void drawRadarMap(Graphics2D gr) {
        if (radarMap != null) {
            AffineTransform base = gr.getTransform();
            gr.setStroke(BORDER_STROKE);
            for (Tuple2<Point2D, Color> t : radarMap) {
                gr.setTransform(base);
                drawObstacle(gr, t._1, t._2);
            }
        }
    }

    /**
     * Draws the robot
     *
     * @param gr the graphic context
     */
    private void drawRobot(Graphics2D gr) {
        gr.transform(at(robotLocation, robotDirection));
        gr.setColor(ROBOT_COLOR);
        gr.setStroke(BORDER_STROKE);
        gr.fill(ROBOT_SHAPE);
    }

    /**
     * Draws sensor
     *
     * @param gr the graphic context
     */
    private void drawSensor(Graphics2D gr) {
        int angle = robotDirection + sensorDirection;
        gr.transform(at(robotLocation, angle));
        gr.setColor(SENSOR_COLOR);
        gr.setStroke(BORDER_STROKE);
        gr.draw(SENSOR_SHAPE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = (Graphics2D) g.create();
        gr.transform(createBaseTransform());
        AffineTransform base = gr.getTransform();
        drawGrid(gr);

        gr.setTransform(base);
        drawMap(gr);

        gr.setTransform(base);
        drawSensor(gr);

        gr.setTransform(base);
        drawObstacle(gr, obstacleLocation, OBSTACLE_COLOR);

        gr.setTransform(base);
        drawRadarMap(gr);

        gr.setTransform(base);
        drawRobot(gr);

        drawHUD(g);
    }

    public void setCanMoveBacward(boolean canMoveBacward) {
        this.canMoveBacward = canMoveBacward;
        repaint();
    }

    public void setCanMoveForward(boolean canMoveForward) {
        this.canMoveForward = canMoveForward;
        repaint();
    }

    public void setCenterLocation(Point2D centerLocation) {
        this.centerLocation = requireNonNull(centerLocation);
        repaint();
    }

    public void setContacts(int contacts) {
        this.contacts = contacts;
        repaint();
    }

    public void setDistance(float distance) {
        this.distance = distance;
        repaint();
    }

    public void setObstacleLocation(Point2D obstacleLocation) {
        this.obstacleLocation = obstacleLocation;
        repaint();
    }

    public void setObstacleMap(List<Point2D> obstacleMap) {
        this.obstacleMap = obstacleMap;
        repaint();
    }

    public void setRadarMap(RadarMap radarMap) {
        if (radarMap != null) {
            this.radarMap = Arrays.stream(radarMap.getMap())
                    .filter(MapSector::isKnown)
                    .map(sector -> Tuple2.of(sector.getLocation(),
                            sector.isFilled() ? FILLED_COLOR : EMPTY_COLOR))
                    .collect(Collectors.toList());
        } else {
            this.radarMap = null;
        }
    }

    public void setReward(float reward) {
        this.reward = reward;
        repaint();
    }

    public void setRobotDirection(int robotDirection) {
        this.robotDirection = robotDirection;
        repaint();
    }

    void setRobotLocation(Point2D location) {
        this.robotLocation = requireNonNull(location);
        repaint();
    }

    public void setScale(float scale) {
        this.scale = scale;
        repaint();
    }

    public void setSensorDirection(int sensorDirection) {
        this.sensorDirection = sensorDirection;
        repaint();
    }

    public void setTime(long time) {
        this.time = time;
        repaint();
    }

    public void setTimeRatio(float timeRatio) {
        this.timeRatio = timeRatio;
    }
}
