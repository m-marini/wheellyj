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
import org.jetbrains.annotations.NotNull;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.mqtt.MqttRobot;
import org.mmarini.wheelly.swing.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.RobotSpec.DISTANCE_PER_PULSE;
import static org.mmarini.wheelly.apis.RobotSpec.ROBOT_TRACK;
import static org.mmarini.wheelly.apis.Utils.SIN_1DEG;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Executes a checkup of robot by
 * <ul>
 *     <li>scanning the lidar field of view from left to right,</li>
 *     <li>turning around clockwise and counterclockwise the robot,</li>
 *     <li>for directions 0, 90, 180, 270 DEG turn the robot in direction and move ahead and back</li>
 * </ul>
 * The final report shows
 * <ul>
 *     <li>the average front and rear distances of the lidars on three samples for each direction,</li>
 *     <li>the rotation required direction, the effective rotation, the rotation time and the angle and location error</li>
 *     <li>the move required direction, the effective rotation, the move power the angle and location error</li>
 * </ul>
 */
public class RobotCheckUp {
    public static final String CHECKUP_SCHEMA_YML = "https://mmarini.org/wheelly/checkup-schema-1.0";
    public static final long SCANNER_CHECK_TIMEOUT = 10000;
    public static final int SCANNER_CHECK_SAMPLES = 3;
    public static final int SCANNER_CHECK_NUMBER = 9;
    public static final int MAX_ROTATION_SPEED = 5;
    public static final long EXTENSION_DURATION = 5000;
    public static final long ROTATION_TIMEOUT = round(PI * ROBOT_TRACK / DISTANCE_PER_PULSE / MAX_ROTATION_SPEED * 1000) + EXTENSION_DURATION;
    public static final long HALT_TIMEOUT = 500;
    public static final Complex ROTATION_TOLERANCE = Complex.fromDeg(5);
    public static final int TEST_SPEED = RobotSpec.MAX_PPS / 2;
    public static final double MOVEMENT_DISTANCE = 0.5;
    public static final long MOVEMENT_DURATION = round(MOVEMENT_DISTANCE / DISTANCE_PER_PULSE / TEST_SPEED * 1000) + EXTENSION_DURATION;
    public static final double DISTANCE_TOLERANCE = 0.1;
    private static final Logger logger = LoggerFactory.getLogger(RobotCheckUp.class);

    /**
     * Returns the command line arguments parser
     */
    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(RobotCheckUp.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run manual control robot.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show the current version");
        parser.addArgument("-c", "--config")
                .setDefault("checkup.yml")
                .help("specify the yaml configuration file");
        parser.addArgument("-v", "--verbose")
                .action(Arguments.storeTrue())
                .help("print verbose output");
        return parser;
    }

