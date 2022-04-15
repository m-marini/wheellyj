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
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.model.AbstractScannerMap.THRESHOLD_DISTANCE;
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
    private final GlobalMap globalMap;
    private String host;
    private String joystickPort;
    private Disposable statusDisposable;
    private Disposable connectionDisposable;
    private Disposable errorDisposable;
    private Disposable elapsDisposable;
    private Disposable proxyDisposable;
    private int port;
    private final ScannerMap map;
    private InferenceEngine engine;
    private JsonNode configNode;

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
        this.globalMap = frame.getGlobalMap();
        this.map = GridScannerMap.create(List.of(), THRESHOLD_DISTANCE);

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
                throw new IllegalArgumentException(format("creator method is not static", engineRef));
            }
            Object result = creator.invoke(null, engineNode);
            return (InferenceEngine) result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
//        return new ManualEngine(RxJoystickImpl.create(joystickPort));
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
        if (value.sensorRelativeDeg == FORWARD_DIRECTION) {
            dashboard.setObstacleDistance(value.distance);
        }
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
        dashboard.setAngle(status.asset.value().getDirectionRad());
        radar.setAsset(status.asset.value().getLocation(), status.asset.value().getDirectionRad());
        globalMap.setAsset(status.asset.value().getLocation(), status.asset.value().getDirectionRad());
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
        //joystickPort = Yaml.joystickPort(node);
        host = Yaml.host(configNode);
        port = Yaml.port(configNode);
    }

    /**
     * Returns the UIController with current configuration opened
     */
    private void openConfig() {
        try {
            RobotController controller = RawController.create(host, port);
            this.engine = createEngine();

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

            // Creates status flow
            Flowable<WheellyStatus> statusFlow = controller.readStatus()
                    .doOnNext(this::handleStatusMessage);

            // Creates map flow
            Flowable<ScannerMap> mapFlow = controller.readProxy()
                    .doOnNext(this::handleProxySample)
                    .doOnNext(dashboard::setProxy)
                    .scanWith(() -> GridScannerMap.create(List.of(), THRESHOLD_DISTANCE),
                            ScannerMap::process)
                    .cast(ScannerMap.class)
                    .doOnNext(map -> {
                        radar.setObstacles(map.getObstacles());
                        globalMap.setObstacles(map.getObstacles());
                    });

            // Builds commands flow
            Flowable<Tuple2<MotorCommand, Integer>> commands = combineLatest(statusFlow, mapFlow, Tuple2::of)
                    .map(engine::process)
                    .publish()
                    .autoConnect();

            // Splits the commands  flow in motor and scanner command flows
            Flowable<MotorCommand> motorComandFlow = commands.map(Tuple2::getV1)
                    .sample(100, TimeUnit.MILLISECONDS);

            Flowable<Integer> scanCommand1Flow = commands.map(Tuple2::getV2)
                    .distinctUntilChanged();
            Flowable<Integer> scanCommandFlow = combineLatest(interval(100, TimeUnit.MILLISECONDS), scanCommand1Flow, (t, a) -> a)
                    .buffer(2, 1)
                    .filter(list -> !(list.get(0) == 0 && list.get(1) == 0))
                    .concatMap(list -> list.get(1) == 0
                            ? fromIterable(list)
                            : just(list.get(0)))
                    .doOnNext(a -> logger.debug("scan {}", a));

            controller.activateMotors(motorComandFlow)
                    .scan(scanCommandFlow)
                    .start();
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
