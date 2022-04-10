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

import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.*;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static org.mmarini.wheelly.swing.RxJoystick.NONE_CONTROLLER;
import static org.mmarini.yaml.Utils.fromFile;

/**
 *
 */
public class UIController {
    public static final String DEFAULT_BASE_URL = "http://192.168.1.10/api/v1/wheelly";
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
    private String host;
    private String joystickPort;
    private FlowBuilder flowBuilder;
    private Disposable statusDisposable;
    private Disposable connectionDisposable;
    private Disposable errorDisposable;
    private Disposable elapsDisposable;
    private Disposable proxyDisposable;
    private int port;
    private ScannerMap map;

    /**
     *
     */
    protected UIController() {
        this.joystickPort = NONE_CONTROLLER;
        this.host = DEFAULT_BASE_URL;
        this.frame = new WheellyFrame();
        this.preferencesPane = new PreferencesPane();
        this.dashboard = frame.getDashboard();
        this.radar = frame.getRadar();
        this.map = ScannerMap.create(List.of());

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
                .doOnNext(ev -> {
                    closeConfig();
                    openConfig();
                })
                .subscribe();
        return this;
    }

    /**
     * Returns the UIController with current configuration closed
     */
    private UIController closeConfig() {
        if (statusDisposable != null) {
            statusDisposable.dispose();
            statusDisposable = null;
        }
        if (errorDisposable != null) {
            errorDisposable.dispose();
            errorDisposable = null;
        }
        if (connectionDisposable != null) {
            connectionDisposable.dispose();
            connectionDisposable = null;
        }
        if (elapsDisposable != null) {
            elapsDisposable.dispose();
            elapsDisposable = null;
        }
        if (proxyDisposable != null) {
            proxyDisposable.dispose();
            proxyDisposable = null;
        }
        flowBuilder.detach();
        return this;
    }

    /**
     * @param actionEvent the event
     */
    private void handlePreferences(ActionEvent actionEvent) {
        preferencesPane.setJoystickPort(joystickPort)
                .setBaseUrl(host);
        int result = JOptionPane.showConfirmDialog(
                frame, preferencesPane, "Preferences",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            joystickPort = preferencesPane.getJoystickPort();
            host = preferencesPane.getBaseUrl();
            saveConfig();
        }
    }

    /**
     * @param sample
     */
    private void handleProxySample(Timed<ProxySample> sample) {
        dashboard.setProxy(sample);
        ProxySample value = sample.value();
        if (value.relativeDirection == FORWARD_DIRECTION) {
            dashboard.setObstacleDistance(value.distance);
        }
        map = map.process(sample);
        radar.setObstacles(map.obstacles);
    }

    /**
     * Handles the status message
     *
     * @param status the status
     */
    private void handleStatusMessage(WheellyStatus status) {
        dashboard.setPower(status.voltage.value());
        dashboard.setMotors(status.motors.value()._1, status.motors.value()._2);
        dashboard.setCps(status.cps.value());
        dashboard.setForwardBlock(!status.canMoveForward.value());
        dashboard.setAngle(status.asset.value().getRadDirection());
        radar.setAsset(status.asset.value().getLocation(), status.asset.value().getRadDirection());
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
        JsonNode node = fromFile(CONFIG_FILE);
        Yaml.config().apply(Locator.root()).accept(node);
        joystickPort = Yaml.joystickPort(node);
        host = Yaml.host(node);
        port = Yaml.port(node);
    }

    /**
     * Returns the UIController with current configuration opened
     */
    private void openConfig() {
        try {
            RobotController controller = RawController.create(host, port);
            this.flowBuilder = FlowBuilder.create(controller, RxJoystickImpl.create(joystickPort)).build();
            this.statusDisposable = controller.readStatus()
                    .doOnNext(this::handleStatusMessage)
                    .subscribe();
            this.connectionDisposable = controller.readConnection()
                    .doOnNext(connected -> {
                        dashboard.setWifiLed(connected);
                        frame.log(connected ? "Connected" : "Disconnected");
                    })
                    .subscribe();
            this.errorDisposable = controller.readErrors()
                    .doOnNext(ex -> {
                        logger.error("Error on flow", ex);
                        frame.log(ex.getMessage());
                    })
                    .doOnError(ex -> {
                        logger.error("Error reading errors", ex);
                        frame.log(ex.getMessage());
                    })
                    .subscribe();
            this.proxyDisposable = controller.readProxy()
                    .doOnNext(this::handleProxySample)
                    .doOnNext(dashboard::setProxy)
                    .subscribe();
            controller.start();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            frame.log(ex.getMessage());
        }
    }

    /**
     *
     */
    private void saveConfig() {
        try {
            Yaml.saveConfig(CONFIG_FILE, joystickPort, host, port);
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
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
        } catch (FileNotFoundException e) {
            saveConfig();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
        buildFlow();
        frame.setVisible(true);
    }
}
