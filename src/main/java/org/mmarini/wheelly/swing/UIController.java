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
import io.reactivex.Flowable;
import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.rxjava3.core.BackpressureStrategy;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.Math.*;
import static org.mmarini.Utils.getValue;
import static org.mmarini.wheelly.swing.RxJoystick.NONE_CONTROLLER;
import static org.mmarini.yaml.Utils.fromFile;

/**
 *
 */
public class UIController {
    public static final String DEFAULT_BASE_URL = "http://192.168.1.10/api/v1/wheelly";
    private static final String CONFIG_FILE = ".wheelly.yml";
    private static final Logger logger = LoggerFactory.getLogger(UIController.class);

    private static final long REMOTE_CLOCK_PERIOD = 60;   // Seconds
    private static final int REMOTE_CLOCK_SAMPLES = 10;
    private static final long STATUS_INTERVAL = 200; // Millis
    private static final long MOTOR_PERIOD = 100;
    private static final long MOTOR_INTERVAL = 100;

    /**
     *
     */
    public static UIController create() {
        return new UIController();
    }

    /**
     * @param x
     * @param y
     */
    static MotorCommand.Direction toDir(float x, float y) {
        double dir = atan2(y, x) + Math.PI;
        double deg = toDegrees(dir);
        int i = ((int) round(dir * 8 / Math.PI)) % 16;
        int d = ((i + 13) / 2) % 8;
        return MotorCommand.Direction.values()[d + 1];
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

    /**
     * @param motorCommands
     */
    private static boolean isPassingCommand(List<MotorCommand> motorCommands) {
        MotorCommand m0 = motorCommands.get(0);
        MotorCommand m1 = motorCommands.get(1);
        return m0.isRunning() || m1.isRunning();
    }

    /**
     * @param xy
     */
    private static MotorCommand toMotorCommand(Tuple2<Float, Float> xy) {
        float x = xy._1;
        float y = xy._2;
        float speed = (float) sqrt(x * x + y * y);
        MotorCommand.Direction direction = MotorCommand.Direction.NONE;
        if (speed > 0.1f) {
            direction = toDir(x, y);
        }
        return MotorCommand.empty().setDirection(direction).setSpeed(speed);
    }

    private final PreferencesPane preferencesPane;
    private final WheellyFrame frame;
    private final Dashboard dashboard;
    private final Radar radar;
    private RxJoystick joystick;
    private RxController wheellyController;
    private String baseUrl;
    private String joystickPort;
    private BehaviorProcessor<RemoteClock> remoteClock;

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
     * Handles the application on window open.
     * Opens current configuration
     */
    private void handleWindowOpened() {
        openConfig();
    }

    /**
     * Returns the UIController with current configuration opened
     */
    private void openConfig() {
        this.wheellyController = RxController.create(baseUrl);
        this.joystick = RxJoystick.create(joystickPort);

        this.remoteClock = BehaviorProcessor.create();
        wheellyController.remoteClock(REMOTE_CLOCK_PERIOD, TimeUnit.SECONDS, REMOTE_CLOCK_SAMPLES)
                .subscribe(remoteClock);

        Flowable<StatusBody> statusFlow = Flowable.interval(0, STATUS_INTERVAL, TimeUnit.MILLISECONDS)
                .flatMap(x -> wheellyController.status());
        Flowable<StatusBody> scanCommand = Flowable.mergeArray(
                        joystick.getValues(RxJoystick.BUTTON_0),
                        joystick.getValues(RxJoystick.BUTTON_1),
                        joystick.getValues(RxJoystick.BUTTON_2),
                        joystick.getValues(RxJoystick.BUTTON_3))
                .filter(x -> x > 0f)
                .flatMap(x -> wheellyController.scan());

        Flowable<MotorCommand> joystickCommand = joystick.getXY()
                .map(UIController::toMotorCommand);
        Flowable<StatusBody> motorCommand = Flowable.interval(0, MOTOR_PERIOD, TimeUnit.MILLISECONDS)
                .withLatestFrom(joystickCommand, (t, cmd) -> cmd.setTick(t))
                .buffer(2, 1)
                .filter(UIController::isPassingCommand)
                .map(x -> x.get(1))
                .withLatestFrom(remoteClock, (cmd, clock) -> cmd.setClock(clock))
                .flatMap(cmd ->
                        wheellyController.moveTo(
                                cmd.getLeftSpeed(),
                                cmd.getRightSpeed(),
                                cmd.clock.toRemote(Instant.now().plusMillis(MOTOR_INTERVAL))));

        Flowable.merge(statusFlow, scanCommand, motorCommand)
                .withLatestFrom(remoteClock, RxController::toStatus)
                .doOnNext(s -> {
                    dashboard.setPower(s.voltage.value);
                    dashboard.setObstacleDistance(
                            getValue(s.obstacles, 90)
                                    .map(InstantValue::get)
                                    .orElse(400));
                    dashboard.setMotors(s.direction.value._1, s.direction.value._2);
                    dashboard.setForwardBlock(isBlockDistance(s));
                    radar.setSamples(s.obstacles);
                })
                .subscribe();
    }

    /**
     * Returns the UIController with current configuration closed
     */
    private UIController closeConfig() {
        remoteClock.onComplete();
        joystick.close();
        return this;
    }

    /**
     *
     */
    private UIController buildFlow() {
        SwingObservable.actions(frame.getPreferences())
                .doOnNext(x -> logger.debug("{]", x))
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handlePreferences)
                .subscribe();
        return this;
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
