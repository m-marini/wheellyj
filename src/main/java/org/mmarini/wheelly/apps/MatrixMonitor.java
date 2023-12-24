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
import io.reactivex.rxjava3.core.Observable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.swing.ComMonitor;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.SensorMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Optional;

import static java.lang.Math.max;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;


public class MatrixMonitor {
    public static final int TIME_MAJOR_TICK_SPACING = 10;
    public static final String MONITOR_SCHEMA_YML = "https://mmarini.org/wheelly/monitor-schema-0.1";
    private static final Dimension COMMAND_FRAME_SIZE = new Dimension(400, 800);
    private static final Logger logger = LoggerFactory.getLogger(MatrixMonitor.class);
    private static final int MAX_SPEED = 40;
    private static final int MAX_TIME = 60;
    private static final int MIN_TIME = 1;

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
        parser.addArgument("-d", "--dump")
                .help("specify dump signal file");
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
        } catch (ArgumentParserException ignored) {
            System.exit(4);
        } catch (Throwable e) {
            logger.atError().setCause(e).log();
            System.exit(4);
        }
    }

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
    private final ComMonitor comMonitor;
    private final SensorMonitor sensorMonitor;
    private ComDumper dumper;
    private Namespace parseArgs;
    //    private int commandDuration;
    private long runTimestamp;
    private RobotControllerApi controller;
    private boolean halt;
    private JFrame commandFrame;
    private JFrame sensorFrame;
    private JFrame comFrame;

    /**
     * Creates the check
     */
    public MatrixMonitor() {
        this.comMonitor = new ComMonitor();
        this.sensorMonitor = new SensorMonitor();
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
        this.commandPanel = createCommandPanel();
        this.halt = true;
        comMonitor.setPrintTimestamp(true);
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
        timeSlider.setValue(MAX_TIME);
        timeSlider.setMinorTickSpacing(MIN_TIME);
        timeSlider.setMajorTickSpacing(TIME_MAJOR_TICK_SPACING);
        timeSlider.setPaintLabels(true);
        timeSlider.setPaintTicks(true);
        timeSlider.setSnapToTicks(true);

        timeField.setColumns(6);
        timeField.setEditable(false);
        timeField.setHorizontalAlignment(SwingConstants.RIGHT);
        timeField.setValue(MAX_TIME);

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

    private void handleClose(WindowEvent windowEvent) {
        controller.shutdown();
    }

    private void handleCommands(RobotStatus status) {
        long time = System.currentTimeMillis();
        if (!halt && time >= runTimestamp + timeSlider.getValue() * 1000L) {
            halt = true;
            runButton.setEnabled(true);
            timeSlider.setEnabled(true);
        }
        if (halt) {
            controller.execute(RobotCommands.halt());
        } else {
            controller.execute(RobotCommands.move(
                    normalizeDegAngle(robotDirSlider.getValue()),
                    speedSlider.getValue()));
        }
    }

    private void handleControlStatus(String s) {
        comMonitor.onControllerStatus(s);
        sensorMonitor.onControllerStatus(s);
    }

    private void handleHaltButton(ActionEvent actionEvent) {
        sensorDirSlider.setValue(0);
        speedSlider.setValue(0);
        halt = true;
        runButton.setEnabled(true);
        timeSlider.setEnabled(true);
    }

    /**
     * Handles the read line
     *
     * @param line the read line
     */
    private void handleReadLine(String line) {
        comMonitor.onReadLine(line);
        if (dumper != null) {
            dumper.dumpReadLine(line);
        }
        logger.atDebug().setMessage("--> {}").addArgument(line).log();
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
        controller.execute(RobotCommands.scan(sensorDir));
    }

    private void handleShutdown() {
        commandFrame.dispose();
        sensorFrame.dispose();
        comFrame.dispose();
        if (dumper != null) {
            try {
                dumper.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleSpeedSlider(ChangeEvent changeEvent) {
        int robotSpeed = speedSlider.getValue();
        speedField.setValue(robotSpeed);
    }

    private void handleStatus(RobotStatus status) {
        sensorMonitor.onStatus(status);
    }

    private void handleTimeSlider(ChangeEvent changeEvent) {
        int commandDuration = max(timeSlider.getValue(), MIN_TIME);
        timeSlider.setValue(commandDuration);
        timeField.setValue(commandDuration);
    }

    /**
     * Handles the written line
     *
     * @param line the written line
     */
    private void handleWriteLine(String line) {
        logger.atDebug().setMessage("<-- {}").addArgument(line).log();
        if (dumper != null) {
            dumper.dumpWrittenLine(line);
        }
        comMonitor.onWriteLine(line);
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

    private void initFlow() {
        sensorDirSlider.addChangeListener(this::handleSensorDirSlider);
        haltButton.addActionListener(this::handleHaltButton);
        robotDirSlider.addChangeListener(this::handleRobotDirSlider);
        speedSlider.addChangeListener(this::handleSpeedSlider);
        timeSlider.addChangeListener(this::handleTimeSlider);
        runButton.addActionListener(this::handleRunButton);
    }

    /**
     * Runs the check
     */
    private void run() {
        logger.info("Robot check started.");
        controller = Yaml.fromFile(parseArgs.getString("config"), MONITOR_SCHEMA_YML);
        controller.readRobotStatus()
                .doOnNext(this::handleStatus).subscribe();
        controller.readErrors().doOnNext(er -> {
            comMonitor.onError(er);
            logger.atError().setCause(er).log();
        }).subscribe();
        controller.readReadLine().doOnNext(this::handleReadLine).subscribe();
        controller.readWriteLine().doOnNext(this::handleWriteLine).subscribe();
        controller.readShutdown().doOnComplete(this::handleShutdown).subscribe();
        controller.readControllerStatus().doOnNext(this::handleControlStatus).subscribe();
        controller.setOnInference(this::handleCommands);
        Optional.ofNullable(parseArgs.getString("dump"))
                .ifPresent(file -> {
                    try {
                        this.dumper = ComDumper.fromFile(file);
                    } catch (IOException e) {
                        logger.atError().setCause(e).log();
                    }
                });

        this.commandFrame = createFrame(Messages.getString("MatrixMonitor.title"), COMMAND_FRAME_SIZE, commandPanel);
        this.sensorFrame = sensorMonitor.createFrame();
        this.comFrame = comMonitor.createFrame();
        layHorizontally(commandFrame, sensorFrame, comFrame);

        Observable.mergeArray(
                        SwingObservable.window(commandFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(sensorFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(comFrame, SwingObservable.WINDOW_ACTIVE))
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleClose)
                .subscribe();

        commandFrame.setVisible(true);
        sensorFrame.setVisible(true);
        comFrame.setVisible(true);
        comFrame.setState(JFrame.ICONIFIED);
        controller.start();
    }
}
