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

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.swing.MatrixPanel;
import org.mmarini.wheelly.swing.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;

import static java.lang.Math.max;
import static java.lang.String.format;
import static org.mmarini.wheelly.apps.Wheelly.fromConfig;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontaly;


public class MatrixMonitor {
    private static final int SENSOR_COLUMNS = 13 + 70 + 1;
    private static final int SCAN_COLUMNS = 13 + 6 + 1;
    private static final int MOVE_COLUMNS = 13 + 11 + 1;
    private static final Dimension COMMAND_FRAME_SIZE = new Dimension(400, 800);
    private static final Dimension MATRIX_FRAME_SIZE = new Dimension(1200, 800);
    private static final Logger logger = LoggerFactory.getLogger(MatrixMonitor.class);
    private static final int MAX_SPEED = 20;
    private static final int MAX_TIME = 10000;
    private static final int MIN_TIME = 500;

    /**
     * Returns the command line arguments parser
     */
    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(MatrixMonitor.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run manual control robot.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-r", "--robot")
                .setDefault("robot.yml")
                .help("specify robot yaml configuration file");
        parser.addArgument("-c", "--controller")
                .setDefault("controller.yml")
                .help("specify controller yaml configuration file");
        return parser;
    }

    /**
     * Runs the checks
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            new MatrixMonitor().init(args).run();
        } catch (Throwable e) {
            logger.atError().setCause(e).log();
        }
    }

    private static String toMonitorString(RobotStatus status) {
        return format("%6.2f %6.2f %4d %3d %4.2f %4.0f %4.0f %4d %4d %1X %4d %4.1f %1d %1d 0x%02d %1d",
                status.getLocation().getX(),
                status.getLocation().getY(),
                status.getDirection(),
                status.getSensorDirection(),
                status.getEchoDistance(),
                status.getLeftPps(),
                status.getRightPps(),
                status.getWheellyStatus().getFrontSensors(),
                status.getWheellyStatus().getRearSensors(),
                status.getContacts(),
                status.getWheellyStatus().getSupplySensor(),
                status.getSupplyVoltage(),
                status.canMoveForward() ? 1 : 0,
                status.canMoveBackward() ? 1 : 0,
                status.getImuFailure(),
                status.isHalt() ? 1 : 0
        );
    }

    private final MatrixPanel sensorPanel;
    private final MatrixPanel scanPanel;
    private final MatrixPanel movePanel;
    private final Container commandPanel;
    private final JButton haltButton;
    private final JSlider sensorDirSlider;
    private final JFormattedTextField sensorDirField;
    private final JButton runButton;
    private final JSlider robotDirSlider;
    private final JSlider speedSlider;
    private final JFormattedTextField robotDirField;
    private final JFormattedTextField speedField;
    private final JSlider timeSlider;
    private final JFormattedTextField timeField;
    private Namespace parseArgs;
    private int commandDuration;
    private long runTimestamp;
    private RobotControllerApi controller;
    private boolean halt;
    private JFrame frame;
    private JFrame matrixFrame;

    /**
     * Creates the check
     */
    public MatrixMonitor() {
        this.sensorPanel = new MatrixPanel();
        this.scanPanel = new MatrixPanel();
        this.movePanel = new MatrixPanel();
        this.sensorDirSlider = new JSlider();
        this.robotDirSlider = new JSlider();
        this.speedSlider = new JSlider();
        this.timeSlider = new JSlider();
        DecimalFormat degFormat = new DecimalFormat("##0");
        DecimalFormat intFormat = new DecimalFormat("#0");
        this.sensorDirField = new JFormattedTextField(degFormat);
        this.robotDirField = new JFormattedTextField(degFormat);
        this.speedField = new JFormattedTextField(intFormat);
        this.timeField = new JFormattedTextField(intFormat);
        this.haltButton = new JButton("HALT !!!");
        this.runButton = new JButton("Run");
        this.commandDuration = 1000;
        this.commandPanel = createCommandPanel();
        this.halt = true;
        initFlow();
    }

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

