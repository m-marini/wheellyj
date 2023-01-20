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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.Utils;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.wheelly.swing.SensorsPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;
import static org.mmarini.wheelly.apps.Wheelly.fromConfig;


public class RobotCheckUp {
    public static final long INTERVAL = 100;
    public static final long SCANNER_CHECK_DURATION = 1000;
    public static final long COMMAND_INTERVAL = 600;
    public static final long ROTATION_DURATION = 3000;
    public static final long HALT_DURATION = 500;
    public static final long MOVEMENT_DURATION = 2000;
    public static final int SENSOR_STEP = 30;
    public static final int SUPPLY_SAMPLES = 10;
    public static final int ROTATION_TOLERANCE = 5;
    public static final double DISTANCE_TOLERANCE = 0.1;
    private static final Logger logger = LoggerFactory.getLogger(RobotCheckUp.class);
    private static final int TEST_SPEED = 20;

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

    private final long interval;
    private final long scannerCheckDuration;
    private final long commandInterval;
    private final long rotationDuration;
    private final long haltDuration;
    private final int supplySamples;
    private final long movementDuration;
    private final SensorsPanel sensorPanel;
    private Namespace parseArgs;
    private RobotApi robot;
    private Completable monitorStop;

    /**
     * Creates the check
     */
    public RobotCheckUp() {
        this.interval = INTERVAL;
        this.scannerCheckDuration = SCANNER_CHECK_DURATION;
        this.commandInterval = COMMAND_INTERVAL;
        this.rotationDuration = ROTATION_DURATION;
        this.haltDuration = HALT_DURATION;
        this.supplySamples = SUPPLY_SAMPLES;
        this.movementDuration = MOVEMENT_DURATION;
        this.sensorPanel = new SensorsPanel();
    }

    private MovementResult checkMove(int direction, int speed) {
        logger.info("Checking movement to {} DEG, speed {} ...", direction, speed);
        sensorPanel.setInfo(format("Checking movement to %d DEG, speed %d ...", direction, speed));
        RobotStatus status = robot.getStatus();
        long time = status.getTime();
        Point2D startLocation = status.getLocation();
        long movementStart = time;
        long commandTimeout = 0;
        long testTimeout = time + this.movementDuration;
        while (time < testTimeout) {
            if (time > commandTimeout) {
                robot.move(direction, speed);
                commandTimeout = time + commandInterval;
            }
            robot.tick(interval);
            status = robot.getStatus();
            sensorPanel.setStatus(status);
            time = status.getTime();
        }
        robot.halt();
        robot.tick(interval);
        status = robot.getStatus();
        sensorPanel.setStatus(status);
        time = status.getTime();
        Point2D pos = status.getLocation();
        double distance = pos.distance(startLocation);
        Point2D targetLocation = new Point2D.Double(
                startLocation.getX() + distance * sin(toRadians(direction)),
                startLocation.getY() + distance * cos(toRadians(direction)));
        double distanceError = targetLocation.distance(pos);

        int realDirection = (int) round(toDegrees(Utils.direction(startLocation, pos)));
        int directionError = normalizeDegAngle(realDirection - direction);
        sensorPanel.setInfo("");
        return new MovementResult(direction,
                speed,
                time - movementStart,
                distance,
                distanceError,
                abs(directionError),
                robot.getStatus().getImuFailure());
    }

    /**
     * Checks rotation
     *
     * @param targetDir target direction DEG
     */
    private RotateResult checkRotate(int targetDir) {
        logger.info("Checking rotation to {} DEG ...", targetDir);
        sensorPanel.setInfo(format("Checking rotation to %d DEG ...", targetDir));
        RobotStatus status = robot.getStatus();
        long time = status.getTime();
        Point2D startLocation = status.getLocation();
        long startDirection = time;
        long commandTimeout = 0;
        long testTimeout = time + rotationDuration;
        long haltTime = 0;
        int startAngle = status.getDirection();
        while (time < testTimeout) {
            if (time > commandTimeout) {
                robot.move(targetDir, 0);
                commandTimeout = time + commandInterval;
            }
            robot.tick(interval);
            status = robot.getStatus();
            sensorPanel.setStatus(status);
            time = status.getTime();
            if (status.getLeftPps() == 0 && status.getRightPps() == 0) {
                if (haltTime == 0) {
                    haltTime = time;
                }
                if ((time - haltTime) > this.haltDuration) {
                    break;
                }
            } else {
                haltTime = 0;
            }
        }
        robot.halt();
        robot.tick(interval);
        status = robot.getStatus();
        sensorPanel.setStatus(status);
        time = status.getTime();
        int dir = status.getDirection();
        int directionError = abs(normalizeDegAngle(dir - targetDir));
        int rotationAngle = normalizeDegAngle(dir - startAngle);
        double distanceError = status.getLocation().distance(startLocation);
        robot.halt();
        sensorPanel.setInfo("");
        return new RotateResult(time - startDirection, targetDir, directionError, distanceError, rotationAngle, robot.getStatus().getImuFailure());
    }

