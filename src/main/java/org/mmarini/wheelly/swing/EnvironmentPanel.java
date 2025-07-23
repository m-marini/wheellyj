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

import org.mmarini.wheelly.apis.RobotStatus;

import java.awt.*;
import java.awt.geom.AffineTransform;

import static java.lang.String.format;
import static org.mmarini.wheelly.swing.Utils.DEFAULT_WORLD_SIZE;

/**
 * The canvas with environment display
 */
public class EnvironmentPanel extends MapPanel {
    public static final int DEFAULT_WINDOW_SIZE = 800;
    public static final float DEFAULT_MARKER_SIZE = 0.3f;
    private static final int HUD_WIDTH = 200;

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
    private double reactionRealTime;
    private double reactionRobotTime;
    private double reward;
    private double timeRatio;
    private RobotStatus robotStatus;

    /**
     * Creates the environment panel
     */
    public EnvironmentPanel() {
        setFont(Font.decode("Monospaced"));
        setPreferredSize(new Dimension(DEFAULT_WINDOW_SIZE, DEFAULT_WINDOW_SIZE));
    }

    /**
     * Draws the HUD
     *
     * @param g         graphics
     * @param status    the status
     * @param reward    the reward
     * @param timeRatio the time ratio
     */
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

    /**
     * Draws a text line
     *
     * @param g      the graphics
     * @param text   the text
     * @param row    the row index
     * @param colour the colour
     */
    private void drawLine(Graphics g, String text, int row, Color colour) {
        FontMetrics fm = getFontMetrics(getFont());
        int y = fm.getHeight() * (row + 1);
        g.setColor(colour);
        g.drawString(text, 0, y);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        RobotStatus status = robotStatus;
        if (status != null) {
            // compute hud position
            hudAtBottom = !hudAtBottom && status.location().getY() > DEFAULT_WORLD_SIZE / 6
                    || (!hudAtBottom || !(status.location().getY() < -DEFAULT_WORLD_SIZE / 6))
                    && hudAtBottom;
            hudAtRight = !hudAtRight && status.location().getX() < -DEFAULT_WORLD_SIZE / 6
                    || (!hudAtRight || !(status.location().getX() > DEFAULT_WORLD_SIZE / 6))
                    && hudAtRight;
            drawHUD(g, status, reward, timeRatio);
        }
    }

    @Override
    public void robotStatus(RobotStatus status) {
        super.robotStatus(status);
        this.robotStatus = status;
    }

    /**
     * Sets the reaction real time
     *
     * @param reactionRealTime the reaction real time
     */
    public void setReactionRealTime(double reactionRealTime) {
        this.reactionRealTime = reactionRealTime;
        repaint();
    }

    /**
     * Sets the reaction robot time
     *
     * @param reactionRobotTime the reaction robot time
     */
    public void setReactionRobotTime(double reactionRobotTime) {
        this.reactionRobotTime = reactionRobotTime;
        repaint();
    }

    /**
     * Sets the reward
     *
     * @param reward reward
     */
    public void setReward(double reward) {
        this.reward = reward;
        repaint();
    }

    /**
     * Sets the time ratio
     * @param timeRatio the time ratio (x)
     */
    public void setTimeRatio(double timeRatio) {
        this.timeRatio = timeRatio;
        repaint();
    }
}
