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

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.wheelly.apis.Robot;
import org.mmarini.wheelly.apis.Utils;
import org.mmarini.wheelly.apis.WheellyStatus;
import org.mmarini.wheelly.swing.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;


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
    public static final float DISTANCE_TOLERANCE = 0.1F;
    private static final Logger logger = LoggerFactory.getLogger(RobotCheckUp.class);

    /**
     * Returns the command line arguments parser
     */
    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(RobotCheckUp.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-r", "--robotHost")
                .required(true)
                .help("specify robot host");
        parser.addArgument("-p", "--port")
                .type(Integer.class)
                .setDefault(22)
                .help("specify robot port");
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
    private Namespace parseArgs;
    private Robot robot;

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
    }

    private MovementResult checkMove(int direction, float speed) {
        logger.info("Checking movement to {} DEG, speed {} ...", direction, speed);
        WheellyStatus status = robot.getStatus();
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
            time = status.getTime();
        }
        robot.halt();
        robot.tick(interval);
        status = robot.getStatus();
        time = status.getTime();
        Point2D pos = status.getLocation();
        double distance = pos.distance(startLocation);
        Point2D targetLocation = new Point2D.Float(
                (float) (startLocation.getX() + distance * sin(toRadians(direction))),
                (float) (startLocation.getY() + distance * cos(toRadians(direction))));
        double distanceError = targetLocation.distance(pos);

        int realDirection = (int) round(toDegrees(Utils.direction(startLocation, pos)));
        int directionError = normalizeDegAngle(realDirection - direction);
        return new MovementResult(direction,
                speed,
                time - movementStart,
                (float) distance,
                (float) distanceError,
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
        WheellyStatus status = robot.getStatus();
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
            time = status.getTime();
            if (status.getLeftSpeed() == 0 && status.getRightSpeed() == 0) {
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
        time = status.getTime();
        int dir = status.getDirection();
        int directionError = abs(normalizeDegAngle(dir - targetDir));
        int rotationAngle = normalizeDegAngle(dir - startAngle);
        float distanceError = (float) status.getLocation().distance(startLocation);
        robot.halt();
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
        WheellyStatus status = robot.getStatus();
        long time = status.getTime();
        int sensDir = -90;
        long commandTimeout = time + commandInterval;
        long measureTimeout = time + scannerCheckDuration;
        logger.info("Checking sensor to {} DEG ...", sensDir);
        robot.scan(sensDir);
        long measureStart = time;
        long scannerMoveTime = 0;
        int measureCount = 0;
        int sampleCount = 0;
        float totDistance = 0;
        List<ScannerResult> results = new ArrayList<>();
        for (; ; ) {
            robot.tick(this.interval);
            status = robot.getStatus();
            time = status.getTime();

            int dir = status.getSensorDirection();
            if (dir == sensDir) {
                sampleCount++;
                if (scannerMoveTime == 0) {
                    scannerMoveTime = time - measureStart;
                }
                float sensorDistance = (float) status.getSampleDistance();
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
        return results;
    }

    /**
     * Runs the check supply and returns the average supply voltage
     */
    private double checkSupply() {
        logger.info("Checking supply ...");
        double sum = 0;
        for (int i = 0; i <= supplySamples; i++) {
            robot.tick(interval);
            sum += robot.getStatus().getVoltage();
        }
        return sum / supplySamples;
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

    /**
     * Prints the details of check
     *
     * @param scannerResults the scanner result
     * @param rotateResults  the rotation result
     * @param moveResults    the movement result
     */
    private void printDetails(List<RotateResult> rotateResults, List<MovementResult> moveResults, List<ScannerResult> scannerResults) {
        for (ScannerResult scannerResult : scannerResults) {
            logger.info(format("Sensor direction check %d DEG", scannerResult.direction));
            logger.info(format("    %s", scannerResult.valid ? "valid" : "invalid"));
            logger.info(format("    duration %d ms", scannerResult.testDuration));
            logger.info(format("    move time %d ms", scannerResult.moveTime));
            logger.info(format("    average distance %.2f m", scannerResult.averageDistance));
        }

        for (RotateResult result : rotateResults) {
            logger.info(format("Rotate direction %d DEG", result.targetDir));
            logger.info(format("    rotation %d DEG", result.rotationAngle));
            logger.info(format("    duration %d ms", result.testDuration));
            logger.info(format("    direction error %d DEG", result.directionError));
            logger.info(format("    location error %.2f m", result.locationError));
        }
        for (MovementResult result : moveResults) {
            logger.info(format("Movement direction %d DEG, speed %.1f", result.direction, result.speed));
            logger.info(format("    distance %.2f m", result.distance));
            logger.info(format("    duration %d ms", result.testDuration));
            logger.info(format("    distance error %.2f m", result.distanceError));
            logger.info(format("    direction error %d DEG", result.directionError));
        }
    }

    /**
     * Prints summary of results
     *
     * @param scannerResults the scanner result
     * @param rotateResults  the rotation result
     * @param moveResults    the movement result
     * @param averageSupply  the supply voltage result
     */
    private void printSummary(List<ScannerResult> scannerResults, List<RotateResult> rotateResults, List<MovementResult> moveResults, double averageSupply) {
        long imuFailure = scannerResults.stream().filter(r -> r.imuFailure != 0).count() +
                rotateResults.stream().filter(r -> r.imuFailure != 0).count() +
                moveResults.stream().filter(r -> r.imuFailure != 0).count();

        logger.info("");
        logger.info("Check summary");
        logger.info("");
        if (imuFailure > 0) {
            logger.error(format("%d imu failure", imuFailure));
        } else {
            logger.info("No imu failure");
        }

        long maxMoveDuration = scannerResults.stream()
                .mapToLong(r -> r.moveTime)
                .max()
                .orElse(0);

        float minDistance = Float.MAX_VALUE;
        int minDistanceDirection = 0;
        float maxDistance = 0;
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
            logger.error(format("Scanner checks failed: %d failures", numScannerFailures));
        } else {
            logger.info("Scanner checks passed");
        }
        logger.info(format("    move time <= %d ms", maxMoveDuration));
        if (validSamples) {
            logger.info(format("    min distance = %.1f at %d DEG",
                    minDistance, minDistanceDirection));
            logger.info(format("    max distance = %.1f at %d DEG",
                    maxDistance, maxDistanceDirection));
        }

        long numRotationFailure = rotateResults.stream().filter(result -> result.directionError > ROTATION_TOLERANCE || result.imuFailure != 0).count();
        int maxDirectionError = 0;
        float maxLocationError = 0;
        float maxRotationSpeed = 0;
        for (RotateResult r : rotateResults) {
            if (r.imuFailure == 0) {
                if (r.directionError > maxDirectionError) {
                    maxDirectionError = r.directionError;
                }
                if (r.locationError > maxLocationError) {
                    maxLocationError = r.locationError;
                }
                if (r.testDuration > 0) {
                    float speed = (float) abs(r.rotationAngle) / r.testDuration * 1000;
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
        float maxMoveDistanceError = 0;
        float maxSpeed = 0;
        float maxMoveDistance = 0;
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
                float speed = r.distance / r.testDuration * 1000;
                if (speed > maxSpeed) {
                    maxSpeed = speed;
                }
            }
        }

        if (numRotationFailure > 0) {
            logger.error(format("Rotation checks failed: %d failures", numRotationFailure));
        } else {
            logger.info("Rotation checks passed");
        }

        logger.info(format("    direction error <= %d DEG", maxDirectionError));
        logger.info(format("    location error <= %.2f m", maxLocationError));
        logger.info(format("    rotation speed <= %.2f DEG/s", maxRotationSpeed));

        if (numMovementFailure > 0) {
            logger.error(format("Movement checks failed: %d failures", numMovementFailure));
        } else {
            logger.info("Movement checks passed");
        }

        logger.info(format("    distance <= %.2f m", maxMoveDistance));
        logger.info(format("    speed <= %.2f m/s", maxSpeed));
        logger.info(format("    distance error <= %.2f m", maxMoveDistanceError));
        logger.info(format("    direction error <=  %d DEG", maxMoveDirectionError));

        logger.info(format("Supply voltage %.1f V", averageSupply));
    }

    /**
     * Runs the check
     *
     * @throws IOException in case of error
     */
    private void run() throws IOException {
        logger.info("Robot check started.");
        try (Robot robot = Robot.create(parseArgs.getString("robotHost"),
                parseArgs.getInt("port"),
                null, 10000, 1000, 0)) {
            this.robot = robot;
            robot.start();

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
            moveResults.add(checkMove(0, 1));
            moveResults.add(checkMove(0, -1));

            rotateResults.add(checkRotate(90));
            moveResults.add(checkMove(90, 1));
            moveResults.add(checkMove(90, -1));

            rotateResults.add(checkRotate(180));
            moveResults.add(checkMove(180, 1));
            moveResults.add(checkMove(180, -1));

            rotateResults.add(checkRotate(-90));
            moveResults.add(checkMove(-90, 1));
            moveResults.add(checkMove(-90, -1));

            rotateResults.add(checkRotate(0));

            double averageSupply = checkSupply();

            // Prints the results
            if (parseArgs.getBoolean("verbose")) {
                printDetails(rotateResults, moveResults, scannerResults);
            }

            printSummary(scannerResults, rotateResults, moveResults, averageSupply);
        }
        logger.info("Robot check completed.");
    }

    static class MovementResult {
        public final int direction;
        public final int directionError;
        public final float distance;
        public final float distanceError;
        public final int imuFailure;
        public final float speed;
        public final long testDuration;

        MovementResult(int direction, float speed, long testDuration, float distance, float distanceError, int directionError, int imuFailure) {
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
        public final float locationError;
        public final int rotationAngle;
        public final int targetDir;
        public final long testDuration;


        RotateResult(long testDuration, int targetDir, int directionError, float distanceError, int rotationAngle, int imuFailure) {
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
        public final float averageDistance;
        public final int direction;
        public final int imuFailure;
        public final long moveTime;
        public final long testDuration;
        public final boolean valid;

        ScannerResult(int direction, long testDuration, boolean valid, long moveTime, float averageDistance, int imuFailure) {
            this.direction = direction;
            this.valid = valid;
            this.averageDistance = averageDistance;
            this.moveTime = moveTime;
            this.testDuration = testDuration;
            this.imuFailure = imuFailure;
        }
    }
}
