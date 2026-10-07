/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.HeadStatus;
import org.mmarini.wheelly.apis.MotionStatus;
import org.mmarini.wheelly.apis.RobotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

/**
 * Displays the robot status, controller status, and robot command parameters
 * in a real-time matrix grid table layout.
 */
public class SensorMonitor extends MatrixTable {

    /**
     * The unique column identifier key assigned to display the robot heading bearing.
     */
    public static final String HEADING_KEY = "head";

    /**
     * The unique column identifier key assigned to display the sensor head alignment direction.
     */
    public static final String SENSOR_DIR_KEY = "sensorDir";

    /**
     * The unique column identifier key assigned to display the front lidar range distance.
     */
    public static final String FRONT_DIST_KEY = "distance";

    /**
     * The unique column identifier key assigned to display the rear lidar range distance.
     */
    public static final String REAR_DIST_KEY = "rearDistance";

    /**
     * The unique column identifier key assigned to display parsed QR code data strings.
     */
    public static final String QR_CODE_KEY = "qrCode";

    /**
     * The unique column identifier key assigned to display the calculated robot position along the X axis.
     */
    public static final String X_LOCATION_KEY = "xLocation";

    /**
     * The unique column identifier key assigned to display the calculated robot position along the Y axis.
     */
    public static final String Y_LOCATION_KEY = "yLocation";

    /**
     * The unique column identifier key assigned to display the boolean forward clearance indicator status.
     */
    public static final String CAN_MOVE_FORWARD_KEY = "canMoveForward";

    /**
     * The unique column identifier key assigned to display the boolean backward clearance indicator status.
     */
    public static final String CAN_MOVE_BACKWARD_KEY = "canMoveBackward";

    /**
     * The unique column identifier key assigned to display the raw speed output pulses of the left motor.
     */
    public static final String LEFT_PPS_KEY = "leftPps";

    /**
     * The unique column identifier key assigned to display the raw speed output pulses of the right motor.
     */
    public static final String RIGHT_PPS_KEY = "rightPps";

    /**
     * The unique column identifier key assigned to display the active flag indicators signalling an IMU component failure.
     */
    public static final String IMU_FAILURE_KEY = "imuFailure";

    /**
     * The unique column identifier key assigned to display textual operating status sequences from the controller.
     */
    public static final String CONTROLLER_STATUS_KEY = "controllerStatus";

    /**
     * The unique column identifier key assigned to display the internal power supply voltage readings.
     */
    public static final String SUPPLY_KEY = "supply";

    /**
     * The unique column identifier key assigned to display outgoing sensor head tracking alignment profiles.
     */
    public static final String SCAN_HEAD_KEY = "moveHead";

    /**
     * The unique column identifier key assigned to display the current relative calculated feedback reward score value.
     */
    public static final String REWARD_KEY = "reward";

    /**
     * The unique column identifier key assigned to display the raw electronic power values assigned to the left motor.
     */
    public static final String LEFT_POWER_KEY = "leftPower";

    /**
     * The unique column identifier key assigned to display the raw electronic power values assigned to the right motor.
     */
    public static final String RIGHT_POWER_KEY = "rightPower";

    /**
     * The unique column identifier key assigned to display the targeted pulse setpoints configuration for the left motor.
     */
    public static final String LEFT_TARGET_PPS_KEY = "leftTargetPps";

    /**
     * The unique column identifier key assigned to display the targeted pulse setpoints configuration for the right motor.
     */
    public static final String RIGHT_TARGET_PPS_KEY = "rightTargetPps";

    /**
     * The unique column identifier key assigned to display dispatched movement objective properties.
     */
    public static final String MOVE_KEY = "move";

    /**
     * The unique column identifier key assigned to display active chassis orientation/motion state descriptions.
     */
    public static final String MOTION_STATUS_KEY = "motionStatus";

    /**
     * The unique column identifier key assigned to display active sensor head tracking behaviour state descriptions.
     */
    public static final String HEAD_STATUS_KEY = "headStatus";

    /**
     * The unique column identifier key assigned to display front bumper physical contact switch readings.
     */
    private static final String FRONT_CONTACT_KEY = "frontContact";

