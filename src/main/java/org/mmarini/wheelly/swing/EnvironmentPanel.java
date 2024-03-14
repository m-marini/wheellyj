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
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotStatus;

import java.awt.*;
import java.awt.geom.*;
import java.util.List;

import static java.lang.String.format;

/**
 * The canvas with environment display
 */
public class EnvironmentPanel extends RadarPanel {
    public static final BasicStroke BORDER_STROKE = new BasicStroke(0);
    private static final float ROBOT_RADIUS = 0.15f;
    private static final float ROBOT_ARROW_X = 0.11f;
    private static final float ROBOT_ARROW_Y = 0.102f;
    private static final float ROBOT_ARROW_BACK = 0.05f;
    private static final float DEFAULT_WORLD_SIZE = 11;
    private static final float DEFAULT_SCALE = 800 / DEFAULT_WORLD_SIZE;
    private static final float OBSTACLE_SIZE = 0.2f;
    private static final float SENSOR_LENGTH = 3f;
    private static final int HUD_WIDTH = 200;
    private static final double PING_SIZE = 0.1;
    private static final Color ROBOT_COLOR = new Color(255, 255, 0);
    private static final Color OBSTACLE_PHANTOM_COLOR = new Color(128, 128, 128);
    private static final Color HUD_BACKGROUND_COLOR = new Color(32, 32, 32);
    private static final Color SENSOR_COLOR = new Color(200, 0, 0);
    private static final Color PING_COLOR = new Color(255, 192, 192);
    private static final float[][] ROBOT_POINTS = {
            {0, ROBOT_RADIUS},
            {-ROBOT_ARROW_X, -ROBOT_ARROW_Y},
            {0, -ROBOT_ARROW_BACK},
            {ROBOT_ARROW_X, -ROBOT_ARROW_Y}
    };
    private static final Shape ROBOT_SHAPE = createShape(ROBOT_POINTS);
    private static final Shape ROBOT_OUTER = new Ellipse2D.Double(-ROBOT_RADIUS, -ROBOT_RADIUS, ROBOT_RADIUS * 2, ROBOT_RADIUS * 2);
    private static final float[][] SENSOR_POINTS = {
            {0, 0},
            {0, SENSOR_LENGTH}
    };
    private static final Shape SENSOR_SHAPE = createShape(SENSOR_POINTS);
    private static final Shape PING_SHAPE = new Ellipse2D.Double(-PING_SIZE / 2, -PING_SIZE / 2, PING_SIZE, PING_SIZE);

