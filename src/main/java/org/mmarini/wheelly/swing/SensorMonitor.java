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

import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.stream.Stream;

/**
 * Displays the robot status, controller status and roboto commands as matrix table
 */
public class SensorMonitor extends MatrixTable {

    public static final String ECHO_DIST_KEY = "echoDistance";
    public static final String SENSOR_DIR_KEY = "sensorDir";
    public static final String HEAD_KEY = "head";
    public static final String LEFT_PPS_KEY = "leftPps";
    public static final String RIGHT_PPS_KEY = "rightPps";
    public static final String CAN_MOVE_FORWARD_KEY = "canMoveForward";
    public static final String CAN_MOVE_BACKWARD_KEY = "canMoveBackward";
    public static final String CONTACTS_KEY = "contacts";
    public static final String X_LOCATION_KEY = "xLocation";
    public static final String Y_LOCATION_KEY = "yLocation";
    public static final String IMU_FAILURE_KEY = "imuFailure";
    public static final String CONTROLLER_STATUS_KEY = "controllerStatus";
    public static final String SUPPLY_KEY = "supply";
    public static final String MOVE_HEAD = "moveHead";
    public static final String MOVE_SPEED = "moveSpeed";
    public static final String REWARD_KEY = "reward";
    public static final String LEFT_POWER_KEY = "leftPower";
    public static final String RIGHT_POWER_KEY = "rightPower";
    public static final String LEFT_TARGET_PPS_KEY = "leftTargetPps";
    public static final String RIGHT_TARGET_PPS_KEY = "rightTargetPps";
    private static final Logger logger = LoggerFactory.getLogger(SensorMonitor.class);

    /**
     * Create the sensor monitor
     */
    public SensorMonitor() {
        Stream.of(
                        addColumn(HEAD_KEY, Messages.getString("SensorMonitor.head"), 4),
                        addColumn(SENSOR_DIR_KEY, Messages.getString("SensorMonitor.sensorDir"), 3),
                        addColumn(ECHO_DIST_KEY, Messages.getString("SensorMonitor.echoDistance"), 3),
                        addColumn(LEFT_PPS_KEY, Messages.getString("SensorMonitor.leftPps"), 3),
                        addColumn(RIGHT_PPS_KEY, Messages.getString("SensorMonitor.rightPps"), 3),
                        addColumn(CAN_MOVE_FORWARD_KEY, Messages.getString("SensorMonitor.canMoveForward"), 1),
                        addColumn(CAN_MOVE_BACKWARD_KEY, Messages.getString("SensorMonitor.canMoveBackward"), 1),
                        addColumn(CONTACTS_KEY, Messages.getString("SensorMonitor.contacts"), 1),
                        addColumn(X_LOCATION_KEY, Messages.getString("SensorMonitor.xLocation"), 5),
                        addColumn(Y_LOCATION_KEY, Messages.getString("SensorMonitor.yLocation"), 5),
                        addColumn(CONTROLLER_STATUS_KEY, Messages.getString("SensorMonitor.controllerStatus"), 3),
                        addColumn(MOVE_HEAD, Messages.getString("SensorMonitor.moveHead"), 4),
                        addColumn(MOVE_SPEED, Messages.getString("SensorMonitor.moveSpeed"), 3),
                        addColumn(REWARD_KEY, Messages.getString("SensorMonitor.reward"), 6),
                        addColumn(LEFT_TARGET_PPS_KEY, Messages.getString("SensorMonitor.leftTargetPps"), 3),
                        addColumn(RIGHT_TARGET_PPS_KEY, Messages.getString("SensorMonitor.rightTargetPps"), 3),
                        addColumn(LEFT_POWER_KEY, Messages.getString("SensorMonitor.leftPower"), 4),
                        addColumn(RIGHT_POWER_KEY, Messages.getString("SensorMonitor.rightPower"), 4),
                        addColumn(IMU_FAILURE_KEY, Messages.getString("SensorMonitor.imuFailure"), 3),
                        addColumn(SUPPLY_KEY, Messages.getString("SensorMonitor.supply"), 3))
                .forEach(col -> col.setScrollOnChange(true));
        setPrintTimestamp(false);
    }

    /**
     * Returns the frame with the monitor
     */
    public JFrame createFrame() {
        return createFrame(Messages.getString("SensorMonitor.title"));
    }

    /**
     * Shows the command
     *
     * @param command the command
     */
    public void onCommand(RobotCommands command) {
        if (command.isMove()) {
            printf(MOVE_HEAD, "%4d", command.moveDirection);
            printf(MOVE_SPEED, "%3d", command.speed);
        }
    }

    /**
     * Shows the controller
     *
     * @param status the status
     */
    public void onControllerStatus(String status) {
        printf(CONTROLLER_STATUS_KEY, ControllerStatusMapper.map(status));
    }

    /**
     * Shows the reward
     *
     * @param reward the reward
     */
    public void onReward(double reward) {
        printf(REWARD_KEY, "%6.2f", reward);
    }

    /**
     * Shows the roboto status
     *
     * @param status the robot status
     */
    public void onStatus(RobotStatus status) {
        printf(HEAD_KEY, "%4d", status.getDirection());
        printf(SENSOR_DIR_KEY, "%4d", status.getSensorDirection());
        printf(ECHO_DIST_KEY, "%3.1f", status.getEchoDistance());
        printf(LEFT_PPS_KEY, "%3.0f", status.getLeftPps());
        printf(RIGHT_PPS_KEY, "%3.0f", status.getRightPps());
        printf(CAN_MOVE_FORWARD_KEY, status.canMoveForward() ? "-" : "F");
        printf(CAN_MOVE_BACKWARD_KEY, status.canMoveBackward() ? "-" : "B");
        printf(CONTACTS_KEY, "%1X", status.getContacts());
        printf(X_LOCATION_KEY, "%6.2f", status.getLocation().getX());
        printf(Y_LOCATION_KEY, "%6.2f", status.getLocation().getY());
        printf(IMU_FAILURE_KEY, "%3d", status.getImuFailure());
        printf(SUPPLY_KEY, "%4.1f", status.getSupplyVoltage());
        printf(LEFT_TARGET_PPS_KEY, "%3d", status.getLeftTargetPps());
        printf(RIGHT_TARGET_PPS_KEY, "%3d", status.getRightTargetPps());
        printf(LEFT_POWER_KEY, "%4d", status.getLeftPower());
        printf(RIGHT_POWER_KEY, "%4d", status.getRightPower());
    }
}
