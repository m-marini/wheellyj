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

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.swing.GridLayoutHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;

import static java.awt.Color.*;
import static java.lang.Math.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.RobotController.WARN_DISTANCE;

/**
 *
 */
public class Dashboard extends JPanel {

    public static final double INFO_DISTANCE = WARN_DISTANCE + 0.2;
    public static final double MIN_VOLTAGE = 7;
    public static final double FULL_VOLTAGE = 12.6;

    private final Led leftForwardMotor;
    private final Led leftBackwardMotor;
    private final Led rightForwardMotor;
    private final Led rightBackwardMotor;
    private final Led forwardBlock;
    private final JLabel leftPower;
    private final JLabel rightPower;
    private final JProgressBar obstacleMeasureBar;
    private final Led obstacleLed;
    private final JLabel obstacleMeasure;
    private final Led powerLed;
    private final JProgressBar powerMeasureBar;
    private final JLabel powerMeasure;
    private final Led wifiLed;
    private final JLabel cps;
    private final Flowable<ActionEvent> resetFlow;
    private final JLabel elaps;
    private final JButton reset;
    private final JLabel yaw;
    private final JLabel robotLocation;
    private final Compass compass;
    private final Led imuStatus;
    private final Led imuFailure;

    /**
     *
     */
    public Dashboard() {
        this.leftForwardMotor = Led.create("/images/up-off.png", "/images/up.png");
        this.leftBackwardMotor = Led.create("/images/down-off.png", "/images/down.png");
        this.rightForwardMotor = Led.create("/images/up-off.png", "/images/up.png");
        this.rightBackwardMotor = Led.create("/images/down-off.png", "/images/down.png");
        this.forwardBlock = Led.create("/images/brake-off.png", "/images/brake.png");
        this.obstacleLed = Led.create("/images/barrier-off.png", "/images/green-barrier.png", "/images/yellow-barrier.png", "/images/red-barrier.png");
        this.wifiLed = Led.create("/images/wifi-off.png", "/images/wifi-on.png");
        this.obstacleMeasureBar = new JProgressBar(JProgressBar.VERTICAL);
        this.obstacleMeasure = new JLabel();
        this.powerMeasureBar = new JProgressBar(JProgressBar.VERTICAL);
        this.powerLed = Led.create("/images/red-charge.png", "/images/yellow-charge.png", "/images/green-charge.png");
        this.powerMeasure = new JLabel();
        this.leftPower = new JLabel();
        this.rightPower = new JLabel();
        this.cps = new JLabel();
        this.elaps = new JLabel();
        this.yaw = new JLabel();
        this.robotLocation = new JLabel();
        this.reset = new JButton("Reset");
        this.imuStatus = Led.create("/images/engine-off.png", "/images/engine-on.png");
        this.imuFailure = Led.create("/images/gyro-off.png", "/images/gyro-on.png");
        this.compass = new Compass();
        this.resetFlow = SwingObservable.actions(reset).toFlowable(BackpressureStrategy.DROP);
        setCps(0);
        setElapsed(0);
        setYaw(0);
        setRobotLocation(new Point2D.Double());
        setImuStatus(0);

        setBackground(BLACK);

        powerMeasureBar.setMinimum(0);
        powerMeasureBar.setMaximum(100);
        cps.setBackground(BLACK);
        cps.setForeground(WHITE);
        elaps.setBackground(BLACK);
        elaps.setForeground(WHITE);
        leftPower.setBackground(BLACK);
        leftPower.setForeground(WHITE);
        rightPower.setBackground(BLACK);
        rightPower.setForeground(WHITE);
        yaw.setBackground(BLACK);
        yaw.setForeground(WHITE);
        robotLocation.setBackground(BLACK);
        robotLocation.setForeground(WHITE);

        new GridLayoutHelper<>(this).modify("weight,1,1 fill at,0,0").add(createConnectionPanel()).modify("at,1,0").add(createMotorsPanel()).modify("at,2,0").add(createObstaclePanel()).modify("at,3,0").add(createPowerPanel()).modify("at,4,0").add(createAssetPanel());

        setObstacleDistance(0);
        setPower(0);
        setMotors(0, 0);
    }

    /**
     *
     */
    private JPanel createAssetPanel() {
        JPanel container = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 weight,1,1 fill center").add(compass)
                .modify("at,0,1 nofill noweight").add(yaw)
                .modify("at,0,2 nofill noweight").add(robotLocation)
                .modify("at,0,4").add(imuStatus)
                .modify("at,0,5").add(imuFailure)
                .modify("at,0,6").add(reset)
                .getContainer();
        container.setBackground(BLACK);
        return container;
    }

