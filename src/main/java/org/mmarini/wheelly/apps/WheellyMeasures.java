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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.jetbrains.annotations.NotNull;
import org.mmarini.MapStream;
import org.mmarini.rl.agents.CSVReader;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.swing.Messages;
import org.mmarini.swing.SwingUtils;
import org.mmarini.wheelly.mqtt.RemoteDevice;
import org.mmarini.wheelly.mqtt.RxMqttClient;
import org.mmarini.wheelly.mqtt.StringCommand;
import org.mmarini.wheelly.swing.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.Math.round;
import static org.mmarini.wheelly.apps.SpeedMeasures.*;

/**
 * Runs a session of measure tests for Wheelly physics.
 * The specific Wheelly measure sketch installation is required.
 * The mqtt interface is yet not supported.
 */

public class WheellyMeasures {
    public static final String DEFAULT_BROKER = "tcp://localhost:1883";
    public static final String DEFAULT_PASSWORD = "wheellyj";
    public static final String DEFAULT_USER = "wheellyj";
    public static final String DEFAULT_CLIENT_ID = "wheelly";
    public static final int SAFETY_TEST_DISTANCE = 300;
    public static final long COMMAND_TIMEOUT = 1000L;
    public static final String DEFAULT_DEVICE_ID = "wheelly/e05a1b66f89c/v0";
    public static final int MAX_POWER = 255;
    private static final Logger logger = LoggerFactory.getLogger(WheellyMeasures.class);
    /**
     * The key comparator by voltage
     */
    private final static Comparator<SampleKey> VOLTAGE_KEY_COMPARATOR = Comparator.comparingInt(SampleKey::voltage)
            .thenComparingInt(SampleKey::power)
            .thenComparingInt(SampleKey::supply);

    /**
     * Returns the argument parser
     */
    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(WheellyMeasures.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of measure tests.");
        parser.addArgument("-d", "--device")
                .setDefault(DEFAULT_DEVICE_ID)
                .help("specify the device id");
        parser.addArgument("-o", "--output")
                .setDefault("measures.csv")
                .help("specify output files");
        parser.addArgument("-p", "--password")
                .setDefault(DEFAULT_PASSWORD)
                .help("specify mqtt password");
        parser.addArgument("-s", "--server")
                .setDefault(DEFAULT_BROKER)
                .help("specify robot server name or ip address");
        parser.addArgument("-u", "--user")
                .setDefault(DEFAULT_USER)
                .help("specify mqtt user");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        return parser;
    }

