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
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.swing.SwingUtils.createButton;
import static org.mmarini.swing.SwingUtils.createToolBarToggleButton;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;


/**
 * Provides a monitoring panel visualization for handling manual command inputs,
 * managing telemetry parameters, and observing spatial grid details from the matrix.
 */
public class MatrixMonitor {
    public static final String MONITOR_SCHEMA_YML = "https://mmarini.org/wheelly/monitor-schema-1.0";
    public static final int MAX_DISTANCE = 200;
    public static final double CM = 0.01;
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
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new MatrixMonitor(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }

    /**
     * The root graphical panel container organizing execution control blocks.
     */
    private final Container commandPanel;
    /**
     * The slider component utilised to adjust target chassis angular rotations.
     */
    private final JSlider rotationSlider;
    /**
     * The slider component utilised to define target movement distance offsets.
     */
    private final JSlider motionDistanceSlider;
    /**
     * The slider component utilised to adjust sensor head alignment bearings.
     */
    private final JSlider headRotationSlider;
    /**
     * The slider component utilised to define relative operational tracking ranges.
     */
    private final JSlider trackDistanceSlider;
    /**
     * The text field rendering formatted angular rotation parameter options.
     */
    private final JFormattedTextField rotationField;
    /**
     * The text field rendering formatted chassis translation limits.
     */
    private final JFormattedTextField motionDistanceField;
    /**
     * The text field rendering formatted sensor head angular bearings.
     */
    private final JFormattedTextField headRotationField;
    /**
     * The text field rendering formatted relative tracking limits.
     */
    private final JFormattedTextField trackDistanceField;
    /**
     * The user interface field rendering active movement status information text.
     */
    private final JTextField motionStatusField;
    /**
     * The user interface field rendering active sensor head status information text.
     */
    private final JTextField headStatusField;
    /**
     * The command trigger button assigned to invoke reconnection sequences.
     */
    private final JButton reconnectButton;
    /**
     * The control interface switch assigned to immediately park or halt the chassis.
     */
    private final JToggleButton haltButton;
    /**
     * The control interface switch assigned to request linear forward movement profiles.
     */
    private final JToggleButton forwardButton;
    /**
     * The control interface switch assigned to request linear backward movement profiles.
     */
    private final JToggleButton backwardButton;
    /**
     * The control interface switch assigned to request chassis orientation adjustment sweeps.
     */
    private final JToggleButton rotateButton;
    /**
     * The control interface switch assigned to activate front-facing sensor tracking behaviors.
     */
    private final JToggleButton frontTrackButton;
    /**
     * The control interface switch assigned to activate rear-facing sensor tracking behaviors.
     */
    private final JToggleButton rearTrackButton;
    /**
     * The control interface switch assigned to trigger dynamic field scanning routines.
     */
    private final JToggleButton scanButton;
    /**
     * The real-time logging monitor visualising physical telemetry responses.
     */
    private final SensorMonitor sensorMonitor;
    /**
     * The low-level interface display detailing diagnostic communication metrics.
     */
    private final ComMonitor comMonitor;
    /**
     * The current application parameter arguments namespace wrapper.
     */
    private final Namespace args;
    /**
     * The active orientation state tracking indicator bound to the robot chassis.
     */
    private MotionStatus motionStatus;
    /**
     * The frame container rendering communication metrics views.
     */
    private JFrame comFrame;
    /**
     * The main frame container rendering manual operation components.
     */
    private JFrame commandFrame;
    /**
     * The frame container rendering live sensor stream views.
     */
    private JFrame sensorFrame;
    /**
     * The engine interface managing target processing command pipelines.
     */
    private RobotControllerApi controller;
    /**
     * The interface connection channel assigned to talk to the robot unit.
     */
    private RobotApi robot;
    /**
     * The active tracking status profile bound to the sensor head component.
     */
    private HeadStatus headStatus;
    /**
     * The telemetry state structure mapping responses from the robot unit.
     */
    private RobotStatus robotStatus;