    /**
     * The unique column identifier key assigned to display rear bumper physical contact switch readings.
     */
    private static final String REAR_CONTACT_KEY = "rearContact";

    private static final Logger logger = LoggerFactory.getLogger(SensorMonitor.class);

    /**
     * Initialises a new {@link SensorMonitor} panel instance, structuring all data column properties,
     * loading associated translation labels, and establishing default auto-scrolling behaviours.
     */
    public SensorMonitor() {
        List.of(
                        addColumn(X_LOCATION_KEY, Messages.getString("SensorMonitor.xLocation"), 5),
                        addColumn(Y_LOCATION_KEY, Messages.getString("SensorMonitor.yLocation"), 5),
                        addColumn(HEADING_KEY, Messages.getString("SensorMonitor.head"), 4),
                        addColumn(MOTION_STATUS_KEY, Messages.getString("SensorMonitor.motionStatus"), 13),
                        addColumn(MOVE_KEY, Messages.getString("SensorMonitor.move"), 13),

                        addColumn(SENSOR_DIR_KEY, Messages.getString("SensorMonitor.sensorDir"), 3),
                        addColumn(FRONT_DIST_KEY, Messages.getString("SensorMonitor.frontDistance"), 4),
                        addColumn(REAR_DIST_KEY, Messages.getString("SensorMonitor.rearDistance"), 4),
                        addColumn(QR_CODE_KEY, Messages.getString("SensorMonitor.qrCode"), 3),
                        addColumn(HEAD_STATUS_KEY, Messages.getString("SensorMonitor.headStatus"), 13),
                        addColumn(SCAN_HEAD_KEY, Messages.getString("SensorMonitor.scanHead"), 13),

                        addColumn(CAN_MOVE_FORWARD_KEY, Messages.getString("SensorMonitor.canMoveForward"), 1),
                        addColumn(CAN_MOVE_BACKWARD_KEY, Messages.getString("SensorMonitor.canMoveBackward"), 1),
                        addColumn(FRONT_CONTACT_KEY, Messages.getString("SensorMonitor.frontContact"), 1),
                        addColumn(REAR_CONTACT_KEY, Messages.getString("SensorMonitor.rearContact"), 1),

                        addColumn(LEFT_PPS_KEY, Messages.getString("SensorMonitor.leftPps"), 3),
                        addColumn(RIGHT_PPS_KEY, Messages.getString("SensorMonitor.rightPps"), 3),
                        addColumn(LEFT_TARGET_PPS_KEY, Messages.getString("SensorMonitor.leftTargetPps"), 3),
                        addColumn(RIGHT_TARGET_PPS_KEY, Messages.getString("SensorMonitor.rightTargetPps"), 3),
                        addColumn(LEFT_POWER_KEY, Messages.getString("SensorMonitor.leftPower"), 4),
                        addColumn(RIGHT_POWER_KEY, Messages.getString("SensorMonitor.rightPower"), 4),

                        addColumn(IMU_FAILURE_KEY, Messages.getString("SensorMonitor.imuFailure"), 3),
                        addColumn(SUPPLY_KEY, Messages.getString("SensorMonitor.supply"), 3),

                        addColumn(REWARD_KEY, Messages.getString("SensorMonitor.reward"), 6),
                        addColumn(CONTROLLER_STATUS_KEY, Messages.getString("SensorMonitor.controllerStatus"), 3))
                .forEach(col -> col.setScrollOnChange(true));
        setPrintTimestamp(false);
        logger.atDebug().log("Created");
    }

    /**
     * Initialises and builds a top-level graphical frame wrapper containing this monitor panel component.
     *
     * @return the newly created and structured {@link JFrame} container window
     */
    public JFrame createFrame() {
        return createFrame(Messages.getString("SensorMonitor.title"));
    }

    /**
     * Updates the user interface display with the current operational state string logs emitted by the controller.
     *
     * @param status the raw text status information detail received from the controller
     */
    public void onControllerStatus(String status) {
        printf(CONTROLLER_STATUS_KEY, status);
    }

