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

        this.leftForwardMotor = Led.create(null, "/images/up.png");
        this.leftBackwardMotor = Led.create(null, "/images/down.png");
        this.rightForwardMotor = Led.create(null, "/images/up.png");
        this.rightBackwardMotor = Led.create(null, "/images/down.png");
        this.forwardBlock = Led.create(null, "/images/brake.png");
        this.obstacleLed = Led.create(null, "/images/green-barrier.png", "/images/yellow-barrier.png", "/images/red-barrier.png");
        this.obstacleMeasureBar = new JProgressBar(JProgressBar.VERTICAL);
        this.obstacleMeasure = new JLabel();
        this.powerMeasureBar = new JProgressBar(JProgressBar.VERTICAL);
        this.powerLed = Led.create("/images/red-charge.png", "/images/yellow-charge.png", "/images/green-charge.png");
        this.powerMeasure = new JLabel();

        setBackground(BLACK);

        obstacleMeasureBar.setMinimum(0);
        obstacleMeasureBar.setMaximum(MAX_DISTANCE);
        powerMeasureBar.setMinimum(0);
        powerMeasureBar.setMaximum(100);

        new GridLayoutHelper<>(this)
                .modify("weight,1,1 right hfill vfill")
                .add(createMotorsPanel())
                .modify("hw,0.3")
                .add(createObstaclePanel(),
                        createPowerPanel());

        setObstacleDistance(0);
        setPower(0);
        setMotors(0, 0);
    }

    /**
     * @param distance
     */
    public void setObstacleDistance(int distance) {
        if (distance > 0) {
            obstacleMeasureBar.setValue(MAX_DISTANCE - distance);
            obstacleMeasure.setText(format("%d cm", distance));
            Color color = distance <= STOP_DISTANCE ? RED
                    : distance <= WARN_DISTANCE ? YELLOW
                    : distance <= INFO_DISTANCE ? GREEN
                    : GRAY;
            int led = distance <= STOP_DISTANCE ? 3
                    : distance <= WARN_DISTANCE ? 2
                    : distance <= INFO_DISTANCE ? 1
                    : 0;
            obstacleLed.setValue(led);
            obstacleMeasureBar.setForeground(color);
        } else {
            obstacleMeasureBar.setValue(0);
            obstacleMeasure.setText(format("-"));
            Color color = GREEN;
            obstacleLed.setValue(0);
            obstacleMeasureBar.setForeground(color);
        }
    }

    /**
     * @param voltage
     */
    public void setPower(double voltage) {
        powerMeasureBar.setValue((int) round(100 * voltage / MAX_VOLTAGE));
        powerMeasure.setText(format("%.1f V", voltage));
        Color color = voltage <= MIN_VOLTAGE ? RED
                : voltage <= MID_VOLTAGE ? YELLOW
                : GREEN;
        int led = voltage <= MIN_VOLTAGE ? 0
                : voltage <= MID_VOLTAGE ? 1
                : 2;
        powerLed.setValue(led);
        powerMeasureBar.setForeground(color);
    }

    /**
     *
     */
    private JPanel createMotorsPanel() {
        JPanel container = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 span,2,1")
                .add(forwardBlock)
                .modify("at,0,1 weight,1,1 span,1,1 center")
                .add(leftForwardMotor)
                .modify("at,1,1")
                .add(rightForwardMotor)
                .modify("at,0,2")
                .add(leftBackwardMotor)
                .modify("at,1,2")
                .add(rightBackwardMotor)
                .getContainer();
        container.setBackground(BLACK);
        return container;
    }

    /**
     *
     */
    private JPanel createObstaclePanel() {
        JLabel label = new JLabel("Obstacle");
        label.setForeground(WHITE);
        obstacleMeasureBar.setBackground(BLACK);
        obstacleMeasureBar.setBorderPainted(false);
        obstacleMeasure.setForeground(WHITE);
        JPanel container = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0")
                .add(label)
                .modify("below")
                .add(obstacleMeasureBar)
                .add(obstacleMeasure)
                .add(obstacleLed)
                .getContainer();
        container.setBackground(BLACK);
        return container;
    }

    /**
     *
     */
    private JPanel createPowerPanel() {
        JLabel label1 = new JLabel("Power");
        label1.setForeground(WHITE);
        powerMeasureBar.setBackground(BLACK);
        powerMeasureBar.setBorderPainted(false);
        powerMeasure.setForeground(WHITE);
        JPanel container = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0")
                .add(label1)
                .modify("below")
                .add(powerMeasureBar)
                .add(powerMeasure)
                .add(powerLed)
                .getContainer();
        container.setBackground(BLACK);
        return container;
    }

    /**
     * @param left  left speed
     * @param right right speed
     */
    public void setMotors(int left, int right) {
        if (left > 0) {
            leftForwardMotor.setValue(1);
            leftBackwardMotor.setValue(0);
        } else if (left < 0) {
            leftForwardMotor.setValue(0);
            leftBackwardMotor.setValue(1);
        } else {
            leftForwardMotor.setValue(0);
            leftBackwardMotor.setValue(0);
        }
        if (right > 0) {
            rightForwardMotor.setValue(1);
            rightBackwardMotor.setValue(0);
        } else if (right < 0) {
            rightForwardMotor.setValue(0);
            rightBackwardMotor.setValue(1);
        } else {
            rightForwardMotor.setValue(0);
            rightBackwardMotor.setValue(0);
        }
    }

    public void setForwardBlock(boolean block) {
        forwardBlock.setValue(block ? 1 : 0);
    }
}
