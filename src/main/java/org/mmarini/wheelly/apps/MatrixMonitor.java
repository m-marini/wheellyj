/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.mqtt.MqttRobot;
import org.mmarini.wheelly.swing.ComMonitor;
import org.mmarini.wheelly.swing.ControllerStatusMapper;
import org.mmarini.wheelly.swing.SensorMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.mmarini.swing.SwingUtils.createButton;
import static org.mmarini.wheelly.apis.Utils.mm2m;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;

public class MatrixMonitor {
    public static final String MONITOR_SCHEMA_YML = "https://mmarini.org/wheelly/monitor-schema-1.0";
    public static final int MAX_DISTANCE = 200;
    private static final Logger logger = LoggerFactory.getLogger(MatrixMonitor.class);

    /**
     * Returns the command line arguments parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(MatrixMonitor.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run manual control robot.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("monitor.yml")
                .help("specify yaml configuration file");
        return parser;
    }

    /**
     * Runs the checks
     *
     * @param args the command line arguments
     */
    static void main(String[] args) {
        try {
            new MatrixMonitor().init(args).run();
        } catch (ArgumentParserException ignored) {
            System.exit(4);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error running matrix monitor");
            System.exit(4);
        }
    }

    private final Container commandPanel;
    private final JSlider robotDirSlider;
    private final JSlider distanceSlider;
    private final JSlider sensorDirSlider;
    private final JButton haltButton;
    private final JButton forwardButton;
    private final JButton reconnectButton;
    private final JButton backwardButton;
    private final JButton rotateButton;
    private final JFormattedTextField robotDirField;
    private final JFormattedTextField sensorDirField;
    private final JFormattedTextField distanceField;
    private final JTextField targetField;
    private final SensorMonitor sensorMonitor;
    private final ComMonitor comMonitor;
    private JFrame comFrame;
    private JFrame commandFrame;
    private JFrame sensorFrame;
    private RobotControllerApi controller;
    private boolean halt;
    private Namespace parseArgs;
    private RobotApi robot;
    private RobotCommands command;
    private Point2D target;
    private double distanceRange;
    private int directionRange;

    /**
     * Creates the matrix monitor application
     */
    public MatrixMonitor() {
        this.comMonitor = new ComMonitor();
        this.sensorMonitor = new SensorMonitor();
        this.sensorDirSlider = new JSlider();
        this.robotDirSlider = new JSlider();
        this.distanceSlider = new JSlider();
        this.command = RobotCommands.halt();
        DecimalFormat degFormat = new DecimalFormat("##0");
        DecimalFormat intFormat = new DecimalFormat("#0");
        this.sensorDirField = new JFormattedTextField(degFormat);
        this.robotDirField = new JFormattedTextField(degFormat);
        this.distanceField = new JFormattedTextField(intFormat);
        this.targetField = new JTextField();
        this.haltButton = createButton("MatrixMonitor.haltButton");
        this.forwardButton = createButton("MatrixMonitor.forwardButton");
        this.rotateButton = createButton("MatrixMonitor.rotateButton");
        this.backwardButton = createButton("MatrixMonitor.backwardButton");
        this.reconnectButton = createButton("MatrixMonitor.reconnectButton");
        this.commandPanel = createCommandPanel();
        this.halt = true;

        initListeners();
    }