        speedField.setColumns(5);
        speedField.setEditable(false);
        speedField.setHorizontalAlignment(SwingConstants.RIGHT);
        speedField.setValue(0);

        speedSlider.setOrientation(JSlider.VERTICAL);
        speedSlider.setMinimum(-MAX_SPEED);
        speedSlider.setMaximum(MAX_SPEED);
        speedSlider.setValue(0);
        speedSlider.setMinorTickSpacing(5);
        speedSlider.setMajorTickSpacing(10);
        speedSlider.setPaintLabels(true);
        speedSlider.setPaintTicks(true);
        speedSlider.setSnapToTicks(true);

        timeSlider.setOrientation(JSlider.VERTICAL);
        timeSlider.setMinimum(0);
        timeSlider.setMaximum(MAX_TIME);
        timeSlider.setValue(commandDuration);
        timeSlider.setMinorTickSpacing(MIN_TIME);
        timeSlider.setMajorTickSpacing(1000);
        timeSlider.setPaintLabels(true);
        timeSlider.setPaintTicks(true);
        timeSlider.setSnapToTicks(true);

        timeField.setColumns(6);
        timeField.setEditable(false);
        timeField.setHorizontalAlignment(SwingConstants.RIGHT);
        timeField.setValue(commandDuration);

        haltButton.setBackground(Color.RED);
        haltButton.setForeground(Color.WHITE);
        haltButton.setFont(haltButton.getFont().deriveFont(20f));

        JPanel sensorCmdPanel = new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2 weight,1,0 hfill").add(sensorDirSlider)
                .modify("at,0,1 noweight nofill").add(sensorDirField)
                .getContainer();
        sensorCmdPanel.setBorder(BorderFactory.createTitledBorder("Sensor direction (DEG)"));

        JPanel otherPanel = new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,4 span,2,1").add("Direction (DEG)")
                .modify("at,0,1 hfill").add(robotDirSlider)
                .modify("at,0,2 nofill").add(robotDirField)

                .modify("at,0,3 weight,1,0 nospan").add("Speed (pps)")
                .modify("at,0,4 weight,0,1 vfill").add(speedSlider)
                .modify("at,0,5 noweight nofill").add(speedField)

                .modify("at,1,3 weight,1,0 nospan").add("Duration (ms)")
                .modify("at,1,4 noweight vfill").add(timeSlider)
                .modify("at,1,5 nofill").add(timeField)

                .modify("at,1,6 insets,10").add(runButton)
                .getContainer();
        otherPanel.setBorder(BorderFactory.createTitledBorder("Robot command"));