    /**
     * Updates the user interface display layout to capture changes to the designated sensor head tracking profile command.
     *
     * @param status the newly dispatched {@link HeadStatus} command configuration profile
     */
    public void onHeadStatusCommand(HeadStatus status) {
        printf(SCAN_HEAD_KEY, status.toString());
    }

    /**
     * Updates the user interface display layout to reflect the target movement parameters dispatched by the controller.
     *
     * @param status the newly dispatched {@link MotionStatus} chassis objective properties
     */
    public void onMotionStatus(MotionStatus status) {
        printf(MOVE_KEY, status.toString());
    }

    /**
     * Updates the user interface display layout to format and present the latest feedback evaluation reward metric.
     *
     * @param reward the raw calculated reward score value
     */
    public void onReward(double reward) {
        printf(REWARD_KEY, "%6.2f", reward);
    }

    /**
     * Extracts and maps a complete live telemetry data packet frame from the robot, converting
     * positions, active states, motor metrics, distances, and contact switches into localised string structures.
     *
     * @param status the newly received {@link RobotStatus} telemetry data structure
     */
    public void onStatus(RobotStatus status) {
        printf(X_LOCATION_KEY, "%6.2f", status.location().getX());
        printf(Y_LOCATION_KEY, "%6.2f", status.location().getY());
        printf(HEADING_KEY, "%4d", status.direction().toIntDeg());

        switch (status.motionMessage().status()) {
            case HALT -> printf(MOTION_STATUS_KEY, "ha");
            case FORWARD -> printf(MOTION_STATUS_KEY, "fw %.2f,%.2f",
                    status.motionMessage().target().getX(),
                    status.motionMessage().target().getY());
            case BACKWARD -> printf(MOTION_STATUS_KEY, "bw %.2f,%.2f",
                    status.motionMessage().target().getX(),
                    status.motionMessage().target().getY());
            case ROTATE -> printf(MOTION_STATUS_KEY, "ro %4d",
                    status.motionMessage().targetDeg());
            default -> printf(MOTION_STATUS_KEY, status.motionMessage().status().name());
        }

        printf(SENSOR_DIR_KEY, "%4d", status.headDirection().toIntDeg());
        printf(FRONT_DIST_KEY, "%4d", status.lidarMessage().frontDistance());
        printf(REAR_DIST_KEY, "%4d", status.lidarMessage().rearDistance());
        printf(QR_CODE_KEY, status.qrCode());

        switch (status.lidarMessage().trackingState()) {
            case FRONT_TRACK -> printf(HEAD_STATUS_KEY, "ft %.2f,%.2f",
                    status.lidarMessage().target().getX(),
                    status.lidarMessage().target().getY());
            case REAR_TRACK -> printf(HEAD_STATUS_KEY, "rt %.2f,%.2f",
                    status.lidarMessage().target().getX(),
                    status.lidarMessage().target().getY());
            case FIX_DIRECTION -> printf(HEAD_STATUS_KEY, "sc %4d",
                    status.lidarMessage().headDirectionDeg());
            default -> printf(HEAD_STATUS_KEY, status.lidarMessage().trackingState().name());
        }

        printf(CAN_MOVE_FORWARD_KEY, status.canMoveForward() ? "-" : "F");
        printf(CAN_MOVE_BACKWARD_KEY, status.canMoveBackward() ? "-" : "B");
        printf(FRONT_CONTACT_KEY, status.frontSensor() ? "-" : "F");
        printf(REAR_CONTACT_KEY, status.rearSensor() ? "-" : "R");

        printf(LEFT_PPS_KEY, "%3.0f", status.leftPps());
        printf(RIGHT_PPS_KEY, "%3.0f", status.rightPps());
        printf(LEFT_TARGET_PPS_KEY, "%3d", status.leftTargetPps());
        printf(RIGHT_TARGET_PPS_KEY, "%3d", status.rightTargetPps());
        printf(LEFT_POWER_KEY, "%4d", status.leftPower());
        printf(RIGHT_POWER_KEY, "%4d", status.rightPower());

        printf(IMU_FAILURE_KEY, "%3d", status.imuFailure());
        printf(SUPPLY_KEY, "%4.1f", status.supplyVoltage());
    }
}