    /**
     * Runs a scanner test.
     * Measures the signal for the scanner direction between -90 and 90 by 15 DEG
     * Each test takes 1 sec and tracks the direction and distance of each single measure
     */
    private List<ScannerResult> checkScanner() {
        robot.halt();
        robot.tick(this.interval);
        RobotStatus status = robot.getStatus();
        sensorPanel.setStatus(status);
        long time = status.getTime();
        int sensDir = -90;
        long commandTimeout = time + commandInterval;
        long measureTimeout = time + scannerCheckDuration;
        logger.info("Checking sensor to {} DEG ...", sensDir);
        sensorPanel.setInfo(format("Checking sensor to %d DEG ...", sensDir));
        robot.scan(sensDir);
        long measureStart = time;
        long scannerMoveTime = 0;
        int measureCount = 0;
        int sampleCount = 0;
        double totDistance = 0;
        List<ScannerResult> results = new ArrayList<>();
        for (; ; ) {
            robot.tick(this.interval);
            status = robot.getStatus();
            time = status.getTime();
            sensorPanel.setStatus(status);

            int dir = status.getSensorDirection();
            if (dir == sensDir) {
                sampleCount++;
                if (scannerMoveTime == 0) {
                    scannerMoveTime = time - measureStart;
                }
                double sensorDistance = status.getEchoDistance();
                if (sensorDistance > 0) {
                    totDistance += sensorDistance;
                    measureCount++;
                }
            }

            if (time >= measureTimeout) {
                results.add(new ScannerResult(sensDir,
                        time - measureStart,
                        sampleCount > 0, scannerMoveTime,
                        measureCount > 0 ? totDistance / measureCount : 0,
                        robot.getStatus().getImuFailure()));
                sensDir += SENSOR_STEP;
                if (sensDir > 90) {
                    break;
                }

                logger.info("Checking sensor to {} DEG", sensDir);
                measureStart = time;
                scannerMoveTime = 0;
                measureCount = 0;
                totDistance = 0;
                robot.scan(sensDir);
                measureTimeout = time + scannerCheckDuration;
                commandTimeout = time + commandInterval;
            }
            if (time >= commandTimeout) {
                robot.scan(sensDir);
                commandTimeout = time + commandInterval;
            }
        }
        robot.scan(0);
        sensorPanel.setInfo("");
        return results;
    }

    /**
     * Runs the check supply and returns the average supply voltage
     */
    private double checkSupply() {
        logger.info("Checking supply ...");
        double sum = 0;
        sensorPanel.setInfo("Checking supply ...");
        for (int i = 0; i <= supplySamples; i++) {
            robot.tick(interval);
            RobotStatus status = robot.getStatus();
            sensorPanel.setStatus(status);
            sum += status.getSupplyVoltage();
        }
        sensorPanel.setInfo("");
        return sum / supplySamples;
    }

    /**
     * Returns the robot api
     */
    protected RobotApi createRobot() {
        return fromConfig(parseArgs.getString("robot"), new Object[0], new Class[0]);
    }

    /**
     * Initialize the check
     *
     * @param args the command line arguments
     * @throws ArgumentParserException in case of error
     */
    private RobotCheckUp init(String[] args) throws ArgumentParserException {
        parseArgs = createParser().parseArgs(args);
        return this;
    }

    private void processResult(FinalResult result) {
        JTextArea view = new JTextArea();
        view.setEditable(false);
        view.setFont(Font.decode("Monospaced"));

        if (parseArgs.getBoolean("verbose")) {
            result.getDetailsStream().forEach(line -> {
                logger.info(line);
                view.append(line + System.lineSeparator());
            });
        }
        result.getSummaryStream().forEach(line -> {
            logger.info(line);
            view.append(line + System.lineSeparator());
        });

        JFrame resultFrame = new JFrame("Result");
        Container contentPane = resultFrame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(new JScrollPane(view), BorderLayout.CENTER);
        resultFrame.setSize(800, 600);
        resultFrame.setLocation(50, 50);
        resultFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        resultFrame.setVisible(true);
    }