    /**
     *
     */
    private JPanel createConnectionPanel() {
        JPanel container = new GridLayoutHelper<>(new JPanel()).modify("insets,2 at,0,0 weight,1,1").add(wifiLed).modify("at,0,1 center").add(cps).modify("at,0,2").add(elaps).getContainer();
        container.setBackground(BLACK);
        return container;
    }

    /**
     *
     */
    private JPanel createMotorsPanel() {
        JPanel container = new GridLayoutHelper<>(new JPanel()).modify("insets,2 at,0,0 span,2,1").add(forwardBlock).modify("at,0,1 weight,1,1 span,1,1 center").add(leftForwardMotor).modify("at,1,1").add(rightForwardMotor).modify("at,0,2").add(leftPower).modify("at,1,2").add(rightPower).modify("at,0,3").add(leftBackwardMotor).modify("at,1,3").add(rightBackwardMotor).getContainer();
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
        JPanel container = new GridLayoutHelper<>(new JPanel()).modify("insets,2 at,0,0").add(label).modify("below").add(obstacleMeasureBar).add(obstacleMeasure).add(obstacleLed).getContainer();
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
        JPanel container = new GridLayoutHelper<>(new JPanel()).modify("insets,2 at,0,0").add(label1).modify("below").add(powerMeasureBar).add(powerMeasure).add(powerLed).getContainer();
        container.setBackground(BLACK);
        return container;
    }

    /**
     *
     */
    public Flowable<ActionEvent> getResetFlow() {
        return resetFlow;
    }

    void setAngle(double angle) {
        compass.setAngle(angle);
        setYaw(angle);
    }

    /**
     * @param cps the transitions per second
     */
    public void setCps(int cps) {
        this.cps.setText(format("%d CPS", cps));
    }

    /**
     * Sets the average elapsed time
     *
     * @param elaps the average elapsed time
     */
    public void setElapsed(double elaps) {
        this.elaps.setText(format("%.0f ms", elaps));
    }

    /**
     * Sets the forward direction block
     *
     * @param block true if blocked
     */
    public void setForwardBlock(boolean block) {
        forwardBlock.setValue(block ? 1 : 0);
    }

    /**
     * @param failure the failure status
     */
    public void setImuFailure(int failure) {
        imuFailure.setValue(failure != 0 ? 0 : 1);
    }

    /**
     * @param status the imu status
     */
    private void setImuStatus(int status) {
        imuStatus.setValue(status != 0 ? 0 : 1);
    }

    /**
     * @param left  left speed
     * @param right right speed
     */
    public void setMotors(double left, double right) {
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
        leftPower.setText(format("%d%%", round(abs(left * 100f))));
        rightPower.setText(format("%d%%", round(abs(right * 100f))));
    }

    /**
     * @param distance the distance
     */
    public void setObstacleDistance(double distance) {
        if (distance > 0) {
            int barValue = min(max((int) round(100 * (INFO_DISTANCE - distance) / INFO_DISTANCE), 0), 100);
            obstacleMeasureBar.setValue(barValue);
            obstacleMeasure.setText(format("%.0f cm", distance * 100));
            Color color = distance <= STOP_DISTANCE ? RED : distance <= WARN_DISTANCE ? YELLOW : distance <= INFO_DISTANCE ? GREEN : GRAY;
            int led = distance <= STOP_DISTANCE ? 3 : distance <= WARN_DISTANCE ? 2 : distance <= INFO_DISTANCE ? 1 : 0;
            obstacleLed.setValue(led);
            obstacleMeasureBar.setForeground(color);
        } else {
            obstacleMeasureBar.setValue(0);
            obstacleMeasure.setText("-");
            obstacleLed.setValue(0);
            obstacleMeasureBar.setForeground(GREEN);
        }
    }

    /**
     * @param voltage the voltage
     */
    public void setPower(double voltage) {
        int perc = max(0, min((int) round(100 * (voltage - MIN_VOLTAGE) / (FULL_VOLTAGE - MIN_VOLTAGE)), 100));
        powerMeasureBar.setValue(perc);
        powerMeasure.setText(format("%.1f V", voltage));
        Color color = perc < 33 ? RED : perc <= 66 ? YELLOW : GREEN;
        int led = perc < 33 ? 0 : perc < 67 ? 1 : 2;
        powerLed.setValue(led);
        powerMeasureBar.setForeground(color);
    }

    public void setRobotLocation(Point2D location) {
        robotLocation.setText(format("%.1f, %.1f m", location.getX(), location.getY()));
    }

    /**
     * @param on true if connected
     */
    public void setWifiLed(boolean on) {
        wifiLed.setValue(on ? 1 : 0);
    }

    /**
     * @param yaw the yow in RAD
     */
    private void setYaw(double yaw) {
        int deg = (int) round(toDegrees(yaw));
        while (deg < 0) {
            deg += 360;
        }
        this.yaw.setText(format("%03d DEG", deg));
    }
}