    /**
     * Creates the matrix monitor application
     *
     * @param args the parsed command line configuration parameters namespace
     */
    public MatrixMonitor(Namespace args) {
        this.args = requireNonNull(args);
        this.comMonitor = new ComMonitor();
        this.sensorMonitor = new SensorMonitor();
        this.headRotationSlider = new JSlider();
        this.rotationSlider = new JSlider();
        this.motionDistanceSlider = new JSlider(JSlider.VERTICAL);
        this.trackDistanceSlider = new JSlider(JSlider.VERTICAL);
        DecimalFormat degFormat = new DecimalFormat("##0");
        DecimalFormat intFormat = new DecimalFormat("#0");
        this.headRotationField = new JFormattedTextField(degFormat);
        this.rotationField = new JFormattedTextField(degFormat);
        this.motionDistanceField = new JFormattedTextField(intFormat);
        this.trackDistanceField = new JFormattedTextField(intFormat);
        this.motionStatusField = new JTextField();
        this.headStatusField = new JTextField();
        this.haltButton = createToolBarToggleButton("MatrixMonitor.haltButton");
        this.forwardButton = createToolBarToggleButton("MatrixMonitor.forwardButton");
        this.rotateButton = createToolBarToggleButton("MatrixMonitor.rotateButton");
        this.backwardButton = createToolBarToggleButton("MatrixMonitor.backwardButton");
        this.reconnectButton = createButton("MatrixMonitor.reconnectButton");
        this.scanButton = createToolBarToggleButton("MatrixMonitor.scanButton");
        this.frontTrackButton = createToolBarToggleButton("MatrixMonitor.frontTrackButton");
        this.rearTrackButton = createToolBarToggleButton("MatrixMonitor.rearTrackButton");
        this.commandPanel = createCommandPanel();
        this.headStatus = HeadStatus.lookStraight();
        this.motionStatus = MotionStatus.halt();

        createListeners();
    }

    /**
     * Returns the command panel
     */
    private Container createCommandPanel() {
        return new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2 noweight fill").add(createHeadPanel())
                .modify("at,1,0").add(createMotionPanel())
                .modify("at,0,1 span,2,1 insets,10 noweight nofill").add(reconnectButton)
                .getContainer();
    }

    /**
     * Creates the application context by loading the configurations.
     */
    private void createConnections() {
        controller.connectRobot(robot);
    }

    /**
     * Creates the application context
     *
     * @throws IOException in case of error
     */
    private void createContext() throws Throwable {
        JsonNode config = org.mmarini.yaml.Utils.fromFile(args.getString("config"));
        WheellyJsonSchemas.instance().validateOrThrow(config, MONITOR_SCHEMA_YML);
        this.robot = AppYaml.robotFromJson(config);
        this.controller = AppYaml.controllerFromJson(config);

        // Creates the frames
        this.commandFrame = createFrame(Messages.getString("MatrixMonitor.title"), commandPanel);
        this.sensorFrame = sensorMonitor.createFrame();
        this.comFrame = comMonitor.createFrame();
        if (controller instanceof RobotController ctrl) {
            ctrl.onHeadStatus(sensorMonitor::onHeadStatusCommand);
            ctrl.onMotionStatus(sensorMonitor::onMotionStatus);
        }

        layHorizontally(commandFrame, sensorFrame, comFrame);
    }

