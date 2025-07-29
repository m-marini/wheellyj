/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apps;

import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.Observable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.swing.ComMonitor;
import org.mmarini.wheelly.swing.ControllerStatusMapper;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.SensorMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;

/**
 * Application to compute the camera calibration (angle of view)
 */
public class CameraCalibration {
    public static final String MONITOR_SCHEMA_YML = "https://mmarini.org/wheelly/camera-calibration-schema-0.1";
    public static final int SLOW_SENSOR_DIRECTION_STEP = 2;
    public static final int FAST_SENSOR_DIRECTION_STEP = 10;
    private static final Logger logger = LoggerFactory.getLogger(CameraCalibration.class);
    private static final int MAX_SAMPLES_PER_SAMPLING = 3;

    /**
     * Returns the command line arguments parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(CameraCalibration.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run camera calibration.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("cameraCalibration.yml")
                .help("specify yaml configuration file");
        return parser;
    }

    /**
     * Runs the checks
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            new CameraCalibration().init(args).run();
        } catch (ArgumentParserException ignored) {
            System.exit(4);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error running matrix monitor");
            System.exit(4);
        }
    }

    private final JButton reconnectButton;
    private final JFormattedTextField cameraViewAngle;
    private final SensorMonitor sensorMonitor;
    private final ComMonitor comMonitor;
    private final Container commandPanel;
    private final List<Sample> samples;
    private JFrame comFrame;
    private JFrame commandFrame;
    private JFrame sensorFrame;
    private RobotControllerApi controller;
    private Namespace parseArgs;
    private RobotApi robot;
    private Consumer<RobotStatus> currentState;
    private int direction;
    private int samplingCounter;
    private long samplingStart;

    /**
     * Creates the camera calibration application
     */
    public CameraCalibration() {
        this.comMonitor = new ComMonitor();
        this.sensorMonitor = new SensorMonitor();
        this.samples = new ArrayList<>();
        DecimalFormat degFormat = new DecimalFormat("##0");
        this.cameraViewAngle = new JFormattedTextField(degFormat);
        this.reconnectButton = new JButton("Reconnect");
        this.commandPanel = createCommandPanel();

        initListeners();
    }

    /**
     * Returns the command panel
     */
    private Container createCommandPanel() {
        cameraViewAngle.setColumns(5);
        cameraViewAngle.setEditable(false);

        JPanel cameraViewPanel = new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 noweight nofill").add(cameraViewAngle)
                .getContainer();
        cameraViewPanel.setBorder(BorderFactory.createTitledBorder("Camera view angle (DEG)"));

        return new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2 noweight fill").add(cameraViewPanel)
                .modify("at,0,1 insets,10 noweight nofill").add(reconnectButton)
                .getContainer();
    }

    /**
     * Creates the context connections
     */
    private void createConnections() {
        controller.connectRobot(robot);
        controller.setOnInference(this::onInference);
    }

    /**
     * Creates the application context
     *
     * @throws IOException in case of error
     */
    private void createContext() throws IOException {
        JsonNode config = org.mmarini.yaml.Utils.fromFile(parseArgs.getString("config"));
        JsonSchemas.instance().validateOrThrow(config, MONITOR_SCHEMA_YML);
        this.robot = AppYaml.robotFromJson(config);
        this.controller = AppYaml.controllerFromJson(config);

        // Creates the frames
        this.commandFrame = createFrame(Messages.getString("MatrixMonitor.title"), commandPanel);
        this.sensorFrame = sensorMonitor.createFrame();
        this.comFrame = comMonitor.createFrame();

        layHorizontally(commandFrame, sensorFrame, comFrame);
    }