    /**
     * Runs the checks
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            new RobotCheckUp().init(args).run();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }
    private final ComMonitor comMonitor;
    private final MotionConfigPanel motionConfigPanel;
    private final JFrame frame;
    private final JFrame monitorFrame;
    private final JFrame sensorFrame;
    private final JFrame motionControllerFrame;
    private final SensorMonitor sensorMonitor;
    private final SensorsPanel sensorPanel;
    private Function<RobotStatus, FinalResult> checkupProcess;
    private RobotControllerApi controller;
    private Namespace parseArgs;
    private RobotApi robot;

    /**
     * Creates the check
     */
    public RobotCheckUp() {
        this.sensorPanel = new SensorsPanel();
        this.comMonitor = new ComMonitor();
        this.sensorMonitor = new SensorMonitor();
        this.motionConfigPanel = new MotionConfigPanel();
        comMonitor.setPrintTimestamp(true);
        this.frame = createFrame(Messages.getString("RobotCheckUp.title"), sensorPanel);
        this.monitorFrame = comMonitor.createFrame();
        this.sensorFrame = sensorMonitor.createFrame();
        this.motionControllerFrame = motionConfigPanel.createFrame();
        layHorizontally(frame, motionControllerFrame, sensorFrame, monitorFrame);
        Observable.mergeArray(
                        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(sensorFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(monitorFrame, SwingObservable.WINDOW_ACTIVE),
                        SwingObservable.window(motionControllerFrame, SwingObservable.WINDOW_ACTIVE))
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .subscribe(this::onWindowClose);
        motionConfigPanel.readCommands()
                .subscribe(this::onCommandsChange);
        sensorPanel.scanButton().addActionListener(this::onStartScan);
        sensorPanel.moveButton().addActionListener(this::onStartMove);
        sensorPanel.rotateButton().addActionListener(this::onStartRotate);
    }

    /**
     * Returns the checkup process
     *
     * @param checkUpScanner the checkup scanner function
     */
    private CompositeCheckupProcess createCheckUpProcess(Function<RobotStatus, List<ScannerResult>> checkUpScanner) {
        return new CompositeCheckupProcess(checkUpScanner);
    }

    /**
     * Returns the polling process for a scanner test.
     * <p>
     * Measures the signal for the scanner direction between -90 and 90 by 15 DEG
     * Each test takes 1.5 sec and tracks the direction and distance of each single measure
     *
     * @param directions the test directions
     */
    private Function<RobotStatus, List<ScannerResult>> createCheckUpScanner(Complex... directions) {
        return new Function<>() {
            private final List<ScannerResult> results = new ArrayList<>();
            private int currentTest = -1;
            private int measureFrontCount;
            private int measureRearCount;
            private long measureStart;
            private int sampleCount;
            private long scannerMoveTime;
            private double totFrontDistance;
            private double totRearDistance;

            @Override
            public List<ScannerResult> apply(RobotStatus status) {
                long time = status.simulationTime();
                RobotCommands command = RobotCommands.haltMove();
                // Check for first sample
                if (currentTest < 0) {
                    // First sample
                    currentTest = 0;
                    // Check for no test required
                    if (currentTest == directions.length) {
                        // Execute a halt move command
                        controller.execute(command);
                        sensorPanel.setInfo("");
                        return results;
                    }
                    // move head
                    controller.execute(command.setScan(directions[currentTest]));
                    // clean measures (time, counter, accumulator)
                    measureStart = time;
                    totFrontDistance = totRearDistance = 0;
                    sampleCount = 0;
                    measureFrontCount = measureRearCount = 0;
                    scannerMoveTime = 0;
                    // set scan command to the current test direction
                    command = command.setScan(directions[currentTest]);
                    logger.atInfo().log("Checking sensor to {} DEG ...", directions[currentTest].toIntDeg());
                    sensorPanel.setInfo(format("Checking sensor to %d DEG ...", directions[currentTest].toIntDeg()));
                }
                Complex sensDir = directions[currentTest];
                Complex dir = status.headDirection();
                // Check for head to the required direction
                if (dir.isCloseTo(sensDir, SIN_1DEG)) {
                    // Increase scan counter
                    sampleCount++;
                    // Set head move time measure
                    if (scannerMoveTime == 0) {
                        scannerMoveTime = time - measureStart;
                    }
                    // add distance accumulator and increase the measure counter
                    double sensorFrontDistance = status.frontDistance();
                    if (sensorFrontDistance > 0) {
                        totFrontDistance += sensorFrontDistance;
                        measureFrontCount++;
                    }
                    double sensorRearDistance = status.rearDistance();
                    if (sensorRearDistance > 0) {
                        totRearDistance += sensorRearDistance;
                        measureRearCount++;
                    }
                    logger.atInfo().log("Sample measure counter {} ...", sampleCount);
                }
                // Check for scan completion
                if (sampleCount >= SCANNER_CHECK_SAMPLES || time >= measureStart + SCANNER_CHECK_TIMEOUT) {
                    // Computes the results
                    results.add(new ScannerResult(sensDir,
                            time - measureStart,
                            sampleCount > 0, scannerMoveTime,
                            measureFrontCount > 0 ? totFrontDistance / measureFrontCount : 0,
                            measureRearCount > 0 ? totRearDistance / measureRearCount : 0,
                            status.imuFailure()));
                    // step to next test
                    currentTest++;
                    // Check for last direction test
                    if (currentTest >= directions.length) {
                        controller.execute(command.setScan(Complex.DEG0));
                        sensorPanel.setInfo("");
                        return results;
                    }
                    // Move head the next direction
                    controller.execute(command.setScan(directions[currentTest]));
                    logger.atInfo().log("Checking sensor to {} DEG ...", directions[currentTest].toIntDeg());
                    sensorPanel.setInfo(format("Checking sensor to %d DEG ...", directions[currentTest].toIntDeg()));
                    // reset all measures
                    measureStart = time;
                    totFrontDistance = totRearDistance = 0;
                    sampleCount = 0;
                    measureFrontCount = measureRearCount = 0;
                    scannerMoveTime = 0;
                }
                return null;
            }
        };
    }

    /**
     * Initialises the check
     *
     * @param args the command line arguments
     * @throws ArgumentParserException in case of error
     */
    private RobotCheckUp init(String[] args) throws ArgumentParserException {
        parseArgs = createParser().parseArgs(args);
        this.sensorPanel.verbose(parseArgs.getBoolean("verbose"));
        return this;
    }

    /**
     * Handles command changed
     *
     * @param commands the commands
     */
    private void onCommandsChange(String[] commands) {
        if (robot instanceof MqttRobot mqttRobot) {
            mqttRobot.configure(commands);
        }
    }

    private void onControlStatus(String status) {
        comMonitor.onControllerStatus(status);
        sensorMonitor.onControllerStatus(status);
    }

    /**
     * Handles the controller errors
     *
     * @param err the error
     */
    private void onControllerError(Throwable err) {
        comMonitor.onError(err);
        logger.atError().setCause(err).log("Controller error");
    }

    /**
     * Handles the flow error
     *
     * @param error the error
     */
    private void onFlowError(Throwable error) {
        logger.atError().setCause(error).log("Flow error");
    }

    /**
     * Handle inference
     *
     * @param status the status
     */
    private void onInference(RobotStatus status) {
        sensorPanel.setStatus(status);
        sensorMonitor.onStatus(status);
        Function<RobotStatus, FinalResult> cp = checkupProcess;
        if (cp != null) {
            FinalResult result = cp.apply(status);
            if (result != null) {
                processResult(result);
                checkupProcess = null;
                sensorPanel.enableButtons(true);
            }
        }
    }

    /**
     * Handles shutdown event (when controller has shut down)
     */
    private void onShutdown() {
        frame.dispose();
        monitorFrame.dispose();
        sensorFrame.dispose();
        System.exit(0);
    }

    /**
     * Handles start movement checkup
     *
     * @param actionEvent the event
     */
    private void onStartMove(ActionEvent actionEvent) {
        sensorPanel.enableButtons(false);
        startMoveCheckup();
    }

    /**
     * Handles start rotation checkup
     *
     * @param actionEvent the event
     */
    private void onStartRotate(ActionEvent actionEvent) {
        sensorPanel.enableButtons(false);
        startRotateCheckup();
    }

    /**
     * Handles start button event
     *
     * @param actionEvent the event
     */
    private void onStartScan(ActionEvent actionEvent) {
        sensorPanel.enableButtons(false);
        startScanCheckup();
    }

    /**
     * Handles the windows close
     *
     * @param windowEvent the event
     */
    private void onWindowClose(WindowEvent windowEvent) {
        if (controller != null) {
            controller.shutdown();
        }
    }

    /**
     * Process the result creating report frame
     *
     * @param result the result
     */
    private void processResult(FinalResult result) {
        JTextArea view = new JTextArea();
        view.setEditable(false);
        view.setFont(Font.decode("Monospaced"));

        if (sensorPanel.verbose()) {
            result.getDetailsStream().forEach(line -> {
                logger.info(line);
                view.append(line + System.lineSeparator());
            });
        }
        result.getSummaryStream().forEach(line -> {
            logger.info(line);
            view.append(line + System.lineSeparator());
        });

        JFrame resultFrame = createFrame(Messages.getString("Result.title"), new JScrollPane(view));
        resultFrame.setLocation(50, 50);
        resultFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        resultFrame.setVisible(true);
    }

    /**
     * Runs the check
     */
    private void run() throws Throwable {
        logger.info("Robot check started.");
        File configFile = new File(parseArgs.getString("config"));
        JsonNode config = fromFile(configFile);
        WheellyJsonSchemas.instance().validateOrThrow(config, CHECKUP_SCHEMA_YML);

        logger.info("Creating robot ...");
        robot = AppYaml.robotFromJson(config);

        logger.info("Creating controller ...");
        controller = AppYaml.controllerFromJson(config);
        controller.connectRobot(robot);

        controller.readErrors().subscribe(
                this::onControllerError,
                this::onFlowError);

        // Create flow
        controller.readControllerStatus()
                .map(ControllerStatusMapper::map)
                .distinct()
                .subscribe(
                        this::onControlStatus,
                        this::onFlowError);
        controller.readShutdown()
                .subscribe(this::onShutdown,
                        this::onFlowError);
        controller.setOnInference(this::onInference);
        frame.setVisible(true);
        sensorFrame.setVisible(true);
        motionControllerFrame.setVisible(true);
        monitorFrame.setVisible(true);
        monitorFrame.setState(JFrame.ICONIFIED);
        if (robot instanceof MqttRobot mqttRobot) {
            motionConfigPanel.commands(
                    mqttRobot.configCommands()
            );
        }

        controller.start();
        sensorPanel.setInfo("Controller started");
    }

    /**
     * Runs the check-up
     */
    private void startMoveCheckup() {
        double headRangeRad = robot.robotSpec().headFOV().toRad() / 2;

        checkupProcess = createCheckUpProcess(
                createCheckUpScanner())
                // turn the robot in directions 0, 90, 180, 20 DEG and move ahead and back in each step
                .addMoveTest(Complex.DEG0, TEST_SPEED)
                .addMoveTest(Complex.DEG0, -TEST_SPEED)

                .addRotateTest(Complex.DEG90)
                .addMoveTest(Complex.DEG90, TEST_SPEED)
                .addMoveTest(Complex.DEG90, -TEST_SPEED)

                .addRotateTest(Complex.DEG180)
                .addMoveTest(Complex.DEG180, TEST_SPEED)
                .addMoveTest(Complex.DEG180, -TEST_SPEED)

                .addRotateTest(Complex.DEG270)
                .addMoveTest(Complex.DEG270, TEST_SPEED)
                .addMoveTest(Complex.DEG270, -TEST_SPEED)

                .addRotateTest(Complex.DEG0);
    }

    /**
     * Runs the check-up
     */
    private void startRotateCheckup() {
        checkupProcess = createCheckUpProcess(
                // Scan from left to right for the head field of view
                createCheckUpScanner())
                // Rotate clockwise and counterclockwise the robot
                .addRotateTest(Complex.DEG0)
                .addRotateTest(Complex.DEG90)
                .addRotateTest(Complex.DEG180)
                .addRotateTest(Complex.DEG270)
                .addRotateTest(Complex.DEG0)
                .addRotateTest(Complex.DEG270)
                .addRotateTest(Complex.DEG180)
                .addRotateTest(Complex.DEG90)
                .addRotateTest(Complex.DEG0);
    }

    /**
     * Runs the check-up
     */
    private void startScanCheckup() {
        double headRangeRad = robot.robotSpec().headFOV().toRad() / 2;
        checkupProcess = createCheckUpProcess(
                // Scan from left to right for the head field of view
                createCheckUpScanner(
                        IntStream.range(0, SCANNER_CHECK_NUMBER)
                                .mapToObj(i ->
                                        Complex.fromRad(headRangeRad * 2 * i / (SCANNER_CHECK_NUMBER - 1) - headRangeRad)
                                ).toArray(Complex[]::new))
        );
    }

    record FinalResult(List<ScannerResult> scannerResults, List<RotateResult> rotateResults,
                       List<MovementResult> moveResults) {

        public Stream<String> getDetailsStream() {
            Stream.Builder<String> builder = Stream.builder();
            builder.add("");
            builder.add("Check up details");
            builder.add("");
            for (ScannerResult scannerResult : scannerResults) {
                builder.add(format("Sensor direction check %d DEG", scannerResult.direction.toIntDeg()));
                builder.add(format("    %s", scannerResult.valid ? "valid" : "invalid"));
                builder.add(format("    duration %d ms", scannerResult.testDuration));
                builder.add(format("    move localTime %d ms", scannerResult.moveTime));
                builder.add(format("    average front distance %.3f m", scannerResult.averageFrontDistance));
                builder.add(format("    average rear distance %.3f m", scannerResult.averageRearDistance));
            }

            for (RotateResult result : rotateResults) {
                builder.add(format("Rotate direction %d DEG", result.targetDir.toIntDeg()));
                builder.add(format("    rotation %d DEG", result.rotationAngle.toIntDeg()));
                builder.add(format("    duration %d ms", result.testDuration));
                builder.add(format("    direction error %d DEG", result.directionError.toIntDeg()));
                builder.add(format("    location error %.2f m", result.locationError));
            }
            for (MovementResult result : moveResults) {
                builder.add(format("Movement direction %d DEG, power %.1f", result.direction.toIntDeg(), result.speed));
                builder.add(format("    distance %.2f m", result.distance));
                builder.add(format("    duration %d ms", result.testDuration));
                builder.add(format("    distance error %.2f m", result.distanceError));
                builder.add(format("    direction error %d DEG", result.directionError.toIntDeg()));
            }
            return builder.build();
        }

        public Stream<String> getSummaryStream() {
            Stream.Builder<String> builder = Stream.builder();
            long imuFailure = scannerResults.stream().filter(r -> r.imuFailure != 0).count() +
                    rotateResults.stream().filter(r -> r.imuFailure != 0).count() +
                    moveResults.stream().filter(r -> r.imuFailure != 0).count();

            builder.add("");
            builder.add("Check summary");
            builder.add("");
            if (imuFailure > 0) {
                builder.add(format("%d imu failure", imuFailure));
            } else {
                builder.add("No imu failure");
            }

            long maxMoveDuration = scannerResults.stream()
                    .mapToLong(r -> r.moveTime)
                    .max()
                    .orElse(0);

            double minFrontDistance = Double.MAX_VALUE;
            Complex minFrontDistanceDirection = Complex.DEG0;
            double maxFrontDistance = 0;
            Complex maxFrontDistanceDirection = Complex.DEG0;
            boolean validFrontSamples = false;
            double minRearDistance = Double.MAX_VALUE;
            Complex minRearDistanceDirection = Complex.DEG0;
            double maxRearDistance = 0;
            Complex maxRearDistanceDirection = Complex.DEG0;
            boolean validRearSamples = false;
            for (int i = 1; i < scannerResults.size(); i++) {
                ScannerResult r = scannerResults.get(i);
                if (r.valid) {
                    if (r.averageFrontDistance > 0) {
                        validFrontSamples = true;
                        if (r.averageFrontDistance < minFrontDistance) {
                            minFrontDistance = r.averageFrontDistance;
                            minFrontDistanceDirection = r.direction;
                        }
                        if (r.averageFrontDistance > maxFrontDistance) {
                            maxFrontDistance = r.averageFrontDistance;
                            maxFrontDistanceDirection = r.direction;
                        }
                    }
                    if (r.averageRearDistance > 0) {
                        validRearSamples = true;
                        if (r.averageRearDistance < minRearDistance) {
                            minRearDistance = r.averageRearDistance;
                            minRearDistanceDirection = r.direction;
                        }
                        if (r.averageRearDistance > maxRearDistance) {
                            maxRearDistance = r.averageRearDistance;
                            maxRearDistanceDirection = r.direction;
                        }
                    }
                }
            }

            long numScannerFailures = scannerResults.stream().filter(r -> !r.valid).count();
            if (numScannerFailures > 0) {
                builder.add(format("Scanner checks failed: %d failures", numScannerFailures));
            } else {
                builder.add("Scanner checks passed");
            }
            builder.add(format("    move localTime <= %d ms", maxMoveDuration));
            if (validFrontSamples) {
                builder.add(format("    min front distance = %.3f m at %d DEG",
                        minFrontDistance, minFrontDistanceDirection.toIntDeg()));
                builder.add(format("    max front distance = %.3f m at %d DEG",
                        maxFrontDistance, maxFrontDistanceDirection.toIntDeg()));
            }
            if (validRearSamples) {
                builder.add(format("    min rear distance = %.3f m at %d DEG",
                        minRearDistance, minRearDistanceDirection.toIntDeg()));
                builder.add(format("    max rear distance = %.3f m at %d DEG",
                        maxRearDistance, maxRearDistanceDirection.toIntDeg()));
            }

            long numRotationFailure = rotateResults.stream()
                    .filter(result -> result.directionError.sub(ROTATION_TOLERANCE).positive()
                            || result.imuFailure != 0).count();
            Complex maxDirectionError = Complex.DEG0;
            double maxLocationError = 0;
            double maxRotationSpeed = 0;
            for (RotateResult r : rotateResults) {
                if (r.imuFailure == 0) {
                    if (r.directionError.sub(maxDirectionError).positive()) {
                        maxDirectionError = r.directionError;
                    }
                    if (r.locationError > maxLocationError) {
                        maxLocationError = r.locationError;
                    }
                    if (r.testDuration > 0) {
                        double speed = abs(r.rotationAngle.toDeg()) / r.testDuration * 1000;
                        if (speed > maxRotationSpeed) {
                            maxRotationSpeed = speed;
                        }
                    }
                }
            }

            long numMovementFailure = moveResults.stream()
                    .filter(result -> result.directionError.sub(ROTATION_TOLERANCE).positive()
                            || result.distanceError > DISTANCE_TOLERANCE
                            || result.imuFailure != 0).count();
            Complex maxMoveDirectionError = Complex.DEG0;
            double maxMoveDistanceError = 0;
            double maxSpeed = 0;
            double maxMoveDistance = 0;
            for (MovementResult r : moveResults) {
                if (r.imuFailure == 0) {
                    if (r.distance > maxMoveDistance) {
                        maxMoveDistance = r.distance;
                    }
                    if (r.distanceError > maxMoveDistanceError) {
                        maxMoveDistanceError = r.distanceError;
                    }
                    if (r.directionError.sub(maxMoveDirectionError).positive()) {
                        maxMoveDirectionError = r.directionError;
                    }
                    double speed = r.distance / r.testDuration * 1000;
                    if (speed > maxSpeed) {
                        maxSpeed = speed;
                    }
                }
            }

            if (numRotationFailure > 0) {
                builder.add(format("Rotation checks failed: %d failures", numRotationFailure));
            } else {
                builder.add("Rotation checks passed");
            }

            builder.add(format("    direction error <= %d DEG", maxDirectionError.toIntDeg()));
            builder.add(format("    location error <= %.2f m", maxLocationError));
            builder.add(format("    rotation power <= %.2f DEG/s", maxRotationSpeed));

            if (numMovementFailure > 0) {
                builder.add(format("Movement checks failed: %d failures", numMovementFailure));
            } else {
                builder.add("Movement checks passed");
            }

            builder.add(format("    distance <= %.2f m", maxMoveDistance));
            builder.add(format("    power <= %.2f m/s", maxSpeed));
            builder.add(format("    distance error <= %.2f m", maxMoveDistanceError));
            builder.add(format("    direction error <=  %d DEG", maxMoveDirectionError.toIntDeg()));

            return builder.build();
        }
    }

    record MovementResult(Complex direction, double speed, long testDuration, double distance, double distanceError,
                          Complex directionError, int imuFailure) {
    }

    /**
     * Rotation result
     */
    static class RotateResult {
        public final Complex directionError;
        public final int imuFailure;
        public final double locationError;
        public final Complex rotationAngle;
        public final Complex targetDir;
        public final long testDuration;


        RotateResult(long testDuration, Complex targetDir, Complex directionError, double distanceError, Complex rotationAngle, int imuFailure) {
            this.testDuration = testDuration;
            this.targetDir = targetDir;
            this.directionError = directionError;
            this.locationError = distanceError;
            this.imuFailure = imuFailure;
            this.rotationAngle = rotationAngle;
        }
    }

    /**
     * Scanner result
     */
    record ScannerResult(Complex direction, long testDuration, boolean valid, long moveTime,
                         double averageFrontDistance,
                         double averageRearDistance, int imuFailure) {
    }

    class CompositeCheckupProcess implements Function<RobotStatus, FinalResult> {
        private final List<Predicate<RobotStatus>> checkList;
        private final Function<RobotStatus, List<ScannerResult>> checkUpScanner;
        private final List<MovementResult> moveResults;
        private final List<RotateResult> rotateResults;
        private int currentCheck;
        private List<ScannerResult> scannerResults;

        CompositeCheckupProcess(Function<RobotStatus, List<ScannerResult>> checkUpScanner) {
            this.checkUpScanner = checkUpScanner;
            rotateResults = new ArrayList<>();
            moveResults = new ArrayList<>();
            checkList = new ArrayList<>();
        }

        /**
         * Adds the move test
         * Move the robot to the given direction at test power
         * until the test distance has reached or a frontal or rear obstacle has found or test time elapsed
         *
         * @param direction the direction
         * @param speed     the power
         */
        public CompositeCheckupProcess addMoveTest(Complex direction, int speed) {
            checkList.add(new Predicate<>() {
                private long moveStart;
                private Point2D startLocation;

                @Override
                public boolean test(RobotStatus status) {
                    long time = status.simulationTime();
                    RobotCommands command = RobotCommands.none();
                    if (startLocation == null) {
                        // store start location, time, rotation at the beginning of test
                        startLocation = status.location();
                        moveStart = time;
                        controller.execute(command.setMove(direction, speed));
                        sensorPanel.setInfo(format("Checking movement to %d DEG, power %d ...", direction.toIntDeg(), speed));
                    }
                    // Check for maximum distance travelled or test timeout or obstacle found
                    Point2D pos = status.location();
                    double distance = pos.distance(startLocation);
                    if (distance >= MOVEMENT_DISTANCE
                            || time >= moveStart + MOVEMENT_DURATION
                            || !(status.canMoveForward() || status.canMoveBackward())) {
                        // Test completed
                        // computes the distance error
                        Point2D targetLocation = direction.at(startLocation, MOVEMENT_DISTANCE);
                        double distanceError = targetLocation.distance(pos);
                        // computes the direction error
                        Complex realDirection = Complex.direction(startLocation, pos);
                        Complex directionError = realDirection.sub(direction);
                        sensorPanel.setInfo("");
                        moveResults.add(new MovementResult(direction,
                                speed,
                                time - moveStart,
                                distance,
                                distanceError,
                                directionError.abs(),
                                status.imuFailure()));
                        sensorPanel.setInfo("");
                        controller.execute(command.setHalt());
                        return true;
                    }
                    return false;
                }
            });
            return this;
        }

        /**
         * Adds rotation test
         *
         * @param direction the direction
         */
        public CompositeCheckupProcess addRotateTest(Complex direction) {
            checkList.add(new Predicate<>() {
                private long haltTime;
                private long rotationStart;
                private Complex startAngle;
                private Point2D startLocation;

                @Override
                public boolean test(RobotStatus status) {
                    long time = status.simulationTime();
                    Complex dir = status.direction();
                    RobotCommands command = RobotCommands.none();
                    if (startLocation == null) {
                        // store start location, time, rotation at the beginning of test
                        startLocation = status.location();
                        rotationStart = time;
                        startAngle = dir;
                        // Move to the desired direction at 0 power
                        controller.execute(command.setMove(direction, 0));
                        sensorPanel.setInfo(format("Checking rotation to %d DEG ...", direction.toIntDeg()));
                    }
                    if (time >= rotationStart + ROTATION_TIMEOUT) {
                        // Rotation timeout
                        Complex directionError = dir.sub(direction);
                        Complex rotationAngle = dir.sub(startAngle);
                        double distanceError = status.location().distance(startLocation);
                        rotateResults.add(new RotateResult(time - rotationStart, direction, directionError, distanceError, rotationAngle, status.imuFailure()));
                        controller.execute(command.setHalt());
                        sensorPanel.setInfo("");
                        return true;
                    }
                    if (status.leftPps() == 0 && status.rightPps() == 0) {
                        // the robot is not moving
                        if (haltTime == 0) {
                            // Store halt time
                            haltTime = time;
                        }
                        Complex directionError = dir.sub(direction);
                        if (directionError.isCloseTo(Complex.DEG0, ROTATION_TOLERANCE)) {
                            // Rotation completed
                            Complex rotationAngle = dir.sub(startAngle);
                            double distanceError = status.location().distance(startLocation);
                            rotateResults.add(new RotateResult(time - rotationStart, direction, directionError, distanceError, rotationAngle, status.imuFailure()));
                            controller.execute(command.setHalt());
                            sensorPanel.setInfo("");
                            return true;
                        } else if (time >= haltTime + HALT_TIMEOUT) {
                            // Halt timeout
                            Complex rotationAngle = dir.sub(startAngle);
                            double distanceError = status.location().distance(startLocation);
                            rotateResults.add(new RotateResult(time - rotationStart, direction, directionError, distanceError, rotationAngle, status.imuFailure()));
                            controller.execute(command.setHalt());
                            sensorPanel.setInfo("");
                            return true;
                        }
                    } else {
                        // The robot is moving
                        haltTime = 0;
                    }
                    return false;
                }
            });
            return this;
        }

        @Override
        public FinalResult apply(RobotStatus status) {
            if (scannerResults == null) {
                scannerResults = checkUpScanner.apply(status);
                return null;
            }
            if (currentCheck >= checkList.size()) {
                return new FinalResult(scannerResults, rotateResults, moveResults);
            }
            if (checkList.get(currentCheck).test(status)) {
                currentCheck++;
                if (currentCheck >= checkList.size()) {
                    return new FinalResult(scannerResults, rotateResults, moveResults);
                }
            }
            return null;
        }
    }
}
