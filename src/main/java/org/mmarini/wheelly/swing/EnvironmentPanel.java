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

import org.mmarini.wheelly.apis.ObstacleMap;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.RobotStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import static java.lang.String.format;
import static org.mmarini.wheelly.swing.BaseShape.*;
import static org.mmarini.wheelly.swing.Utils.DEFAULT_WORLD_SIZE;
import static org.mmarini.wheelly.swing.Utils.GRID_SIZE;

/**
 * The canvas with environment display
 */
public class EnvironmentPanel extends JComponent {
    public static final int DEFAULT_WINDOW_SIZE = 800;
    public static final float PING_RADIUS = 0.05f;
    static final float DEFAULT_SCALE = DEFAULT_WINDOW_SIZE / DEFAULT_WORLD_SIZE;
    private static final int HUD_WIDTH = 200;
    private static final float TARGET_SIZE = 0.2f;

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

    private final Point2D centerLocation;
    private boolean hudAtBottom;
    private boolean hudAtRight;
    private double reactionRealTime;
    private double reactionRobotTime;
    private double reward;
    private double timeRatio;
    private BaseShape gridShape;
    private BaseShape target;
    private BaseShape mapShape;
    private BaseShape robotShape;
    private BaseShape sensorShape;
    private BaseShape pingShape;
    private BaseShape hinderedShape;
    private BaseShape labeledShape;
    private RobotStatus robotStatus;

    public EnvironmentPanel() {
        this.centerLocation = new Point2D.Float();

        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setFont(Font.decode("Monospaced"));
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
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
        g1.setColor(BaseShape.HUD_BACKGROUND_COLOR);
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
     * Returns the location of center map
     */
    public Point2D getCenterLocation() {
        return centerLocation;
    }

    /**
     * Returns the scale
     */
    public float getScale() {
        return DEFAULT_SCALE;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Dimension size = getSize();
        g.setColor(getBackground());
        g.fillRect(0, 0, size.width, size.height);
        Graphics2D gr = (Graphics2D) g.create();
        gr.transform(createBaseTransform());
        AffineTransform base = gr.getTransform();
        if (gridShape != null) {
            gridShape.paint(gr);
        }
        if (hinderedShape != null) {
            hinderedShape.paint(gr);
        }
        if (labeledShape != null) {
            labeledShape.paint(gr);
        }
        if (mapShape != null) {
            mapShape.paint(gr);
        }
        gr.setTransform(base);
        if (robotShape != null) {
            robotShape.paint(gr);
        }
        if (sensorShape != null) {
            sensorShape.paint(gr);
        }
        if (pingShape != null) {
            pingShape.paint(gr);
        }
        if (target != null) {
            gr.setTransform(base);
            target.paint(gr);
        }
        RobotStatus status = this.robotStatus;
        if (status != null) {
            // compute hud position
            hudAtBottom = !hudAtBottom && status.location().getY() > DEFAULT_WORLD_SIZE / 6
                    || (!hudAtBottom || !(status.location().getY() < -DEFAULT_WORLD_SIZE / 6))
                    && hudAtBottom;
            hudAtRight = !hudAtRight && status.location().getX() < -DEFAULT_WORLD_SIZE / 6
                    || (!hudAtRight || !(status.location().getX() > DEFAULT_WORLD_SIZE / 6))
                    && hudAtRight;
            gr.setTransform(base);
            drawHUD(g, status, reward, timeRatio);
        }
    }

    /**
     * Sets the obstacle map
     *
     * @param map the map
     */
    public void setObstacles(ObstacleMap map) {
        float obstacleSize = (float) map.gridSize();
        this.hinderedShape = new CompositeShape(map.hindered()
                .map(obs ->
                        createRectangle(OBSTACLE_PHANTOM_COLOR, BORDER_STROKE, true, obs, obstacleSize, obstacleSize))
                .toList());
        this.labeledShape = new CompositeShape(map.labeled()
                .map(obs ->
                        createRectangle(LABELED_PHANTOM_COLOR, BORDER_STROKE, true, obs, obstacleSize, obstacleSize))
                .toList());
        repaint();
    }

    /**
     * Sets the radar map
     *
     * @param radarMap the radar map
     */
    public void setRadarMap(RadarMap radarMap) {
        this.gridShape = BaseShape.createGridShape(radarMap.topology(), GRID_SIZE);
        this.mapShape = BaseShape.createMapShape((float) radarMap.topology().gridSize(), radarMap.cells());
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
        this.robotStatus = status;
        this.robotShape = createRobotShape(status.location(), status.direction());
        this.sensorShape = createSensorShape(status.location(), status.direction().add(status.sensorDirection()));
        this.pingShape = status.sensorObstacle()
                .map(point -> createCircle(PING_COLOR, BORDER_STROKE, true, point, PING_RADIUS))
                .orElse(null);
        repaint();
    }

    /**
     * Sets the target point
     *
     * @param target the target point
     */
    public void setTarget(Point2D target) {
        this.target = BaseShape.createCircle(TARGET_COLOR, BORDER_STROKE, false, target, TARGET_SIZE);
        repaint();
    }

    public void setTimeRatio(double timeRatio) {
        this.timeRatio = timeRatio;
        repaint();
    }
}