    /**
     * Initialises the reactive data streams and event flows for the controller,
     * registering telemetry updates, error logging subscriptions, shutdown hooks,
     * UI frame monitoring events, and visual visibility settings.
     */
    private void createFlows() {
        controller.addOnRobotStatus(this::onRobotStatus);
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
     * Helper method to stub out the layout block for the head operations component dashboard.
     *
     * @return the initialised graphical UI component container for head controls
     */
    private Container createHeadPanel() {
        headRotationField.setColumns(5);
        headRotationField.setEditable(false);
        headRotationField.setHorizontalAlignment(SwingConstants.RIGHT);
        headRotationField.setValue(0);

        headRotationSlider.setMinimum(-90);
        headRotationSlider.setMaximum(90);
        headRotationSlider.setValue(0);
        headRotationSlider.setMinorTickSpacing(5);
        headRotationSlider.setMajorTickSpacing(15);
        headRotationSlider.setPaintLabels(true);
        headRotationSlider.setPaintTicks(true);
        headRotationSlider.setSnapToTicks(true);

        trackDistanceField.setColumns(5);
        trackDistanceField.setEditable(false);
        trackDistanceField.setHorizontalAlignment(SwingConstants.RIGHT);
        trackDistanceField.setValue(0);

        trackDistanceSlider.setMinimum(0);
        trackDistanceSlider.setMaximum(MAX_DISTANCE);
        trackDistanceSlider.setValue(0);
        trackDistanceSlider.setMinorTickSpacing(10);
        trackDistanceSlider.setMajorTickSpacing(20);
        trackDistanceSlider.setPaintLabels(true);
        trackDistanceSlider.setPaintTicks(true);
        trackDistanceSlider.setSnapToTicks(true);

        headStatusField.setColumns(13);
        headStatusField.setEditable(false);

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(frontTrackButton);
        buttonGroup.add(scanButton);
        buttonGroup.add(rearTrackButton);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 2));
        buttons.add(frontTrackButton);
        buttons.add(scanButton);
        buttons.add(rearTrackButton);

        /*
         * 0 - | rotation slider                 | distance slider |
         * 1 - | rotation label | rotation field |                 |
         * 2 - |                                 | distance label  |
         * 3 - | target label   | target field   | distance field  |
         * 4 - |                     buttons                       |
         */
        JPanel panel = GridLayoutHelper.create()
                .modify("insets,2")
                .modify("at,0,0 span,2,1 hfill").add(headRotationSlider)
                .modify("at,2,0 span,1,2 vfill").add(trackDistanceSlider)

                .modify("at,0,1 nw nofill nospan").add("MatrixMonitor.headDir.label")
                .modify("at,1,1").add(headRotationField)

                .modify("at,2,2 center").add("MatrixMonitor.trackDistance.label")

                .modify("at,0,3 w").add("MatrixMonitor.headStatus.label")
                .modify("at,1,3").add(headStatusField)
                .modify("at,2,3 center").add(trackDistanceField)

