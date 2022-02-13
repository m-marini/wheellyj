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

import org.mmarini.swing.GridLayoutHelper;

import javax.swing.*;
import java.awt.*;

import static java.awt.Color.*;
import static java.lang.Math.round;
import static java.lang.String.format;

/**
 *
 */
public class Dashboard extends JPanel {

    public static final double MAX_VOLTAGE = 15;
    public static final int MAX_DISTANCE = 300;
    public static final int STOP_DISTANCE = 30;
    public static final int WARN_DISTANCE = 50;
    public static final int INFO_DISTANCE = 70;
    public static final double MIN_VOLTAGE = 5.0;
    public static final double MID_VOLTAGE = 5.8;

    private final Led leftForwardMotor;
    private final Led leftBackwardMotor;
    private final Led rightForwardMotor;
    private final Led rightBackwardMotor;
    private final Led forwardBlock;
    private final JProgressBar obstacleMeasureBar;
    private final Led obstacleLed;
    private final JLabel obstacleMeasure;
    private final Led powerLed;
    private final JProgressBar powerMeasureBar;
    private final JLabel powerMeasure;

    /**
     *
     */
    public Dashboard() {

        this.leftForwardMotor = new Led();
        this.leftBackwardMotor = new Led();
        this.rightForwardMotor = new Led();
        this.rightBackwardMotor = new Led();
        this.forwardBlock = new Led();
        this.obstacleLed = new Led();
        this.obstacleMeasureBar = new JProgressBar(JProgressBar.VERTICAL);
        this.obstacleMeasure = new JLabel();
        this.powerMeasureBar = new JProgressBar(JProgressBar.VERTICAL);
        this.powerLed = new Led();
        this.powerMeasure = new JLabel();

        obstacleMeasureBar.setMinimum(0);
        obstacleMeasureBar.setMaximum(MAX_DISTANCE);
        powerMeasureBar.setMinimum(0);
        powerMeasureBar.setMaximum(100);

        new GridLayoutHelper<>(this)
                .modify("weight,1,1 at 0,0 hfill vfill")
                .add(createMotorsPanel())
                .modify("hw,0.5 right")
                .add(createObstaclePanel(),
                        createPowerPanel());

        setObstacleDistance(0);
        setPower(0);
        setMotors(0, 0);
    }

    /**
     * @param distance
     */
    void setObstacleDistance(int distance) {
        if (distance > 0) {
            obstacleMeasureBar.setValue(MAX_DISTANCE - distance);
            obstacleMeasure.setText(format("%d cm", distance));
            Color color = distance <= STOP_DISTANCE
                    ? RED
                    : distance <= WARN_DISTANCE
                    ? ORANGE
                    : distance <= INFO_DISTANCE
                    ? YELLOW
                    : GREEN;
            obstacleLed.setBackground(color);
            obstacleMeasureBar.setForeground(color);
        } else {
            obstacleMeasureBar.setValue(0);
            obstacleMeasure.setText(format("-"));
            Color color = GREEN;
            obstacleLed.setBackground(color);
            obstacleMeasureBar.setForeground(color);
        }
    }

    /**
     * @param voltage
     */
    public void setPower(double voltage) {
        powerMeasureBar.setValue((int) round(100 * voltage / MAX_VOLTAGE));
        powerMeasure.setText(format("%.1f V", voltage));
        Color color = voltage <= MIN_VOLTAGE
                ? RED
                : voltage <= MID_VOLTAGE
                ? YELLOW
                : GREEN;
        powerLed.setBackground(color);
        powerMeasureBar.setForeground(color);
    }

    private JPanel createMotorsPanel() {
        return new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 right ")
                .add(new JLabel("Forward block"), forwardBlock)
                .modify("below weight,1,1")
                .add(leftForwardMotor)
                .modify("right")
                .add(rightForwardMotor )
                .modify("below")
                .add(leftBackwardMotor)
                .modify("right")
                .add(rightBackwardMotor)
                .getContainer();
    }

    private JPanel createObstaclePanel() {
        return new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0")
                .add(new JLabel("Obstacle"))
                .modify("below")
                .add(obstacleMeasureBar)
                .add(obstacleMeasure)
                .add(obstacleLed)
                .getContainer();
    }

    private JPanel createPowerPanel() {
        return new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0")
                .add(new JLabel("Power"))
                .modify("below")
                .add(powerMeasureBar)
                .add(powerMeasure)
                .add(powerLed)
                .getContainer();
    }

    public void setMotors(int left, int right) {
        if (left > 0) {
            leftForwardMotor.setBackground(GREEN);
            leftBackwardMotor.setBackground(BLACK);
        } else if (left < 0) {
            leftForwardMotor.setBackground(BLACK);
            leftBackwardMotor.setBackground(YELLOW);
        } else {
            leftForwardMotor.setBackground(BLACK);
            leftBackwardMotor.setBackground(BLACK);
        }
        if (right > 0) {
            rightForwardMotor.setBackground(GREEN);
            rightBackwardMotor.setBackground(BLACK);
        } else if (right < 0) {
            rightForwardMotor.setBackground(BLACK);
            rightBackwardMotor.setBackground(YELLOW);
        } else {
            rightForwardMotor.setBackground(BLACK);
            rightBackwardMotor.setBackground(BLACK);
        }
    }

    public void setForwardBlock(boolean block) {
        forwardBlock.setBackground(block ? RED : GREEN);
    }
}