    /**
     * Returns the transformation to draw the CW rotated shape in a world location
     *
     * @param location  location in world coordinate
     * @param direction direction
     */
    private static AffineTransform at(Point2D location, Complex direction) {
        AffineTransform tr = AffineTransform.getTranslateInstance(location.getX(), location.getY());
        tr.rotate(-direction.toRad());
        return tr;
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
     * Returns the string representation of a localTime interval
     *
     * @param interval the localTime interval in millis
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

    private boolean hudAtBottom;
    private boolean hudAtRight;
    private List<Point2D> obstacleMap;
    private Shape obstacleShape;
    private PanelData panelData;
    private double reactionRealTime;
    private double reactionRobotTime;
    private double reward;
    private double timeRatio;

    public EnvironmentPanel() {
        setFont(Font.decode("Monospaced"));
        setScale(DEFAULT_SCALE);
        setObstacleSize(OBSTACLE_SIZE);
        panelData = new PanelData(null, null);
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

    private void drawHUD(Graphics g, RobotStatus status, double reward, double timeRatio) {
        Graphics2D g1 = (Graphics2D) g;
        AffineTransform tr = g1.getTransform();

        FontMetrics fm = getFontMetrics(getFont());
        int hudHeight = 8 * fm.getHeight();

        Dimension size = getSize();

        int xHud = hudAtRight ? size.width - HUD_WIDTH : 0;
        int yHud = hudAtBottom ? size.height - hudHeight : 0;

        g1.translate(xHud, yHud);
        g1.setColor(HUD_BACKGROUND_COLOR);
        g1.fillRect(0, 0, HUD_WIDTH, hudHeight);
        g1.setColor(getForeground());
        drawLine(g1, format("Time     %s %.1fx", strDate(status.simulationTime()), timeRatio), 0, Color.GREEN);
        drawLine(g1, format("Reaction: %.3f s / %.3f s", reactionRobotTime, reactionRealTime), 1, Color.GREEN);
        drawLine(g1, format("Reward   %.2f", reward), 2, Color.GREEN);
        drawLine(g1, format("Distance %.2f m", status.echoDistance()), 3, Color.GREEN);
        drawLine(g1, format("Contacts %s %s",
                        status.frontSensor() ? "-" : "F",
                        status.rearSensor() ? "-" : "B"),
                4, Color.GREEN);
        if (!status.canMoveForward()) {
            drawLine(g1, "FORWARD  STOP", 5, Color.RED);
        }
        if (!status.canMoveBackward()) {
            drawLine(g1, "BACKWARD STOP", 6, Color.RED);
        }
        if (status.imuFailure() != 0) {
            drawLine(g1, format("Imu failure: 0x%x", status.imuFailure()), 7, Color.RED);
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
    private void drawMap(Graphics2D gr, List<Point2D> obstacleMap) {
        if (obstacleMap != null) {
            AffineTransform base = gr.getTransform();
            gr.setStroke(BORDER_STROKE);
            for (Point2D obstacle : obstacleMap) {
                gr.setTransform(base);
                drawObstacle(gr, obstacle);
            }
        }
    }

    /**
     * Draws an obstacle
     *
     * @param gr       the graphic context
     * @param location the location
     */
    private void drawObstacle(Graphics2D gr, Point2D location) {
        if (location != null) {
            gr.transform(at(location));
            gr.setColor(EnvironmentPanel.OBSTACLE_PHANTOM_COLOR);
            gr.setStroke(BORDER_STROKE);
            gr.fill(obstacleShape);
        }
    }

    /**
     * Draws the ping echo point
     *
     * @param gr       the graphics
     * @param location the location
     */
    private void drawPing(Graphics2D gr, Point2D location) {
        if (location != null) {
            fillShape(gr, location, EnvironmentPanel.PING_COLOR, PING_SHAPE);
        }
    }

    /**
     * Draws the robot
     *
     * @param gr the graphic context
     */
    private void drawRobot(Graphics2D gr, Point2D robotLocation, Complex robotDirection) {
        gr.transform(at(robotLocation, robotDirection));
        gr.setColor(ROBOT_COLOR);
        gr.setStroke(BORDER_STROKE);
        gr.draw(ROBOT_OUTER);
        gr.fill(ROBOT_SHAPE);
    }

    /**
     * Draws sensor
     *
     * @param gr the graphic context
     */
    private void drawSensor(Graphics2D gr, Point2D location, Complex robotDirection, Complex sensorDirection) {
        gr.transform(at(location, robotDirection.add(sensorDirection)));
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
        drawMap(gr, obstacleMap);
        PanelData data = this.panelData;

        if (data != null) {
            RobotStatus status = data.status;
            if (status != null) {
                // compute hud position
                hudAtBottom = !hudAtBottom && status.location().getY() > DEFAULT_WORLD_SIZE / 6
                        || (!hudAtBottom || !(status.location().getY() < -DEFAULT_WORLD_SIZE / 6))
                        && hudAtBottom;
                hudAtRight = !hudAtRight && status.location().getX() < -DEFAULT_WORLD_SIZE / 6
                        || (!hudAtRight || !(status.location().getX() > DEFAULT_WORLD_SIZE / 6))
                        && hudAtRight;
            }
            RadarMap radarMap = data.radarMap;
            if (radarMap != null) {
                gr.setTransform(base);
                List<Tuple2<Point2D, Color>> radarMap1 = createMap(radarMap);
                Shape sectorShape = createSectorShape(radarMap);
                drawRadarMap(gr, radarMap1, sectorShape);
            }

            if (status != null) {
                gr.setTransform(base);
                drawRobot(gr, status.location(), status.direction());

                gr.setTransform(base);
                drawSensor(gr, status.location(), status.direction(), status.sensorDirection());

                gr.setTransform(base);
                status.sensorObstacle()
                        .ifPresent(point -> drawPing(gr, point));
                drawHUD(g, status, reward, timeRatio);
            }

            if (target != null) {
                gr.setTransform(base);
                drawTarget(gr, target);
            }
        }
    }

    /**
     * Sets the obstacle map
     *
     * @param obstacleMap the obstacle map
     */
    public void setObstacleMap(List<Point2D> obstacleMap) {
        this.obstacleMap = obstacleMap;
        repaint();
    }

    public void setObstacleSize(double obstacleSize) {
        this.obstacleShape = new Rectangle2D.Double(
                -obstacleSize / 2, -obstacleSize / 2,
                obstacleSize, obstacleSize);
        repaint();
    }

    public void setRadarMap(RadarMap radarMap) {
        this.panelData = this.panelData.setRadarMap(radarMap);
        repaint();
    }

    public void setReactionRealTime(double reactionRealTime) {
        this.reactionRealTime = reactionRealTime;
    }

    public void setReactionRobotTime(double reactionRobotTime) {
        this.reactionRobotTime = reactionRobotTime;
    }

    public void setReward(double reward) {
        this.reward = reward;
        repaint();
    }

    public void setRobotStatus(RobotStatus status) {
        this.panelData = this.panelData.setStatus(status);
        repaint();
    }

    public void setTimeRatio(double timeRatio) {
        this.timeRatio = timeRatio;
        repaint();
    }

    record PanelData(RobotStatus status, RadarMap radarMap) {

        PanelData setRadarMap(RadarMap radarMap) {
            return new PanelData(status, radarMap);
        }

        PanelData setStatus(RobotStatus status) {
            return new PanelData(status, radarMap);
        }
    }
}