    /**
     * Returns the sample table
     */
    private static DefaultTableModel createSampleTable() {
        DefaultTableModel result = new DefaultTableModel(0, 3) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 3
                        ? Double.class : Integer.class;
            }
        };
        result.setColumnIdentifiers(new Object[]{
                Messages.getString("WheellyMeasures.voltage"),
                Messages.getString("WheellyMeasures.power"),
                Messages.getString("WheellyMeasures.speed"),
        });
        return result;
    }

    /**
     * Export report to csv
     *
     * @param file    the csv file
     * @param reports the reports
     */
    private static void exportDataCsv(File file, Map<SampleKey, SpeedMeasure> reports) throws IOException {
        file.getAbsoluteFile().delete();
        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            naturalSortedKeys(reports).toList().forEach(key -> {
                out.print(key.type().ordinal());
                out.print(",");
                out.print(key.power());
                out.print(",");
                out.print(key.supply());
                SpeedMeasure value = reports.get(key);
                out.print(",");
                out.print(value.dt());
                out.print(",");
                out.print(value.dPulses());
                out.println();
            });
        }
    }

    /**
     * Loads report from the file
     *
     * @param file the file
     */
    private static Map<SampleKey, SpeedMeasure> loadReport(File file) {
        try (CSVReader in = new CSVReader(file)) {
            Map<SampleKey, SpeedMeasure> result = new HashMap<>();
            for (; ; ) {
                try (INDArray data = in.read(1)) {
                    if (data == null) {
                        break;
                    }
                    if (data.size(1) != 5) {
                        logger.atError().log("Required 5 columns in file {} ({})", file, data.size(1));
                        return null;
                    }
                    SampleType type = SampleType.values()[data.getInt(0, 0)];
                    int power = data.getInt(0, 1);
                    int supply = data.getInt(0, 2);
                    long dt = data.getLong(0, 3);
                    int dp = data.getInt(0, 4);
                    result.put(new SampleKey(type, power, supply), new SpeedMeasure(dt, dp));
                }
            }
            return result;
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error reading file {}", file);
            return null;
        }
    }

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new WheellyMeasures(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }

    /**
     * Returns the natural sorted key of the map
     *
     * @param results the results
     */
    private static Stream<SampleKey> naturalSortedKeys(Map<SampleKey, SpeedMeasure> results) {
        return results.keySet().stream()
                .sorted(
                        Comparator.comparingInt((SampleKey a) -> a.type().ordinal())
                                .thenComparingInt(SampleKey::voltage)
                                .thenComparingInt(SampleKey::power)
                                .thenComparingInt(SampleKey::supply));
    }

    /**
     * Returns the status by parsing the mqtt message
     *
     * @param message the message
     */
    private static SpeedMeasures.Status parseStatusMessage(MqttMessage message) {
        String text = new String(message.getPayload());
        Status status = createStatus(text);
        if (status == null) {
            logger.atError().log("Wrong message {}", text);
        }
        return status;
    }

    /**
     * Show the sample data in table
     *
     * @param table   the table
     * @param type    the type of samples
     * @param results the result
     */
    private static void showData(DefaultTableModel table, SampleType type, Map<SampleKey, SpeedMeasure> results) {
        table.setNumRows(0);
        MapStream.of(results)
                .filterKeys(t ->
                        type.equals(t.type()))
                .tuples()
                .sorted((a, b) ->
                        -VOLTAGE_KEY_COMPARATOR.compare(a.getV1(), b.getV1()))
                .forEach(t -> {
                    Object[] row = new Object[]{
                            (int) round((double) t._1.voltage() / MAX_POWER),
                            t._1.power(),
                            t._2.speed()};
                    table.addRow(row);
                });
    }

    /**
     * Show the error message
     *
     * @param err the error
     */
    static void showError(Throwable err) {
        showError(err.getMessage());
    }

    /**
     * Show the error message
     *
     * @param message the message
     */
    static void showError(String message) {
        JOptionPane.showMessageDialog(null, message,
                Messages.getString("WheellyMeasures.error.title"), JOptionPane.ERROR_MESSAGE);
    }

    private final Namespace args;
    private final RemoteDevice robotDevice;
    private final JSpinner leftMaxPower;
    private final JSpinner leftAccelerationInterval;
    private final JSpinner leftAccelerationPower;
    private final JSpinner leftDecelerationInterval;
    private final JSpinner leftDecelerationPower;
    private final JSpinner rightMaxPower;
    private final JSpinner rightAccelerationInterval;
    private final JSpinner rightAccelerationPower;
    private final JSpinner rightDecelerationInterval;
    private final JSpinner rightDecelerationPower;
    private final JButton runButton;
    private final JFrame frame;
    private final DefaultTableModel leftIncTable;
    private final DefaultTableModel leftDecTable;
    private final DefaultTableModel rightIncTable;
    private final DefaultTableModel rightDecTable;
    private final JButton clearButton;
    private Map<SpeedMeasures.SampleKey, SpeedMeasures.SpeedMeasure> results;

    /**
     * Creates the robot executor
     */
    public WheellyMeasures(Namespace args) {
        this.args = args;
        this.leftMaxPower = new JSpinner(new SpinnerNumberModel(150, -255, 255, 1));
        this.leftAccelerationInterval = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
        this.leftAccelerationPower = new JSpinner(new SpinnerNumberModel(10, 1, 255, 1));
        this.leftDecelerationInterval = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
        this.leftDecelerationPower = new JSpinner(new SpinnerNumberModel(10, 1, 255, 1));
        this.rightMaxPower = new JSpinner(new SpinnerNumberModel(-150, -255, 255, 1));
        this.rightAccelerationInterval = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
        this.rightAccelerationPower = new JSpinner(new SpinnerNumberModel(10, 1, 255, 1));
        this.rightDecelerationInterval = new JSpinner(new SpinnerNumberModel(100, 1, 10000, 1));
        this.rightDecelerationPower = new JSpinner(new SpinnerNumberModel(10, 1, 255, 1));
        this.runButton = SwingUtils.createButton("WheellyMeasures.runButton");
        this.clearButton = SwingUtils.createButton("WheellyMeasures.clearButton");
        this.leftIncTable = createSampleTable();
        this.leftDecTable = createSampleTable();
        this.rightIncTable = createSampleTable();
        this.rightDecTable = createSampleTable();
        this.frame = Utils.createFrame("WheellyMeasures.title", createContent());
        String brokerUrl = args.getString("server");
        String user = args.getString("user");
        String password = args.getString("password");
        String robotId = args.getString("device");
        try {
            RxMqttClient client = RxMqttClient.create(brokerUrl, DEFAULT_CLIENT_ID, user, password);
            robotDevice = new RemoteDevice(robotId, client);
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        runButton.addActionListener(this::onRun);
        clearButton.addActionListener(this::onClear);
        runButton.setEnabled(false);
        clearButton.setEnabled(false);
        Utils.center(frame);
    }

    /**
     * Adds the result to the current report
     *
     * @param result the result
     */
    private void addResults(Map<SampleKey, SpeedMeasure> result) {
        if (result != null) {
            if (this.results != null) {
                Map<SampleKey, SpeedMeasure> merge = SpeedMeasures.merge(this.results, result);
                setResults(merge);
            } else {
                setResults(result);
            }
        }
    }

    /**
     * Returns frame the content
     */
    private Component createContent() {
        JPanel dataPan = new GridLayoutHelper<>(new JPanel()).modify("insets,5 weight,0,0")
                .modify("at,1,0 center").add("WheellyMeasures.left")
                .modify("at,2,0 center").add("WheellyMeasures.right")
                .modify("at,3,0 center hw,1").add("WheellyMeasures.filler")
                .modify("at,0,1 e hw,0").add("WheellyMeasures.maxPower")
                .modify("at,1,1 e").add(leftMaxPower)
                .modify("at,1,2").add(leftAccelerationInterval)
                .modify("at,1,3").add(leftAccelerationPower)
                .modify("at,1,4").add(leftDecelerationInterval)
                .modify("at,1,5").add(leftDecelerationPower)
                .modify("at,2,1").add(rightMaxPower)
                .modify("at,2,2").add(rightAccelerationInterval)
                .modify("at,2,3").add(rightAccelerationPower)
                .modify("at,2,4").add(rightDecelerationInterval)
                .modify("at,2,5").add(rightDecelerationPower)
                .modify("at,0,6 span,3,1 center").add(runButton)
                .modify("at,0,7 span,3,1 center").add(clearButton)
                .getContainer();

        JPanel tablesPan = new GridLayoutHelper<>(new JPanel()).modify("insets,5")
                .modify("at,1,0 center").add("WheellyMeasures.left")
                .modify("at,2,0").add("WheellyMeasures.right")
                .modify("at,0,1 n").add("WheellyMeasures.increment")
                .modify("at,0,2").add("WheellyMeasures.decrement")
                .modify("at,1,1 weight,1,1 fill").add(new JScrollPane(new JTable(leftIncTable)))
                .modify("at,1,2").add(new JScrollPane(new JTable(leftDecTable)))
                .modify("at,2,1").add(new JScrollPane(new JTable(rightIncTable)))
                .modify("at,2,2").add(new JScrollPane(new JTable(rightDecTable)))
                .getContainer();
        tablesPan.setBorder(BorderFactory.createEtchedBorder());
        return new GridLayoutHelper<>(new JPanel()).modify("insets,2")
                .modify("at,0,0 ne").add(dataPan)
                .modify("at,1,0 weight,1,1 fill").add(tablesPan)
                .getContainer();
    }

    /**
     * Executes a test
     *
     * @param leftMaxPower          left maximum power
     * @param leftStepUpInterval    the left step-up interval (ms)
     * @param leftStepUpPower       the left step-up power
     * @param leftStepDownInterval  the left step-down interval (ms)
     * @param leftStepDownPower     the left step-down power
     * @param rightMaxPower         right maximum power
     * @param rightStepUpInterval   the right step-up interval (ms)
     * @param rightStepUpPower      the right step-up power
     * @param rightStepDownInterval the right step-down interval (ms)
     * @param rightStepDownPower    the right step-down power
     * @return the status record list
     */
    private Single<List<Status>> executeTest(
            int leftMaxPower,
            long leftStepUpInterval, int leftStepUpPower,
            long leftStepDownInterval, int leftStepDownPower,
            int rightMaxPower,
            long rightStepUpInterval, int rightStepUpPower,
            long rightStepDownInterval, int rightStepDownPower) {
        String args = new StringJoiner(",")
                .add(String.valueOf(leftMaxPower))
                .add(String.valueOf(leftStepUpInterval))
                .add(String.valueOf(leftStepUpPower))
                .add(String.valueOf(leftStepDownInterval))
                .add(String.valueOf(leftStepDownPower))
                .add(String.valueOf(rightMaxPower))
                .add(String.valueOf(rightStepUpInterval))
                .add(String.valueOf(rightStepUpPower))
                .add(String.valueOf(rightStepDownInterval))
                .add(String.valueOf(rightStepDownPower))
                .toString();
        // Wait for status
        return readStatus().subscribeOn(Schedulers.computation())
                .firstElement()
                .toSingle()
                .flatMap(status -> {
                            String err = invalidTestStatus(status);
                            if (err != null) {
                                logger.atError().log("{}", err);
                                return Single.error(new IllegalArgumentException(err));
                            }
                            Single<List<Status>> r = readStatus().subscribeOn(Schedulers.computation())
                                    .skipWhile(s -> !s.isTesting())
                                    .takeWhile(Status::isTesting)
                                    .toList();
                            logger.atInfo().log("Starting test ...");
                            return robotDevice.execute(new StringCommand("test", new MqttMessage(args.getBytes())), COMMAND_TIMEOUT)
                                    .flatMapSingle(ignored ->
                                            r);
                        }
                );
    }

    /**
     * Returns the invalid test status string or null if ready to test
     *
     * @param status the robot status
     */
    private String invalidTestStatus(Status status) {
        if (status == null) {
            return "Robot status missing";
        } else if (status.isTesting()) {
            return "Test is running";
        } else if (!status.frontSensor() || !status.rearSensor()) {
            return "Robot is blocked";
        } else if ((status.frontDistance() != 0 && status.frontDistance() < SAFETY_TEST_DISTANCE)
                || (status.rearDistance() != 0 && status.rearDistance() < SAFETY_TEST_DISTANCE)) {
            return "Obstacle too close";
        }
        return null;
    }

    /**
     * Handles clear button action
     *
     * @param actionEvent the action
     */
    private void onClear(ActionEvent actionEvent) {
        setResults(Map.of());
        saveResult();
    }

    /**
     * Executes the test
     *
     * @param actionEvent the event
     */
    private void onRun(ActionEvent actionEvent) {
        runButton.setEnabled(false);
        clearButton.setEnabled(false);
        executeTest(
                ((Number) leftMaxPower.getValue()).intValue(),
                ((Number) leftAccelerationInterval.getValue()).longValue(),
                ((Number) leftAccelerationPower.getValue()).intValue(),
                ((Number) leftDecelerationInterval.getValue()).longValue(),
                ((Number) leftDecelerationPower.getValue()).intValue(),
                ((Number) rightMaxPower.getValue()).intValue(),
                ((Number) rightAccelerationInterval.getValue()).longValue(),
                ((Number) rightAccelerationPower.getValue()).intValue(),
                ((Number) rightDecelerationInterval.getValue()).longValue(),
                ((Number) rightDecelerationPower.getValue()).intValue())
                .subscribe(this::onTestCompletion,
                        this::onTestError);
    }

    /**
     * Handles test completion
     *
     * @param states the states
     */
    private void onTestCompletion(List<Status> states) {
        logger.atInfo().log("Test completed ...");
        Map<SampleKey, SpeedMeasure> result = createSamplesByStatus(states);
        if (result == null) {
            logger.atError().log("Invalid test result");
            showError("Invalid test result");
        }
        addResults(result);
        saveResult();
        runButton.setEnabled(true);
        clearButton.setEnabled(true);
    }

    /**
     * Handle test error
     *
     * @param error the error
     */
    private void onTestError(Throwable error) {
        logger.atError().setCause(error).log("Test error");
        showError(error);
        runButton.setEnabled(true);
        clearButton.setEnabled(true);
    }

    /**
     * Returns the status flow
     */
    Flowable<Status> readStatus() {
        return robotDevice.readData("test", WheellyMeasures::parseStatusMessage);
    }

    /**
     * Starts the test.
     */
    private void run() throws MqttException {
        logger.atInfo().log("Starting ...");
        frame.setVisible(true);
        robotDevice.client().connect().blockingGet();
        logger.atInfo().log("Device connected.");
        setResults(loadReport(new File(args.getString("output"))));
        runButton.setEnabled(true);
        clearButton.setEnabled(true);
    }

    /**
     * Saves the result
     */
    private void saveResult() {
        try {
            exportDataCsv(new File(args.getString("output")), this.results);
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error exporting data");
        }
    }

    /**
     * Sets the result
     *
     * @param results the results
     */
    private void setResults(Map<SampleKey, SpeedMeasure> results) {
        this.results = results;
        if (results != null) {
            showData(leftIncTable, SampleType.LeftMotorIncreaseSpeed, results);
            showData(leftDecTable, SampleType.LeftMotorDecreaseSpeed, results);
            showData(rightIncTable, SampleType.RightMotorIncreaseSpeed, results);
            showData(rightDecTable, SampleType.RightMotorDecreaseSpeed, results);
        }
    }
}