    /**
     * Create the flows
     */
    private void createFlows() {
        controller.readRobotStatus()
                .subscribe(this::onStatus);
        controller.readErrors()
                .subscribe(er -> {
                    comMonitor.onError(er);
                    logger.atError().setCause(er).log("Error:");
                });
        controller.readReadLine()
                .subscribe(this::onReadLine);
        controller.readWriteLine()
                .subscribe(this::onWriteLine);
        controller.readShutdown()
                .subscribe(this::onShutdown);
        controller.readControllerStatus()
                .map(ControllerStatusMapper::map)
                .doOnNext(s ->
                        logger.atDebug().log("Status {}", s))
                .distinctUntilChanged()
                .doOnNext(s ->
                        logger.atDebug().log("Distinct {}", s))
                .subscribe(this::onControlStatus);
        controller.setOnLatch(this::onLatch);
        controller.setOnInference(this::onInference);
        Observable.mergeArray(
                        SwingObservable.window(commandFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(sensorFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(comFrame, SwingObservable.WINDOW_ACTIVE))
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .subscribe(this::onClose);
        Stream.of(comFrame, sensorFrame, commandFrame).forEach(f -> f.setVisible(true));
    }

    private void halt() {
        currentState = null;
        controller.execute(RobotCommands.idle());
    }

    /**
     * Initialize the check
     *
     * @param args the command line arguments
     * @throws ArgumentParserException in case of error
     */
    private CameraCalibration init(String[] args) throws ArgumentParserException {
        ArgumentParser parser = createParser();
        try {
            parseArgs = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            throw e;
        }
        return this;
    }

    /**
     * Initializes the listeners
     */
    private void initListeners() {
        reconnectButton.addActionListener(this::onReconnectButton);
    }

    private Consumer<RobotStatus> moveSensor(int direction) {
        logger.atInfo().log("move {}", direction);
        this.direction = clamp(direction, -90, 90);
        this.currentState = this::positioning;
        return currentState;
    }

    private void nextDirection(RobotStatus status, int step) {
        if (direction >= 90) {
            halt();
            processSamples();
        } else {
            moveSensor(direction + step).accept(status);
        }
    }

    /**
     * Handles the window close event
     *
     * @param windowEvent the event
     */
    private void onClose(WindowEvent windowEvent) {
        controller.shutdown();
    }

    /**
     * Handles the control status event
     *
     * @param status the control status event
     */
    private void onControlStatus(String status) {
        comMonitor.onControllerStatus(status);
        sensorMonitor.onControllerStatus(status);
    }

    /**
     * Handles the inference event
     *
     * @param status the robot status
     */
    private void onInference(RobotStatus status) {
        logger.atDebug().log("onInference");
        if (currentState != null) {
            currentState.accept(status);
        }
    }

    private void onLatch(RobotStatus robotStatus) {
        logger.atDebug().log("onLatch");
    }

    /**
     * Handles the read line
     *
     * @param line the read line
     */
    private void onReadLine(String line) {
        comMonitor.onReadLine(line);
        logger.atDebug().setMessage("--> {}").addArgument(line).log();
    }

    /**
     * Handles the reconnect button action
     *
     * @param actionEvent the event
     */
    private void onReconnectButton(ActionEvent actionEvent) {
        controller.reconnect();
    }

    /**
     * Handles the shutdown event
     */
    private void onShutdown() {
        commandFrame.dispose();
        sensorFrame.dispose();
        comFrame.dispose();
    }

    /**
     * Handle the state event
     *
     * @param status the status event
     */
    private void onStatus(RobotStatus status) {
        sensorMonitor.onStatus(status);
    }

    /**
     * Handles the written line
     *
     * @param line the written line
     */
    private void onWriteLine(String line) {
        logger.atDebug().setMessage("<-- {}").addArgument(line).log();
        comMonitor.onWriteLine(line);
    }

    private void positioning(RobotStatus status) {
        WheellyProxyMessage proxy = status.proxyMessage();
        if (proxy.sensorDirectionDeg() != direction) {
            controller.execute(RobotCommands.scan(Complex.fromDeg(direction)));
        } else {
            sample().accept(status);
        }
    }

    private void processSamples() {
        logger.atInfo().log("Process samples");
        Sample minLeft = samples.stream()
                .filter(s -> s.xLabel() <= 0)
                .min(Comparator.comparingDouble(Sample::xLabel))
                .orElse(null);
        Sample maxLeft = samples.stream()
                .filter(s -> s.xLabel() <= 0)
                .max(Comparator.comparingDouble(Sample::xLabel))
                .orElse(null);
        Sample minRight = samples.stream()
                .filter(s -> s.xLabel() >= 0)
                .min(Comparator.comparingDouble(Sample::xLabel))
                .orElse(null);
        Sample maxRight = samples.stream()
                .filter(s -> s.xLabel() >= 0)
                .max(Comparator.comparingDouble(Sample::xLabel))
                .orElse(null);
        if (maxRight == null || maxLeft == null || minRight == null || minLeft == null) {
            logger.atError().log("Inconsistent samples");
            samples.clear();
            moveSensor(-90);
            return;
        }
        double x0 = maxLeft.xLabel;
        double x1 = minRight.xLabel;
        double dir0 = maxLeft.sensorDir.toRad();
        double dir1 = minRight.sensorDir.toRad();
        // Interpolates
        double alpha0Rad = dir0 - x0 * (dir1 - dir0) / (x1 - x0);
        Complex alpha0 = Complex.fromRad(alpha0Rad);
        double alphawLeftRad = 2 * atan2(minLeft.width * tan(minLeft.sensorDir.sub(alpha0).tan()), 2 * minLeft.xLabel);
        double alphawRightRad = 2 * atan2(maxRight.width * tan(maxRight.sensorDir.sub(alpha0).tan()), 2 * maxRight.xLabel);
        Complex alphaw = Complex.fromRad((alphawRightRad + alphawLeftRad) / 2);
        cameraViewAngle.setValue(alphaw.toIntDeg());
        logger.atInfo().log("Results alpha0={} alphaw={}", alpha0, alphaw);
    }

    /**
     * Runs the application
     */
    private void run() throws IOException {
        logger.info("Robot check started.");
        createContext();
        createConnections();
        createFlows();
        comFrame.setState(JFrame.ICONIFIED);
        moveSensor(-90);
        controller.start();
    }

    private Consumer<RobotStatus> sample() {
        logger.atDebug().log("Sampling");
        currentState = this::sampling;
        samplingCounter = 0;
        samplingStart = 0;
        return currentState;
    }

    private void sampling(RobotStatus status) {
        CameraEvent cameraEvent = status.cameraEvent();
        WheellyProxyMessage proxy = status.proxyMessage();
        long t0 = cameraEvent.simulationTime();
        if (samplingStart == 0) {
            samplingStart = t0;
        }
        samplingCounter++;
        if (!CameraEvent.UNKNOWN_QR_CODE.equals(cameraEvent.qrCode())
                && t0 > samplingStart) {
            // Valid sample
            Complex sensorDir = proxy.sensorDirection();
            Point2D[] points = cameraEvent.points();
            double xCamera = 0;
            for (Point2D point : points) {
                xCamera += point.getX();
            }
            int width = cameraEvent.width();
            Sample sample = new Sample(xCamera / 4 - (double) width / 2, width, sensorDir);
            samples.add(sample);
            nextDirection(status, SLOW_SENSOR_DIRECTION_STEP);
        } else if (samplingCounter >= MAX_SAMPLES_PER_SAMPLING) {
            // No samples
            nextDirection(status, FAST_SENSOR_DIRECTION_STEP);
        }
    }

    record Sample(double xLabel, int width, Complex sensorDir) {
    }
}
