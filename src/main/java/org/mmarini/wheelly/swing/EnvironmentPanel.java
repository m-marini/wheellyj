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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static java.lang.Math.toRadians;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The canvas with environment display
 */
public class EnvironmentPanel extends RadarPanel {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    private static final float DEFAULT_WORLD_SIZE = 11;
    private static final float DEFAULT_SCALE = 800 / DEFAULT_WORLD_SIZE;
    private static final float ROBOT_WIDTH = 0.18f;
    private static final float ROBOT_LENGTH = 0.26f;
    private static final float ROBOT_W_BEVEL = 0.06f;
    private static final float ROBOT_L_BEVEL = 0.05f;
    private static final float OBSTACLE_SIZE = 0.2f;
    private static final float SENSOR_LENGTH = 3f;

    private static final Color ROBOT_COLOR = new Color(255, 255, 0);
    private static final Color OBSTACLE_COLOR = new Color(255, 0, 0);
    private static final Color OBSTACLE_PHANTOM_COLOR = new Color(128, 128, 128);
    private static final Color HUD_BACKGROUND_COLOR = new Color(32, 32, 32);
    private static final Color SENSOR_COLOR = new Color(200, 0, 0);

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
    private static final int HUD_WIDTH = 200;

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

    private Shape obstacleShape;
    private Point2D robotLocation;
    private int robotDirection;
    private int sensorDirection;
    private Point2D obstacleLocation;
    private List<Point2D> obstacleMap;
    private int contacts;
    private float distance;
    private float reward;
    private boolean canMoveForward;
    private boolean canMoveBackward;
    private long time;
    private float timeRatio;
    private boolean hudAtRight;
    private boolean hudAtBottom;

    public EnvironmentPanel() {
        setFont(Font.decode("Monospaced"));
        setScale(DEFAULT_SCALE);
        robotLocation = new Point2D.Float();
        setObstacleSize(OBSTACLE_SIZE);
    }

    /**
     * Returns the base transformation to apply to the graphic to draw a shape in world coordinate
     */
    AffineTransform createBaseTransform() {
        Dimension size = getSize();
        AffineTransform result = AffineTransform.getTranslateInstance((float) size.width / 2, (float) size.height / 2);
        float scale = getScale();
        result.scale(scale, -scale);
        Point2D centerLocation = getCenterLocation();
        result.translate(-centerLocation.getX(), -centerLocation.getY());
        return result;
    }

    private void drawHUD(Graphics g) {
        Graphics2D g1 = (Graphics2D) g;
        AffineTransform tr = g1.getTransform();

        FontMetrics fm = getFontMetrics(getFont());
        int hudHeight = 6 * fm.getHeight();

        Dimension size = getSize();

        int xHud = hudAtRight ? size.width - HUD_WIDTH : 0;
        int yHud = hudAtBottom ? size.height - hudHeight : 0;

        g1.translate(xHud, yHud);
        g1.setColor(HUD_BACKGROUND_COLOR);
        g1.fillRect(0, 0, HUD_WIDTH, hudHeight);
        g1.setColor(getForeground());
        drawLine(g1, format("Time     %s %.1fx", strDate(time), timeRatio), 0, Color.GREEN);
        drawLine(g1, format("Reward   %.2f", reward), 1, Color.GREEN);
        drawLine(g1, format("Distance %.2f m", distance), 2, Color.GREEN);
        drawLine(g1, format("Contacts %x", contacts), 3, Color.GREEN);
        if (!canMoveForward) {
            drawLine(g1, "FORWARD  STOP", 4, Color.RED);
        }
        if (!canMoveBackward) {
            drawLine(g1, "BACKWARD STOP", 5, Color.RED);
        }
        g1.setTransform(tr);
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
            gr.fill(obstacleShape);
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

    public void setCanMoveBackward(boolean canMoveBackward) {
        this.canMoveBackward = canMoveBackward;
        repaint();
    }

    public void setCanMoveForward(boolean canMoveForward) {
        this.canMoveForward = canMoveForward;
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

    public void setObstacleSize(float obstacleSize) {
        this.obstacleShape = new Rectangle2D.Float(
                -obstacleSize / 2, -obstacleSize / 2,
                obstacleSize, obstacleSize);

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
        // compute hud position
        hudAtBottom = !hudAtBottom && robotLocation.getY() > DEFAULT_WORLD_SIZE / 6 || (!hudAtBottom || !(robotLocation.getY() < -DEFAULT_WORLD_SIZE / 6)) && hudAtBottom;
        hudAtRight = !hudAtRight && robotLocation.getX() < -DEFAULT_WORLD_SIZE / 6 || (!hudAtRight || !(robotLocation.getY() > DEFAULT_WORLD_SIZE / 6)) && hudAtRight;

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