    /**
     * Returns the command panel
     */
    private Container createCommandPanel() {
        sensorDirField.setColumns(5);
        sensorDirField.setEditable(false);
        sensorDirField.setHorizontalAlignment(SwingConstants.RIGHT);
        sensorDirField.setValue(0);

        sensorDirSlider.setMinimum(-90);
        sensorDirSlider.setMaximum(90);
        sensorDirSlider.setValue(0);
        sensorDirSlider.setMinorTickSpacing(5);
        sensorDirSlider.setMajorTickSpacing(15);
        sensorDirSlider.setPaintLabels(true);
        sensorDirSlider.setPaintTicks(true);
        sensorDirSlider.setSnapToTicks(true);

        robotDirField.setColumns(5);
        robotDirField.setEditable(false);
        robotDirField.setHorizontalAlignment(SwingConstants.RIGHT);
        robotDirField.setValue(0);

        robotDirSlider.setMinimum(-180);
        robotDirSlider.setMaximum(180);
        robotDirSlider.setValue(0);
        robotDirSlider.setMinorTickSpacing(5);
        robotDirSlider.setMajorTickSpacing(45);
        robotDirSlider.setPaintLabels(true);
        robotDirSlider.setPaintTicks(true);
        robotDirSlider.setSnapToTicks(true);

        distanceField.setColumns(5);
        distanceField.setEditable(false);
        distanceField.setHorizontalAlignment(SwingConstants.RIGHT);
        distanceField.setValue(0);

        distanceSlider.setMinimum(0);
        distanceSlider.setMaximum(MAX_DISTANCE);
        distanceSlider.setValue(0);
        distanceSlider.setMinorTickSpacing(10);
        distanceSlider.setMajorTickSpacing(20);
        distanceSlider.setPaintLabels(true);
        distanceSlider.setPaintTicks(true);
        distanceSlider.setSnapToTicks(true);

        targetField.setColumns(13);
        targetField.setEditable(false);
        targetField.setHorizontalAlignment(SwingConstants.CENTER);
        //updateTarget();

        haltButton.setBackground(Color.RED);
        haltButton.setForeground(Color.WHITE);
        haltButton.setFont(haltButton.getFont().deriveFont(20f));

        JPanel sensorCmdPanel = new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2 weight,1,0 hfill").add(sensorDirSlider)
                .modify("at,0,1 noweight nofill").add(sensorDirField)
                .getContainer();
        sensorCmdPanel.setBorder(BorderFactory.createTitledBorder("Sensor direction (DEG)"));
        JPanel otherPanel = new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2").add("MatrixMonitor.robotDir.label")
                .modify("at,1,0 nofill e").add(robotDirField)

                .modify("at,0,2 center").add("MatrixMonitor.distance.label")
                .modify("at,1,2 e").add(distanceField)
                .modify("at,0,4 insets,2 center").add("MatrixMonitor.target.label")
                .modify("at,1,4 noweight nofill e").add(targetField)

                .modify("at,0,1 weight,1,0 hfill span,2,1 center").add(robotDirSlider)
                .modify("at,0,3 weight,0,1 hfill span,2,1").add(distanceSlider)
                .modify("at,0,5 noweight insets,4 nofill").add(forwardButton)
                .modify("at,0,6 insets,4").add(rotateButton)
                .modify("at,0,7 insets,4").add(backwardButton)


                .getContainer();
        otherPanel.setBorder(BorderFactory.createTitledBorder("Robot command"));

        return new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2 noweight fill").add(sensorCmdPanel)
                .modify("at,0,1 weight,1,1").add(otherPanel)
                .modify("at,0,2 insets,10 noweight nofill").add(reconnectButton)
                .modify("at,0,3 insets,10 noweight nofill").add(haltButton)
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
    private void createContext() throws Throwable {
        JsonNode config = org.mmarini.yaml.Utils.fromFile(parseArgs.getString("config"));
        WheellyJsonSchemas.instance().validateOrThrow(config, MONITOR_SCHEMA_YML);
        this.robot = AppYaml.robotFromJson(config);
        this.controller = AppYaml.controllerFromJson(config);
        this.distanceRange = robot.robotSpec().targetRange() + RobotSpec.DISTANCE_PER_PULSE;
        this.directionRange = robot.robotSpec().directionRange().toIntDeg();

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
                .subscribe(this::onRobotStatus);
        controller.readErrors()
                .subscribe(er -> {
                    comMonitor.onError(er);
                    logger.atError().setCause(er).log("Error:");
                });
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
                .subscribe(this::onCloseWindow);
        Stream.of(comFrame, sensorFrame, commandFrame).forEach(f -> f.setVisible(true));
        if (robot instanceof MqttRobot mqttRobot) {
            comMonitor.addRobot(mqttRobot);
        }
    }

    /**
     * Initialize the check
     *
     * @param args the command line arguments
     * @throws ArgumentParserException in case of error
     */
    private MatrixMonitor init(String[] args) throws ArgumentParserException {
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
     * Initialises the listeners
     */
    private void initListeners() {
        sensorDirSlider.addChangeListener(this::onSensorDirSlider);
        haltButton.addActionListener(this::onHaltButton);
        reconnectButton.addActionListener(this::onReconnectButton);
        robotDirSlider.addChangeListener(this::onRobotDirSlider);
        distanceSlider.addChangeListener(this::onDistanceSlider);
        forwardButton.addActionListener(this::onForwardButton);
        rotateButton.addActionListener(this::onRotateButton);
        backwardButton.addActionListener(this::onBackwardButton);
    }

    /**
     * Handles the backward button event
     *
     * @param actionEvent the event
     */
    private void onBackwardButton(ActionEvent actionEvent) {
        forwardButton.setEnabled(false);
        rotateButton.setEnabled(false);
        backwardButton.setEnabled(false);
        robotDirSlider.setEnabled(false);
        distanceSlider.setEnabled(false);
        halt = false;
        command = RobotCommands.backward(sensorDir(), target());
        controller.execute(command);
    }

    /**
     * Handles the window close event
     *
     * @param windowEvent the event
     */
    private void onCloseWindow(WindowEvent windowEvent) {
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
     * Handles the power slider event
     *
     * @param changeEvent eht event
     */
    private void onDistanceSlider(ChangeEvent changeEvent) {
        int distance = distanceSlider.getValue();
        distanceField.setValue(distance);
    }

    /**
     * Handles the forward button event
     *
     * @param actionEvent the event
     */
    private void onForwardButton(ActionEvent actionEvent) {
        forwardButton.setEnabled(false);
        rotateButton.setEnabled(false);
        backwardButton.setEnabled(false);
        robotDirSlider.setEnabled(false);
        distanceSlider.setEnabled(false);
        halt = false;
        command = RobotCommands.forward(sensorDir(), target());
        controller.execute(command);
    }

    /**
     * Handles the halt button event
     *
     * @param actionEvent the event
     */
    private void onHaltButton(ActionEvent actionEvent) {
        //sensorDirSlider.setValue(0);
        command = RobotCommands.halt(this.sensorDirSlider.getValue());
        controller.execute(command);
        halt = true;
        forwardButton.setEnabled(true);
        rotateButton.setEnabled(true);
        backwardButton.setEnabled(true);
        robotDirSlider.setEnabled(true);
        robotDirSlider.setEnabled(true);
        distanceSlider.setEnabled(true);
    }

    /**
     * Handles the inference event
     *
     * @param status the robot status
     */
    private void onInference(RobotStatus status) {
        logger.atDebug().log("onInference");
        double distance = status.location().distance(target);
        logger.atInfo().log("distance {}", distance);
        if (!halt
                && status.halt()
                && (command.isRotate() && status.direction().isCloseTo(robotDirSlider.getValue(),
                directionRange)
                || (!command.isHalt() && !command.isRotate()
                && distance < distanceRange)
        )
        ) {
            command = RobotCommands.halt(sensorDirSlider.getValue());
            controller.execute(command);
            halt = true;
            forwardButton.setEnabled(true);
            rotateButton.setEnabled(true);
            backwardButton.setEnabled(true);
            robotDirSlider.setEnabled(true);
            robotDirSlider.setEnabled(true);
            distanceSlider.setEnabled(true);
        }
        controller.execute(command);
    }

    private void onLatch(RobotStatus robotStatus) {
        logger.atDebug().log("onLatch");
        updateTarget(robotStatus);
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
     * Handles the robot direction slider event
     *
     * @param changeEvent the event
     */
    private void onRobotDirSlider(ChangeEvent changeEvent) {
        int robotDir = robotDirSlider.getValue();
        robotDirField.setValue(robotDir);
    }

    /**
     * Handle the state event
     *
     * @param status the status event
     */
    private void onRobotStatus(RobotStatus status) {
        sensorMonitor.onStatus(status);
    }

    /**
     * Handles the run button event
     *
     * @param actionEvent the event
     */
    private void onRotateButton(ActionEvent actionEvent) {
        forwardButton.setEnabled(false);
        rotateButton.setEnabled(false);
        backwardButton.setEnabled(false);
        robotDirSlider.setEnabled(false);
        distanceSlider.setEnabled(false);
        halt = false;
        command = RobotCommands.rotate(sensorDir(), robotDir());
        controller.execute(command);
    }

    /**
     * Handles the sensor direction slider event
     *
     * @param changeEvent the event
     */
    private void onSensorDirSlider(ChangeEvent changeEvent) {
        int sensorDeg = sensorDirSlider.getValue();
        sensorDirField.setValue(sensorDeg);
        if (!sensorDirSlider.getValueIsAdjusting()) {
            command = halt
                    ? RobotCommands.halt(sensorDir())
                    : command.scanDirection(sensorDir());
            controller.execute(command);
        }
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
     * Returns the robot required direction from ui field
     */
    private Complex robotDir() {
        return Complex.fromDeg(robotDirSlider.getValue());
    }

    /**
     * Runs the application
     */
    private void run() throws Throwable {
        logger.info("Robot check started.");
        createContext();
        createConnections();
        createFlows();
        comFrame.setState(JFrame.ICONIFIED);
        controller.start();
    }

    /**
     * Returns the sensor direction from ui field
     */
    private Complex sensorDir() {
        return Complex.fromDeg(sensorDirSlider.getValue());
    }

    /**
     * Returns the target from ui fields
     */
    private Point2D target() {
        return target;
    }

    private void updateTarget(RobotStatus status) {
        if (halt) {
            target = robotDir().at(status.location(), mm2m(distanceSlider.getValue() * 10));
            targetField.setText(format("%.3f %.3f", target.getX(), target.getY()));
        }
    }
}
