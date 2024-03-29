/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.wheelly.apis.RobotStatus;

import javax.swing.*;
import java.text.DecimalFormat;
import java.util.List;

public class SensorsPanel extends JPanel {
    private final JFormattedTextField direction;
    private final JFormattedTextField sensorDirection;
    private final JFormattedTextField echoTime;
    private final JFormattedTextField distance;
    private final JFormattedTextField robotX;
    private final JFormattedTextField robotY;
    private final JFormattedTextField xPulses;
    private final JFormattedTextField yPulses;
    private final JFormattedTextField leftSpeed;
    private final JFormattedTextField rightSpeed;
    private final JFormattedTextField supplySensor;
    private final JFormattedTextField voltage;
    private final JFormattedTextField imuFailure;
    private final JCheckBox canMoveForward;
    private final JCheckBox canMoveBackward;
    private final JCheckBox halt;
    private final JCheckBox frontSensors;
    private final JCheckBox rearSensors;
    private final JTextField info;
    private final JButton checkUpButton;

    /**
     * Creates the sensor panel.
     * The sensor panel displays the robot status with several UI components and a button to run the check-up
     */
    public SensorsPanel() {
        DecimalFormat cmFormat = new DecimalFormat("#0.00");
        DecimalFormat degFormat = new DecimalFormat("##0");
        DecimalFormat microsFormat = new DecimalFormat("##,##0");
        DecimalFormat pulsesFormat = new DecimalFormat("#,##0.0");
        DecimalFormat voltageFormat = new DecimalFormat("#0.0");
        DecimalFormat intFormat = new DecimalFormat("#0");
        this.robotX = new JFormattedTextField(cmFormat);
        this.robotY = new JFormattedTextField(cmFormat);
        this.direction = new JFormattedTextField(degFormat);
        this.echoTime = new JFormattedTextField(microsFormat);
        this.xPulses = new JFormattedTextField(pulsesFormat);
        this.yPulses = new JFormattedTextField(pulsesFormat);
        this.distance = new JFormattedTextField(cmFormat);
        this.sensorDirection = new JFormattedTextField(degFormat);
        this.leftSpeed = new JFormattedTextField(cmFormat);
        this.rightSpeed = new JFormattedTextField(cmFormat);
        this.voltage = new JFormattedTextField(voltageFormat);
        this.imuFailure = new JFormattedTextField(intFormat);
        this.frontSensors = new JCheckBox();
        this.rearSensors = new JCheckBox();
        this.supplySensor = new JFormattedTextField(intFormat);
        this.canMoveForward = new JCheckBox();
        this.canMoveBackward = new JCheckBox();
        this.halt = new JCheckBox();
        this.info = new JTextField(50);
        this.checkUpButton = new JButton("Check up");
        createContent();
        init();
    }

