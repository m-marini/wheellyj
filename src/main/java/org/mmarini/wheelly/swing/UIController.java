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
import org.mmarini.wheelly.model.InstantValue;
import org.mmarini.wheelly.model.RxController;
import org.mmarini.wheelly.model.WheellyStatus;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.mmarini.Utils.getValue;
import static org.mmarini.wheelly.swing.RxJoystick.NONE_CONTROLLER;
import static org.mmarini.yaml.Utils.fromFile;

/**
 *
 */
public class UIController {
    public static final String DEFAULT_BASE_URL = "http://192.168.1.10/api/v1/wheelly";
    public static final int ELAPS_COUNT = 10;
    private static final String CONFIG_FILE = ".wheelly.yml";
    private static final Logger logger = LoggerFactory.getLogger(UIController.class);
    public static final int MAX_DISTANCE = 400;
    public static final int FORWARD_DIRECTION = 90;

    /**
     *
     */
    public static UIController create() {
        return new UIController();
    }

    /**
     * @param s
     * @return
     */
    private static boolean isBlockDistance(WheellyStatus s) {
        return IntStream.of(75, 90, 105)
                .boxed()
                .map(getValue(s.obstacles))
                .flatMap(Optional::stream)
                .map(InstantValue::getValue)
                .filter(x -> x > 0 && x <= Dashboard.STOP_DISTANCE)
                .findAny()
                .isPresent();
    }

    private final PreferencesPane preferencesPane;
    private final WheellyFrame frame;
    private final Dashboard dashboard;
    private final Radar radar;
    private String baseUrl;
    private String joystickPort;
    private FlowBuilder flowBuilder;
    private Disposable statusDisposable;
    private Disposable connectionDisposable;
    private Disposable errorDisposable;
    private Disposable elapsDisposable;

    /**
     *
     */
    protected UIController() {
        this.joystickPort = NONE_CONTROLLER;
        this.baseUrl = DEFAULT_BASE_URL;
        this.frame = new WheellyFrame();
        this.preferencesPane = new PreferencesPane();
        this.dashboard = frame.getDashboard();
        this.radar = frame.getRadar();

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
        flowBuilder.detach();
        return this;
    }

    /**
     * @param actionEvent the event
     */
    private void handlePreferences(ActionEvent actionEvent) {
        preferencesPane.setJoystickPort(joystickPort)
                .setBaseUrl(baseUrl);
        int result = JOptionPane.showConfirmDialog(
                frame, preferencesPane, "Preferences",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            joystickPort = preferencesPane.getJoystickPort();
            baseUrl = preferencesPane.getBaseUrl();
            saveConfig();
        }
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
        baseUrl = Yaml.baseUrl(node);
    }

    /**
     * Returns the UIController with current configuration opened
     */
    private void openConfig() {
        this.flowBuilder = FlowBuilder.create(RxController.create(baseUrl), RxJoystick.create(joystickPort)).build();
        this.statusDisposable = flowBuilder.getStatus()
                .doOnNext(s -> {
                    dashboard.setPower(s.voltage.value);
                    dashboard.setObstacleDistance(
                            getValue(s.obstacles, FORWARD_DIRECTION)
                                    .map(InstantValue::get)
                                    .orElse(MAX_DISTANCE));
                    dashboard.setMotors(s.direction.value._1, s.direction.value._2);
                    dashboard.setForwardBlock(isBlockDistance(s));
                    dashboard.setCps(s.cps.value);
                    radar.setSamples(s.obstacles);
                }).subscribe();
        this.connectionDisposable = flowBuilder.getConnection()
                .distinctUntilChanged()
                .doOnNext(connected -> {
                    dashboard.setWifiLed(connected);
                    frame.log(connected ? "Connected" : "Disconnected");
                })
                .subscribe();
        this.errorDisposable = flowBuilder.getErrors()
                .doOnNext(ex -> {
                    logger.error("Error on flow", ex);
                    frame.log(ex.getMessage());
                })
                .subscribe();
        this.elapsDisposable = flowBuilder.getElaps()
                .window(ELAPS_COUNT, 1, ELAPS_COUNT)
                .flatMap(list -> list.toList().toFlowable())
                .map(list -> {
                    long sum = list.stream()
                            .mapToLong(x -> x)
                            .sum();
                    return (double) sum / list.size();
                })
                .doOnNext(dashboard::setElapsed)
                .subscribe();

    }

    /**
     *
     */
    private void saveConfig() {
        try {
            Yaml.saveConfig(CONFIG_FILE, joystickPort, baseUrl);
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