                .modify("at,0,4 nofill center span,3,1").add(buttons)
                .getContainer();
        panel.setBorder(BorderFactory.createTitledBorder("Sensor direction (DEG)"));
        return panel;
    }

    /**
     * Registers individual layout update routines, state bindings, and change listeners across all interactive components.
     */
    private void createListeners() {
        haltButton.addActionListener(this::onHaltButton);
        reconnectButton.addActionListener(this::onReconnectButton);
        forwardButton.addActionListener(this::onForwardButton);
        rotateButton.addActionListener(this::onRotateButton);
        backwardButton.addActionListener(this::onBackwardButton);
        frontTrackButton.addActionListener(this::onFrontTrackButton);
        scanButton.addActionListener(this::onScanButton);
        rearTrackButton.addActionListener(this::onRearTrackButton);

        headRotationSlider.addChangeListener(this::onHeadRotationSlider);
        trackDistanceSlider.addChangeListener(this::onTrackDistanceSlider);
        rotationSlider.addChangeListener(this::onRotationSlider);
        motionDistanceSlider.addChangeListener(this::onMotionDistanceSlider);
    }

    /**
     * Helper method to stub out the layout block for the movement operations component dashboard.
     *
     * @return the initialised graphical UI component container for motion controls
     */
    private Container createMotionPanel() {
        rotationField.setColumns(5);
        rotationField.setEditable(false);
        rotationField.setHorizontalAlignment(SwingConstants.RIGHT);
        rotationField.setValue(0);

        rotationSlider.setMinimum(-180);
        rotationSlider.setMaximum(180);
        rotationSlider.setValue(0);
        rotationSlider.setMinorTickSpacing(5);
        rotationSlider.setMajorTickSpacing(45);
        rotationSlider.setPaintLabels(true);
        rotationSlider.setPaintTicks(true);
        rotationSlider.setSnapToTicks(true);

        motionDistanceField.setColumns(5);
        motionDistanceField.setEditable(false);
        motionDistanceField.setHorizontalAlignment(SwingConstants.RIGHT);
        motionDistanceField.setValue(0);

        motionDistanceSlider.setMinimum(0);
        motionDistanceSlider.setMaximum(MAX_DISTANCE);
        motionDistanceSlider.setValue(0);
        motionDistanceSlider.setMinorTickSpacing(10);
        motionDistanceSlider.setMajorTickSpacing(20);
        motionDistanceSlider.setPaintLabels(true);
        motionDistanceSlider.setPaintTicks(true);
        motionDistanceSlider.setSnapToTicks(true);

        motionStatusField.setColumns(13);
        motionStatusField.setEditable(false);

        haltButton.setBackground(Color.RED);
        haltButton.setForeground(Color.WHITE);

        ButtonGroup buttonGroup = new ButtonGroup();
        for (JToggleButton bt : new JToggleButton[]{haltButton, forwardButton, rotateButton, backwardButton}) {
            buttonGroup.add(bt);
        }

        JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 2));
        buttons.add(haltButton);
        buttons.add(forwardButton);
        buttons.add(rotateButton);
        buttons.add(backwardButton);

        /*
         * 0 - |          dir slider            | distance slider |
         * 1 - | dir label      |      dir text |                 |
         * 2 - |                                | distance label  |
         * 3 - | target label   | target text   | distance field  !
         * 4 - |                     buttons                      !
         */
        JPanel panel = GridLayoutHelper.create()
                .modify("insets,2")
                .modify("at,0,0 hfill span,2,1").add(rotationSlider)
                .modify("at,2,0 vfill span,1,2").add(motionDistanceSlider)

                .modify("at,0,1 nw nofill nospan").add("MatrixMonitor.robotDir.label")
                .modify("at,1,1 ne").add(rotationField)

                .modify("at,2,2 w").add("MatrixMonitor.distance.label")

                .modify("at,0,3 w").add("MatrixMonitor.motionStatus.label")
                .modify("at,1,3 e").add(motionStatusField)
                .modify("at,2,3 center").add(motionDistanceField)

                .modify("at,0,4 center span,3,1").add(buttons)

                .getContainer();
        panel.setBorder(BorderFactory.createTitledBorder("Robot command"));
        return panel;
    }

    /**
     * Executes the specified modification routine across all interactive toggle button
     * components registered within the user interface toolbar.
     *
     * @param modifer the consumer functional routine to apply to each {@link JToggleButton}
     */
    private void forAllButtons(Consumer<JToggleButton> modifer) {
        Stream.of(haltButton, forwardButton, rotateButton, backwardButton, frontTrackButton, scanButton, rearTrackButton)
                .forEach(modifer);
    }

    /**
     * Computes the absolute 2D target point coordinates for front sensor tracking,
     * translating the relative values of the head rotation and distance sliders
     * based on the current telemetry position and bearing of the robot.
     *
     * @param robotStatus the current low-level telemetry status of the robot
     * @return the calculated {@link Point2D} representing the absolute target position in space
     */
    private Point2D frontTrackTarget(RobotStatus robotStatus) {
        Complex robotDir = robotStatus.direction();
        Point2D location = robotStatus.location();
        Point2D headLocation = robotStatus.robotSpec().headLocation(location, robotDir);
        return robotDir.add(Complex.fromDeg(headRotationSlider.getValue()))
                .at(headLocation, trackDistanceSlider.getValue() * CM);
    }

    /**
     * Computes the absolute 2D target point coordinates for chassis movement,
     * translating the relative layout values of the rotation and distance sliders
     * based on the current location and alignment bearing of the robot.
     *
     * @param robotStatus the current low-level telemetry status of the robot
     * @return the calculated {@link Point2D} representing the absolute target position in space
     */
    private Point2D motionTarget(RobotStatus robotStatus) {
        Complex robotDir = robotStatus.direction();
        Point2D location = robotStatus.location();
        return robotDir.add(Complex.fromDeg(headRotationSlider.getValue()))
                .at(location, trackDistanceSlider.getValue() * CM);
    }

    /**
     * Handles the backward button selection event by computing the new target position,
     * updating the local motion state to backward, and dispatching it to the controller.
     *
     * @param actionEvent the action event triggered by the backward button component
     */
    private void onBackwardButton(ActionEvent actionEvent) {
        Point2D target = motionTarget(robotStatus);
        motionStatus = MotionStatus.backward(target);
        controller.motionStatus(motionStatus);
        motionStatusField.setText(motionStatus.toString());
    }


    /**
     * Handles the window closing events by safely triggering a complete system
     * shutdown sequence on the active interface controller.
     *
     * @param windowEvent the window event triggered by closing any of the UI frame wrappers
     */
    private void onCloseWindow(WindowEvent windowEvent) {
        controller.shutdown();
    }

    /**
     * Handles updates to the controller status by forwarding the raw status information string
     * to both the communication interface monitor and the sensor data logging monitor.
     *
     * @param status the raw string status information details emitted by the controller
     */
    private void onControlStatus(String status) {
        comMonitor.onControllerStatus(status);
        sensorMonitor.onControllerStatus(status);
    }

    /**
     * Handles the forward button selection event by computing the new spatial target position coordinates,
     * updating the local motion profile state to forward, and dispatching it directly to the controller.
     *
     * @param actionEvent the action event triggered by the forward toolbar button component
     */
    private void onForwardButton(ActionEvent actionEvent) {
        Point2D target = motionTarget(robotStatus);
        motionStatus = MotionStatus.forward(target);
        controller.motionStatus(motionStatus);
        motionStatusField.setText(motionStatus.toString());
    }

    /**
     * Handles the front-facing tracking button selection event by computing the target position,
     * updating the local sensor head operational status to front track, and dispatching it to the controller.
     *
     * @param ev the action event triggered by the front track toolbar button component
     */
    private void onFrontTrackButton(ActionEvent ev) {
        Point2D target = frontTrackTarget(robotStatus);
        headStatus = HeadStatus.trackFrontFace(target);
        controller.headStatus(headStatus);
        headStatusField.setText(headStatus.toString());
    }

    /**
     * Handles the halt button selection event by requesting an immediate stop profile,
     * updating the local motion state indicator, and dispatching the command sequence to the controller.
     *
     * @param actionEvent the action event triggered by the halt toolbar button component
     */
    private void onHaltButton(ActionEvent actionEvent) {
        logger.atDebug().log("Halting robot");
        motionStatus = MotionStatus.halt();
        controller.motionStatus(motionStatus);
        motionStatusField.setText(motionStatus.toString());
    }

    /**
     * Handles the sensor direction slider value adjustment events by extracting the current value
     * and synchronising the corresponding formatted numeric input field display.
     *
     * @param changeEvent the adjustment change event triggered by the head rotation slider component
     */
    private void onHeadRotationSlider(ChangeEvent changeEvent) {
        int sensorDeg = headRotationSlider.getValue();
        headRotationField.setValue(sensorDeg);
    }

    /**
     * Handles the movement distance slider value adjustment events by extracting the current value
     * and synchronising the corresponding formatted numeric input field display.
     *
     * @param changeEvent the adjustment change event triggered by the distance slider component
     */
    private void onMotionDistanceSlider(ChangeEvent changeEvent) {
        int distance = motionDistanceSlider.getValue();
        motionDistanceField.setValue(distance);
    }

    /**
     * Handles the rear-facing tracking button selection event by computing the target position,
     * updating the local sensor head operational status to rear track, and dispatching it to the controller.
     *
     * @param actionEvent the action event triggered by the rear track toolbar button component
     */
    private void onRearTrackButton(ActionEvent actionEvent) {
        Point2D target = rearTrackTarget(robotStatus);
        headStatus = HeadStatus.trackRearFace(target);
        controller.headStatus(headStatus);
        headStatusField.setText(headStatus.toString());
    }

    /**
     * Handles the reconnect button action by instructing the active interface
     * controller to reset and re-establish the hardware link connection state.
     *
     * @param actionEvent the action event triggered by the reconnect button component
     */
    private void onReconnectButton(ActionEvent actionEvent) {
        controller.reconnect();
    }

    /**
     * Handles incoming real-time telemetry updates from the robot by forwarding the data packet to the
     * sensor logging monitor, unblocking control toolbar components upon the initial successful message latch,
     * and updating the local status record.
     *
     * @param status the newly received {@link RobotStatus} telemetry data structure
     */
    private void onRobotStatus(RobotStatus status) {
        sensorMonitor.onStatus(status);
        if (robotStatus == null) {
            forAllButtons(btn -> btn.setEnabled(true));
        }
        robotStatus = status;
    }

    /**
     * Handles the rotation selection button events by computing the absolute target bearing from the relative
     * slider adjustment, initialising a rotation profile command, and dispatching it to the controller.
     *
     * @param actionEvent the action event triggered by the rotate toolbar button component
     */
    private void onRotateButton(ActionEvent actionEvent) {
        int targetDir = robotStatus.direction().add(Complex.fromDeg(rotationSlider.getValue())).toIntDeg();
        motionStatus = MotionStatus.rotate(targetDir);
        controller.motionStatus(motionStatus);
    }

    /**
     * Handles the robot direction orientation slider value adjustment events by extracting the current value
     * and synchronising the corresponding formatted numeric input field display.
     *
     * @param changeEvent the adjustment change event triggered by the rotation slider component
     */
    private void onRotationSlider(ChangeEvent changeEvent) {
        int robotDir = rotationSlider.getValue();
        rotationField.setValue(robotDir);
    }

    /**
     * Handles the sensor head scan button event by initiating an environmental
     * sweep sequence at the specified angular offset, dispatching the profile state
     * change to the controller, and synchronising the text display indicator.
     *
     * @param actionEvent the action event triggered by the scan toolbar button component
     */
    private void onScanButton(ActionEvent actionEvent) {
        headStatus = HeadStatus.scan(headRotationSlider.getValue());
        controller.headStatus(headStatus);
        headStatusField.setText(headStatus.toString());
    }

    /**
     * Handles the shutdown event notification by releasing resources and disposing of all active
     * graphical interface window frame containers.
     */
    private void onShutdown() {
        commandFrame.dispose();
        sensorFrame.dispose();
        comFrame.dispose();
    }

    /**
     * Handles the tracking distance slider value adjustment events by extracting the current value
     * and synchronising the corresponding formatted numeric input field display.
     *
     * @param changeEvent the adjustment change event triggered by the track distance slider component
     */
    private void onTrackDistanceSlider(ChangeEvent changeEvent) {
        int distance = trackDistanceSlider.getValue();
        trackDistanceField.setValue(distance);
    }

    /**
     * Computes the absolute 2D target point coordinates for rear sensor tracking,
     * translating the relative values of the head rotation and distance sliders
     * based on the inverted backend location orientation matrix profile of the robot.
     *
     * @param robotStatus the current low-level telemetry status of the robot
     * @return the calculated {@link Point2D} representing the absolute target position in space
     */
    private Point2D rearTrackTarget(RobotStatus robotStatus) {
        Complex robotDir = robotStatus.direction();
        Point2D location = robotStatus.location();
        Point2D headLocation = robotStatus.robotSpec().headLocation(location, robotDir);
        return robotDir.opposite()
                .add(Complex.fromDeg(headRotationSlider.getValue()))
                .at(headLocation, trackDistanceSlider.getValue() * CM);
    }

    /**
     * Triggers active processing loops and loads up all framework graphical user interface windows.
     */
    private void run() throws Throwable {
        logger.info("Robot check started.");
        createContext();
        createConnections();
        createFlows();
        comFrame.setState(JFrame.ICONIFIED);
        haltButton.setSelected(true);
        scanButton.setSelected(true);
        forAllButtons(btn -> btn.setEnabled(false));
        controller.start();
        controller.headStatus(headStatus);
        controller.motionStatus(motionStatus);
    }
}
