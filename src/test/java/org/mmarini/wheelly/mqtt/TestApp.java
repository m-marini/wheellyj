/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.mqtt;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.TimeUnit;

import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;

public class TestApp {
    private static final Logger logger = LoggerFactory.getLogger(TestApp.class);
    public static final String BROKER_URL = "tcp://localhost:1883";
    public static final String USER_NAME = "wheellyj";
    public static final String PASSWORD = "wheellyj";
    public static final int CAMERA_INTERVAL = 500;
    public static final int CAMERA_TIMEOUT = 5000;

    public static void main(String[] args) {
        new TestApp().run();
    }

    private final JFrame frame;
    private final MqttRobot robot;

    public TestApp() {
        this.frame = new JFrame("Test");
        try {
            this.robot = MqttRobot.create(BROKER_URL, null, USER_NAME, PASSWORD,
                    "wheelly", "wheellycam", "wheellyqr",
                    5000, 3000, CAMERA_INTERVAL, CAMERA_TIMEOUT, DEFAULT_ROBOT_SPEC
                    , new String[0], new String[0]);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    private void run() {

        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                logger.atDebug().log("Window closed");
                frame.dispose();
                robot.close();
            }
        });

        robot.connect();

        frame.setVisible(true);
        Flowable.interval(1000, TimeUnit.MILLISECONDS, Schedulers.computation())
                .subscribe(i ->
                        logger.atDebug().log("Time {}", i));
        logger.atDebug().log("Run completed");
    }
}
