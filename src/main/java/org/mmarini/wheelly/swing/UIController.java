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

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.*;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.lang.String.format;
import static org.mmarini.wheelly.swing.RxJoystick.NONE_CONTROLLER;
import static org.mmarini.yaml.Utils.fromFile;

/**
 *
 */
public class UIController {
    private static final String CONFIG_FILE = ".wheelly.yml";
    private static final Logger logger = LoggerFactory.getLogger(UIController.class);
    private static final int FORWARD_DIRECTION = 0;

    /**
     *
     */
    public static UIController create() {
        return new UIController();
    }

    private final PreferencesPane preferencesPane;
    private final WheellyFrame frame;
    private final Dashboard dashboard;
    private final Radar radar;
    private final GlobalMap globalMap;
    private String joystickPort;
    private JsonNode configNode;
    private BehaviorEngine behaviorEngine;
    private ConfigParameters configParams;

    /**
     *
     */
    protected UIController() {
        this.joystickPort = NONE_CONTROLLER;
        this.frame = new WheellyFrame();
        this.preferencesPane = new PreferencesPane();
        this.dashboard = frame.getDashboard();
        this.radar = frame.getRadar();
        this.globalMap = frame.getGlobalMap();

        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setSize(1024, 800);
        this.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                handleWindowOpened();
            }
        });
    }

    /**
     *
     */
    private UIController buildFlow() {
        SwingObservable.actions(frame.getPreferences())
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handlePreferences)
                .subscribe();
        dashboard.getResetFlow()
                .doOnNext(ev -> closeConfig()
                        .openConfig())
                .subscribe();
        return this;
    }

    /**
     * Returns the UIController with current configuration closed
     */
    private UIController closeConfig() {
        if (behaviorEngine != null) {
            behaviorEngine.close();
            behaviorEngine = null;
        }
        return this;
    }

    /**
     * Returns the inference engine
     */
    private InferenceEngine createEngine() {
        String engineRef = Yaml.engine(configNode);
        JsonPointer ptr = JsonPointer.valueOf(engineRef);
        JsonNode engineNode = configNode.at(ptr);
        if (engineNode.isMissingNode()) {
            throw new IllegalArgumentException(format("Missing %s node", engineRef));
        }
        String className = engineNode.path("class").asText();
        if (className.isEmpty()) {
            throw new IllegalArgumentException(format("Missing %s/class node", engineRef));
        }
        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            Method creator = clazz.getDeclaredMethod("create", JsonNode.class);
            int modifiers = creator.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException("creator method is not static");
            }
            Object result = creator.invoke(null, engineNode);
            return (InferenceEngine) result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleCps(Timed<Integer> cps) {
        dashboard.setCps(cps.value());
    }

    private void handleMapMessage(ScannerMap map) {
        radar.setObstacles(map.getObstacles());
        globalMap.setObstacles(map.getObstacles());
    }

    /**
     * @param actionEvent the event
     */
    private void handlePreferences(ActionEvent actionEvent) {
        preferencesPane.setJoystickPort(joystickPort)
                .setBaseUrl("");
        int result = JOptionPane.showConfirmDialog(
                frame, preferencesPane, "Preferences",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            joystickPort = preferencesPane.getJoystickPort();
        }
    }

    /**
     * Handles the status message
     *
     * @param tStatus the status
     */
    private void handleStatusMessage(Timed<WheellyStatus> tStatus) {
        WheellyStatus status = tStatus.value();
        ProxySample sample = status.sample;
        if (sample.sensorRelativeDeg == FORWARD_DIRECTION) {
            dashboard.setObstacleDistance(sample.distance);
        }
        dashboard.setPower(status.voltage);
        dashboard.setMotors(status.motors._1, status.motors._2);
        dashboard.setForwardBlock(!status.canMoveForward);
        RobotAsset robotAsset = sample.robotAsset;
        dashboard.setAngle(robotAsset.getDirectionRad());
        dashboard.setRobotLocation(robotAsset.location);
        radar.setAsset(robotAsset.location, status.sample.robotAsset.getDirectionRad());
        globalMap.setAsset(robotAsset.location, robotAsset.getDirectionRad());
        dashboard.setImuFailure(status.imuFailure ? 1 : 0);
    }

    /**
     * Handles the application on window open.
     * Opens current configuration
     */
    private void handleWindowOpened() {
        openConfig();
    }

    /**
     * @throws IOException in case of error
     */
    private void loadConfig() throws IOException {
        this.configNode = fromFile(CONFIG_FILE);
        Yaml.config().apply(Locator.root()).accept(configNode);
        configParams = Yaml.configParams(configNode);
    }

    /**
     * Returns the UIController with current configuration opened
     */
    private void openConfig() {
        try {
            logger.debug("openConfig");
            InferenceEngine engine = createEngine();
            this.behaviorEngine = BehaviorEngine.create(configParams, engine);

            behaviorEngine.readConnection()
                    .subscribe(connected -> {
                        dashboard.setWifiLed(connected);
                        frame.log(connected ? "Connected" : "Disconnected");
                    });

            behaviorEngine.readErrors()
                    .subscribe(ex -> {
                        logger.error("Error on flow", ex);
                        frame.log(ex.getMessage());
                    });

            // Creates status flow
            behaviorEngine.readStatus()
                    .subscribe(this::handleStatusMessage);

            behaviorEngine.readMapFlow()
                    .subscribe(this::handleMapMessage);

            behaviorEngine.readCps()
                    .subscribe(this::handleCps);

            behaviorEngine.start();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            frame.log(ex.getMessage());
        }
    }

    /**
     * Starts the application
     * Sets the window visible
     * Initializes the default configuration
     */
    public void start() {
        try {
            loadConfig();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
        buildFlow();
        frame.setVisible(true);
    }
}