    /**
     * Creates the content
     */
    private void createContent() {

        JPanel robotPane = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 noweight nospan nofill e").add("Robot X (m)")
                .modify("at,0,1").add("Robot Y (m)")
                .modify("at,0,2").add("Direction (DEG)")
                .modify("at,0,3").add("Power supply (V)")
                .modify("at,0,4").add("Halt")
                .modify("at,0,5").add("IMU failure code")
                .modify("at,0,6 weight,0,1").add("")
                .modify("at,1,0 noweight w").add(robotX)
                .modify("at,1,1").add(robotY)
                .modify("at,1,2").add(direction)
                .modify("at,1,3").add(voltage)
                .modify("at,1,4").add(halt)
                .modify("at,1,5").add(imuFailure)
                .modify("at,2,0 weight,1,0 w nofill").add(xPulses)
                .modify("at,2,1").add(yPulses)
                .modify("at,2,3").add(supplySensor)
                .getContainer();
        robotPane.setBorder(BorderFactory.createTitledBorder("Robot"));

        JPanel speedPane = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 weight,1,0 nospan nofill center").add("Left")
                .modify("at,1,0").add("Right")
                .modify("at,0,1").add(leftSpeed)
                .modify("at,1,1").add(rightSpeed)
                .getContainer();
        speedPane.setBorder(BorderFactory.createTitledBorder("Speed (pps)"));

        JPanel sensorPanel = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 noweight nospan nofill e").add("Sensor (DEG)")
                .modify("at,0,1").add("Distance (m)")
                .modify("at,2,1").add("(us)")
                .modify("at,0,2 weight,0,1").add("")
                .modify("at,1,0 w noweight").add(sensorDirection)
                .modify("at,1,1").add(distance)
                .modify("at,3,1 weight,1,0 w").add(echoTime)
                .getContainer();
        sensorPanel.setBorder(BorderFactory.createTitledBorder("Distance sensor"));

        JPanel proxyPanel = new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0 noweight nospan nofill e").add("Can move forward")
                .modify("at,0,1").add("Can move backward")
                .modify("at,0,2").add("FrontSensors")
                .modify("at,0,3").add("RearSensors")
                .modify("at,0,4 weight,0,1").add("")
                .modify("at,1,0 w weight,1,0").add(canMoveForward)
                .modify("at,1,1").add(canMoveBackward)
                .modify("at,1,2").add(frontSensors)
                .modify("at,1,3").add(rearSensors)
                .getContainer();
        proxyPanel.setBorder(BorderFactory.createTitledBorder("Proximity sensors"));

        new GridLayoutHelper<>(this)
                .modify("insets,4 at,0,0 hfill span,2,1").add(info)
                .modify("at,0,1 nospan fill weight,1,0").add(robotPane)
                .modify("at,0,2").add(speedPane)
                .modify("at,1,1").add(sensorPanel)
                .modify("at,1,2").add(proxyPanel)
                .modify("at,0,4 span,2,1 center noweight nofill").add(checkUpButton);
    }

    public JButton getCheckUpButton() {
        return checkUpButton;
    }

    /**
     * Initializes the panel.
     * Sets the attributes of UI components
     */
    private void init() {
        for (JTextField x : List.of(robotX, robotY, direction, sensorDirection, distance, leftSpeed, rightSpeed,
                voltage, imuFailure, supplySensor)) {
            x.setColumns(5);
            x.setHorizontalAlignment(JTextField.RIGHT);
        }
        echoTime.setColumns(7);
        echoTime.setHorizontalAlignment(JTextField.RIGHT);

        xPulses.setColumns(8);
        xPulses.setHorizontalAlignment(JTextField.RIGHT);
        yPulses.setColumns(8);
        yPulses.setHorizontalAlignment(JTextField.RIGHT);

        for (JTextField x : List.of(robotX, robotY, direction, sensorDirection, distance,
                leftSpeed, rightSpeed, voltage, imuFailure, info, echoTime,
                supplySensor, xPulses, yPulses)) {
            x.setEditable(false);
        }
        for (JCheckBox x : List.of(canMoveBackward, canMoveForward, halt, frontSensors, rearSensors)) {
            x.setEnabled(false);
        }
    }

    /**
     * Shows the info message
     *
     * @param text the text
     */
    public void setInfo(String text) {
        info.setText(text);
    }

    /**
     * @param status the robot status
     */
    public void setStatus(RobotStatus status) {
        robotX.setValue(status.location().getX());
        robotY.setValue(status.location().getY());
        xPulses.setValue(status.xPulse());
        yPulses.setValue(status.yPulse());
        direction.setValue(status.direction());
        sensorDirection.setValue(status.sensorDirection());
        echoTime.setValue(status.echoDelay());
        distance.setValue(status.echoDistance());
        leftSpeed.setValue(status.leftPps());
        rightSpeed.setValue(status.rightPps());
        voltage.setValue(status.supplyVoltage());
        supplySensor.setValue(status.supplySensor());
        imuFailure.setValue(status.imuFailure());
        rearSensors.setSelected(status.rearSensor());
        frontSensors.setSelected(status.frontSensor());
        canMoveBackward.setSelected(status.canMoveBackward());
        canMoveForward.setSelected(status.canMoveForward());
        halt.setSelected(status.halt());
    }
}