    /**
     * Runs the check
     */
    private void run() {
        logger.info("Robot check started.");
        this.robot = createRobot();
        JFrame frame = new JFrame("Robot check up");
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(sensorPanel, BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setVisible(true);
        SwingObservable.actions(sensorPanel.getCheckUpButton()).toFlowable(BackpressureStrategy.LATEST)
                .doOnNext(ev -> {
                    sensorPanel.getCheckUpButton().setEnabled(false);
                    stopSensorsMonitor().doOnComplete(() -> {
                        Single<FinalResult> result = startCheckUp();
                        result.doOnSuccess(res -> {
                            processResult(res);
                            startSensorsMonitor();
                            sensorPanel.getCheckUpButton().setEnabled(true);
                        }).subscribe();
                    }).subscribe();
                }).subscribe();

        robot.start();
        startSensorsMonitor();

        // Prints the results
    /*
        if (parseArgs.getBoolean("verbose")) {
            printDetails(rotateResults, moveResults, scannerResults);
        }

        printSummary(scannerResults, rotateResults, moveResults, averageSupply);
    }
    //logger.info("Robot check completed.");

     */
    }

    /**
     * Runs the check-up
     */
    private Single<FinalResult> startCheckUp() {
        SingleSubject<FinalResult> result = SingleSubject.create();
        Schedulers.io().scheduleDirect(() -> {
            logger.info("Robot check up started.");
            List<RotateResult> rotateResults = new ArrayList<>();
            List<MovementResult> moveResults = new ArrayList<>();

            // Runs the tests
            List<ScannerResult> scannerResults = checkScanner();

            rotateResults.add(checkRotate(0));
            rotateResults.add(checkRotate(90));
            rotateResults.add(checkRotate(180));
            rotateResults.add(checkRotate(-90));
            rotateResults.add(checkRotate(0));
            rotateResults.add(checkRotate(-90));
            rotateResults.add(checkRotate(-180));
            rotateResults.add(checkRotate(90));

            rotateResults.add(checkRotate(0));
            moveResults.add(checkMove(0, TEST_SPEED));
            moveResults.add(checkMove(0, -TEST_SPEED));

            rotateResults.add(checkRotate(90));
            moveResults.add(checkMove(90, TEST_SPEED));
            moveResults.add(checkMove(90, -TEST_SPEED));

            rotateResults.add(checkRotate(180));
            moveResults.add(checkMove(180, TEST_SPEED));
            moveResults.add(checkMove(180, -TEST_SPEED));

            rotateResults.add(checkRotate(-90));
            moveResults.add(checkMove(-90, TEST_SPEED));
            moveResults.add(checkMove(-90, -TEST_SPEED));

            rotateResults.add(checkRotate(0));

            double averageSupply = checkSupply();
            logger.info("Robot check up completed.");
            result.onSuccess(new FinalResult(scannerResults, rotateResults, moveResults, averageSupply));
        });
        return result;
    }

    private void startSensorsMonitor() {
        if (monitorStop == null) {
            CompletableSubject stop = CompletableSubject.create();
            monitorStop = stop;
            Schedulers.io().scheduleDirect(() -> {
                logger.info("Sensor monitor started.");
                while (monitorStop != null) {
                    robot.tick(this.interval);
                    RobotStatus status = robot.getStatus();
                    sensorPanel.setStatus(status);
                }
                logger.info("Sensor monitor stopped.");
                stop.onComplete();
            });
        }
    }

    private Completable stopSensorsMonitor() {
        Completable result = monitorStop;
        if (result == null) {
            return Completable.complete();
        } else {
            monitorStop = null;
            return result;
        }
    }

    static class FinalResult {
        public final double averageSupply;
        public final List<MovementResult> moveResults;
        public final List<RotateResult> rotateResults;
        public final List<ScannerResult> scannerResults;

        FinalResult(List<ScannerResult> scannerResults, List<RotateResult> rotateResults, List<MovementResult> moveResults, double averageSupply) {
            this.scannerResults = scannerResults;
            this.rotateResults = rotateResults;
            this.moveResults = moveResults;
            this.averageSupply = averageSupply;
        }

        public Stream<String> getDetailsStream() {
            Stream.Builder<String> builder = Stream.builder();
            builder.add("");
            builder.add("Check up details");
            builder.add("");
            for (ScannerResult scannerResult : scannerResults) {
                builder.add(format("Sensor direction check %d DEG", scannerResult.direction));
                builder.add(format("    %s", scannerResult.valid ? "valid" : "invalid"));
                builder.add(format("    duration %d ms", scannerResult.testDuration));
                builder.add(format("    move time %d ms", scannerResult.moveTime));
                builder.add(format("    average distance %.2f m", scannerResult.averageDistance));
            }

            for (RotateResult result : rotateResults) {
                builder.add(format("Rotate direction %d DEG", result.targetDir));
                builder.add(format("    rotation %d DEG", result.rotationAngle));
                builder.add(format("    duration %d ms", result.testDuration));
                builder.add(format("    direction error %d DEG", result.directionError));
                builder.add(format("    location error %.2f m", result.locationError));
            }
            for (MovementResult result : moveResults) {
                builder.add(format("Movement direction %d DEG, speed %.1f", result.direction, result.speed));
                builder.add(format("    distance %.2f m", result.distance));
                builder.add(format("    duration %d ms", result.testDuration));
                builder.add(format("    distance error %.2f m", result.distanceError));
                builder.add(format("    direction error %d DEG", result.directionError));
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

            double minDistance = Double.MAX_VALUE;
            int minDistanceDirection = 0;
            double maxDistance = 0;
            int maxDistanceDirection = 0;
            boolean validSamples = false;
            for (int i = 1; i < scannerResults.size(); i++) {
                ScannerResult r = scannerResults.get(i);
                if (r.valid && r.averageDistance > 0) {
                    validSamples = true;
                    if (r.averageDistance < minDistance) {
                        minDistance = r.averageDistance;
                        minDistanceDirection = r.direction;
                    }
                    if (r.averageDistance > maxDistance) {
                        maxDistance = r.averageDistance;
                        maxDistanceDirection = r.direction;
                    }
                }
            }

            long numScannerFailures = scannerResults.stream().filter(r -> !r.valid).count();
            if (numScannerFailures > 0) {
                builder.add(format("Scanner checks failed: %d failures", numScannerFailures));
            } else {
                builder.add("Scanner checks passed");
            }
            builder.add(format("    move time <= %d ms", maxMoveDuration));
            if (validSamples) {
                builder.add(format("    min distance = %.1f at %d DEG",
                        minDistance, minDistanceDirection));
                builder.add(format("    max distance = %.1f at %d DEG",
                        maxDistance, maxDistanceDirection));
            }

            long numRotationFailure = rotateResults.stream().filter(result -> result.directionError > ROTATION_TOLERANCE || result.imuFailure != 0).count();
            int maxDirectionError = 0;
            double maxLocationError = 0;
            double maxRotationSpeed = 0;
            for (RotateResult r : rotateResults) {
                if (r.imuFailure == 0) {
                    if (r.directionError > maxDirectionError) {
                        maxDirectionError = r.directionError;
                    }
                    if (r.locationError > maxLocationError) {
                        maxLocationError = r.locationError;
                    }
                    if (r.testDuration > 0) {
                        double speed = (double) abs(r.rotationAngle) / r.testDuration * 1000;
                        if (speed > maxRotationSpeed) {
                            maxRotationSpeed = speed;
                        }
                    }
                }
            }

            long numMovementFailure = moveResults.stream().filter(result -> result.directionError > ROTATION_TOLERANCE
                    || result.distanceError > DISTANCE_TOLERANCE
                    || result.imuFailure != 0).count();
            int maxMoveDirectionError = 0;
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
                    if (r.directionError > maxMoveDirectionError) {
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

            builder.add(format("    direction error <= %d DEG", maxDirectionError));
            builder.add(format("    location error <= %.2f m", maxLocationError));
            builder.add(format("    rotation speed <= %.2f DEG/s", maxRotationSpeed));

            if (numMovementFailure > 0) {
                builder.add(format("Movement checks failed: %d failures", numMovementFailure));
            } else {
                builder.add("Movement checks passed");
            }

            builder.add(format("    distance <= %.2f m", maxMoveDistance));
            builder.add(format("    speed <= %.2f m/s", maxSpeed));
            builder.add(format("    distance error <= %.2f m", maxMoveDistanceError));
            builder.add(format("    direction error <=  %d DEG", maxMoveDirectionError));

            builder.add(format("Supply voltage %.1f V", averageSupply));
            return builder.build();
        }
    }

    static class MovementResult {
        public final int direction;
        public final int directionError;
        public final double distance;
        public final double distanceError;
        public final int imuFailure;
        public final double speed;
        public final long testDuration;

        MovementResult(int direction, double speed, long testDuration, double distance, double distanceError, int directionError, int imuFailure) {
            this.direction = direction;
            this.directionError = directionError;
            this.distance = distance;
            this.distanceError = distanceError;
            this.imuFailure = imuFailure;
            this.speed = speed;
            this.testDuration = testDuration;
        }
    }

    /**
     * Rotation result
     */
    static class RotateResult {
        public final int directionError;
        public final int imuFailure;
        public final double locationError;
        public final int rotationAngle;
        public final int targetDir;
        public final long testDuration;


        RotateResult(long testDuration, int targetDir, int directionError, double distanceError, int rotationAngle, int imuFailure) {
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
    static class ScannerResult {
        public final double averageDistance;
        public final int direction;
        public final int imuFailure;
        public final long moveTime;
        public final long testDuration;
        public final boolean valid;

        ScannerResult(int direction, long testDuration, boolean valid, long moveTime, double averageDistance, int imuFailure) {
            this.direction = direction;
            this.valid = valid;
            this.averageDistance = averageDistance;
            this.moveTime = moveTime;
            this.testDuration = testDuration;
            this.imuFailure = imuFailure;
        }
    }
}