        return new GridLayoutHelper<>(new JPanel())
                .modify("at,0,0 insets,2 noweight fill").add(sensorCmdPanel)
                .modify("at,0,1 weight,1,1").add(otherPanel)
                .modify("at,0,2 insets,10 noweight nofill").add(haltButton)
                .getContainer();
    }

    /**
     * Returns the robot controller
     */
    protected RobotControllerApi createController() {
        RobotApi robot = fromConfig(parseArgs.getString("robot"), new Object[0], new Class[0]);
        return fromConfig(parseArgs.getString("controller"), new Object[]{robot}, new Class[]{RobotApi.class});
    }

    private Container createMonitorPanel() {
        sensorPanel.setColumns(SENSOR_COLUMNS);
        scanPanel.setColumns(SCAN_COLUMNS);
        movePanel.setColumns(MOVE_COLUMNS);
        JPanel panel = new GridLayoutHelper<>(new JPanel())
                .modify("noinsets at,0,0 weight,0,1 nospan fill").add(sensorPanel)
                .modify("insets,0,10,0,0 at,1,0").add(scanPanel)
                .modify("weight,1,1 at,2,0").add(movePanel)
                .getContainer();
        panel.setBackground(Color.BLACK);
        return new JScrollPane(panel);
    }

    private void handleCommands(RobotStatus status) {
        long time = System.currentTimeMillis();
        if (!halt && time >= runTimestamp + timeSlider.getValue()) {
            halt = true;
            runButton.setEnabled(true);
            timeSlider.setEnabled(true);
        }
        if (halt) {
            controller.haltRobot();
        } else {
            controller.moveRobot(robotDirSlider.getValue(), speedSlider.getValue());
        }
    }

    private void handleHaltButton(ActionEvent actionEvent) {
        sensorDirSlider.setValue(0);
        speedSlider.setValue(0);
        halt = true;
        runButton.setEnabled(true);
        timeSlider.setEnabled(true);
    }

    private void handleRobotDirSlider(ChangeEvent changeEvent) {
        int robotDir = robotDirSlider.getValue();
        robotDirField.setValue(robotDir);
    }

    private void handleRunButton(ActionEvent actionEvent) {
        runButton.setEnabled(false);
        timeSlider.setEnabled(false);
        this.runTimestamp = System.currentTimeMillis();
        halt = false;
    }

    private void handleSensorDirSlider(ChangeEvent changeEvent) {
        int sensorDir = sensorDirSlider.getValue();
        sensorDirField.setValue(sensorDir);
        controller.moveSensor(sensorDir);
    }

    private void handleShutdown() {
        frame.dispose();
        matrixFrame.dispose();
    }

    private void handleSpeedSlider(ChangeEvent changeEvent) {
        int robotSpeed = speedSlider.getValue();
        speedField.setValue(robotSpeed);
    }

    private void handleStatus(RobotStatus status) {
        if (status != null) {
            sensorPanel.print(toMonitorString(status));
        }
        if (!frame.isVisible() || !matrixFrame.isVisible()) {
            controller.shutdown();
        }
    }

    private void handleTimeSlider(ChangeEvent changeEvent) {
        this.commandDuration = max(timeSlider.getValue(), MIN_TIME);
        timeSlider.setValue(commandDuration);
        timeField.setValue(commandDuration);
    }

    private void handleWriteLine(String line) {
        logger.atDebug().setMessage("<-- {}").addArgument(line).log();
        if (line.startsWith("mv ") || line.equals("ha")) {
            movePanel.print(line);
        } else if (line.startsWith("sc ")) {
            scanPanel.print(line);
        }
    }

    /**
     * Initialize the check
     *
     * @param args the command line arguments
     * @throws ArgumentParserException in case of error
     */
    private MatrixMonitor init(String[] args) throws ArgumentParserException {
        parseArgs = createParser().parseArgs(args);
        return this;
    }

    private void initFlow() {
        SwingObservable.change(sensorDirSlider)
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handleSensorDirSlider)
                .subscribe();
        SwingObservable.actions(haltButton)
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handleHaltButton)
                .subscribe();

        SwingObservable.change(robotDirSlider)
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handleRobotDirSlider)
                .subscribe();
        SwingObservable.change(speedSlider)
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handleSpeedSlider)
                .subscribe();
        SwingObservable.change(timeSlider)
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handleTimeSlider)
                .subscribe();
        SwingObservable.actions(runButton)
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handleRunButton)
                .subscribe();

    }

    /**
     * Runs the check
     */
    private void run() {
        logger.info("Robot check started.");
        controller = createController();
        controller.setOnStatusReady(this::handleStatus);
        controller.setOnInference(this::handleCommands);
        controller.setOnError(er -> logger.atError().setCause(er).log());
        if (logger.isDebugEnabled()) {
            controller.setOnReadLine(line -> logger.atDebug().setMessage("--> {}").addArgument(line).log());
        }
        controller.setOnWriteLine(this::handleWriteLine);
        controller.readShutdown()
                .doOnComplete(this::handleShutdown)
                .subscribe();

        this.frame = createFrame(Messages.getString("MatrixMonitor.title"), COMMAND_FRAME_SIZE, commandPanel);
        this.matrixFrame = createFrame(Messages.getString("MatrixMonitor.title"), MATRIX_FRAME_SIZE, createMonitorPanel());
        layHorizontaly(frame, matrixFrame);
        matrixFrame.setVisible(true);
        frame.setVisible(true);
        controller.start();
    }
}
