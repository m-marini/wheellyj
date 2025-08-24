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

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotStatusApi;
import org.mmarini.wheelly.mqtt.MqttRobot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitor the communication activities
 */
public class ComMonitor extends MatrixTable {

    public static final String ERROR_KEY = "error";
    public static final String CONTROLLER_KEY = "controller";
    public static final String SENSORS_KEY = "sensors";
    public static final String COMMANDS_KEY = "commands";

    private static final Logger logger = LoggerFactory.getLogger(ComMonitor.class);
    private static final Pattern NAME_PATTERN = Pattern.compile("^([^/]+)/[^/]+/[^/]+/[^/]+/(.*)$");

    /**
     * Creates the monitor
     */
    public ComMonitor() {
        addColumn(CONTROLLER_KEY, Messages.getString("ComMonitor.controller"), 3).setScrollOnChange(true);
        addColumn(COMMANDS_KEY, Messages.getString("ComMonitor.commands"), 20);
        addColumn(SENSORS_KEY, Messages.getString("ComMonitor.sensors"), 60);
        addColumn(ERROR_KEY, Messages.getString("ComMonitor.error"), 80);
        setPrintTimestamp(false);
    }

    /**
     * Add the robot to monitor
     *
     * @param robot the robot
     */
    public void addRobot(MqttRobot robot) {
        robot.readRobotStatus()
                .filter(RobotStatusApi::connected)
                .firstElement()
                .subscribe(
                        ignoerd -> {
                            robot.robotDevice().readAllTopics()
                                    .subscribe(this::onMessage);
                            robot.qrCameraDevice().readAllTopics()
                                    .subscribe(this::onMessage);
                        }
                );
    }

    /**
     * Returns the frame containing the com monitor
     */
    public JFrame createFrame() {
        return createFrame(Messages.getString("ComMonitor.title"));
    }

    /**
     * Notifies the controller status
     *
     * @param status the controller status
     */
    public void onControllerStatus(String status) {
        printf(CONTROLLER_KEY, status);
    }

    /**
     * Notifies the error
     *
     * @param err the error
     */
    public void onError(Throwable err) {
        printf(ERROR_KEY, String.valueOf(err.getMessage()));
        logger.atError().setCause(err).log("Error message");
    }

    /**
     * Notifies the message
     *
     * @param message the message (topic, message)
     */
    public void onMessage(Tuple2<String, MqttMessage> message) {
        logger.atDebug().log("message from {}", message._1);
        Matcher m = NAME_PATTERN.matcher(message._1);
        if (m.matches()) {
            String key = "cmd".equals(m.group(1))
                    ? COMMANDS_KEY : SENSORS_KEY;
            String raw = m.group(2) + " " + new String(message._2.getPayload());
            printf(key, raw);
        }
    }
}
