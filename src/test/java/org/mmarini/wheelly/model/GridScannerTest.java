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

package org.mmarini.wheelly.model;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.ArgumentsGenerator;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleBinaryOperator;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.model.AbstractScannerMap.*;
import static org.mmarini.wheelly.model.GridScannerMap.snapToGrid;
import static org.mmarini.wheelly.model.Utils.*;

/**
 * See tests enumeration figure
 */
public interface GridScannerTest {

    int MAX_DT = 1000;
    double MIN_OBSTACLE_DISTANCE = 0.2;
    double MIN_ECHO_DISTANCE = MIN_OBSTACLE_DISTANCE + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE + 0.1;
    double MAX_DISTANCE = 3d;
    double D_EPSILON = 1e-3;
    int MAX_DEG_DIRECTION = 359;
    double LOCATION_RANGE = 3;
    int MAX_SENSOR_DEG = 90;
    int MAX_SENSITIVITY_DEG = (int) round(toDegrees(MAX_SENSITIVITY_ANGLE));
    int NO_SENSITIVITY_DEG = (int) round(toDegrees(NO_SENSITIVITY_ANGLE));
    int LATERAL_DEG = NO_SENSITIVITY_DEG - MAX_SENSITIVITY_DEG;

    static ArgumentsGenerator<Integer> centralDegAngleGen() {
        return uniform(-MAX_SENSITIVITY_DEG + 1, MAX_SENSITIVITY_DEG - 1);
    }

    static double distance1(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                MIN_OBSTACLE_DISTANCE,
                echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE - D_EPSILON);
    }

    static double distance10(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1, THRESHOLD_DISTANCE, MAX_DISTANCE);
    }

    static double distance2(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance - THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE + D_EPSILON,
                echoDistance - THRESHOLD_DISTANCE - D_EPSILON);
    }

    static double distance3(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance - THRESHOLD_DISTANCE,
                echoDistance + THRESHOLD_DISTANCE);
    }

    static double distance4(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance + THRESHOLD_DISTANCE + D_EPSILON,
                echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE - D_EPSILON);
    }

    static double distance9(double relDistance, double echoDistance) {
        return linear(relDistance, 0, 1,
                echoDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE,
                echoDistance + MAX_DISTANCE);
    }

    static Stream<TestSet> echoTestSet(
            DoubleBinaryOperator obstacleRelDistanceFunc,
            ArgumentsGenerator<Integer> obstacleRelDeGen
    ) {
        return createStream(1234,
                uniform(-LOCATION_RANGE, LOCATION_RANGE),   // robotX
                uniform(-LOCATION_RANGE, LOCATION_RANGE),   // robotY
                uniform(0, MAX_DEG_DIRECTION),              // robotDeg
                uniform(-MAX_SENSOR_DEG, MAX_SENSOR_DEG),   // relSensorDeg
                exponential(MIN_ECHO_DISTANCE, MAX_DISTANCE - D_EPSILON),  // echoDistance

                uniform(0.0, 1.0),  // obstacleRelDistance
                obstacleRelDeGen, // obstacleRelDeg

                uniform(0, MAX_DT),     // dt
                exponential(0.5, 1d)   // likelihood
        ).map(args -> {
            long sampleTimestamp = System.currentTimeMillis();

            Object[] ary = args.get();
            double robotX = ((Number) ary[0]).doubleValue();
            double robotY = ((Number) ary[1]).doubleValue();
            int robotDegDirection = ((Number) ary[2]).intValue();
            RobotAsset asset = RobotAsset.create(robotX, robotY, robotDegDirection);

            int sensorDegDirection = ((Number) ary[3]).intValue();
            double echoDistance = ((Number) ary[4]).doubleValue();
            ProxySample proxySample = ProxySample.create(sensorDegDirection, echoDistance, asset);
            Timed<ProxySample> timedSample = new Timed<>(proxySample, sampleTimestamp, TimeUnit.MILLISECONDS);

            double obstacleDistance = obstacleRelDistanceFunc.applyAsDouble(((Number) ary[5]).doubleValue(), echoDistance);
            int obstacleRelDeg = ((Number) ary[6]).intValue();
            long dt = ((Number) ary[7]).longValue();
            double likelihood = ((Number) ary[8]).doubleValue();

            long timestamp = timedSample.time(TimeUnit.MILLISECONDS);
            double obstacleRadDirection = toNormalRadians(asset.directionDeg + proxySample.sensorRelativeDeg + obstacleRelDeg);

            Point2D obstacleLocation = snapToGrid(new Point2D.Double(
                            asset.location.getX() + obstacleDistance * cos(obstacleRadDirection),
                            asset.location.getY() + obstacleDistance * sin(obstacleRadDirection)),
                    THRESHOLD_DISTANCE);
            Obstacle obstacle = Obstacle.create(obstacleLocation, timestamp - dt, (float) likelihood);
            return new TestSet(timedSample, obstacle);
        });
    }

    static ArgumentsGenerator<Integer> extrnalDegAngleGen() {
        return uniform(-NO_SENSITIVITY_DEG, NO_SENSITIVITY_DEG + 1)
                .map(x -> (int) normalizeDegAngle(180 - x));
    }

    static ArgumentsGenerator<Integer> innerDegAngle() {
        return uniform(-NO_SENSITIVITY_DEG + 1, NO_SENSITIVITY_DEG - 1);
    }

    static ArgumentsGenerator<Integer> lateralDegAngleGen() {
        return uniform(-LATERAL_DEG + 1, LATERAL_DEG - 2) // (-14, ..., -1, 0, ..., 13
                .map(i -> i < 0
                        ? i - MAX_SENSITIVITY_DEG
                        : i + MAX_SENSITIVITY_DEG + 1);
        // (-44, ..., -31, 31, ..., 44)
    }

    static Stream<TestSet> noEchoTestSet(
            ArgumentsGenerator<Integer> obstacleRelDeGen
    ) {
        return createStream(1234,
                uniform(-LOCATION_RANGE, LOCATION_RANGE),   // robotX
                uniform(-LOCATION_RANGE, LOCATION_RANGE),   // robotY
                uniform(0, MAX_DEG_DIRECTION),              // robotDeg
                uniform(-MAX_SENSOR_DEG, MAX_SENSOR_DEG),   // relSensorDeg

                exponential(MIN_OBSTACLE_DISTANCE, MAX_DISTANCE - FUZZY_THRESHOLD_DISTANCE - D_EPSILON),  // obstacleDistance
                obstacleRelDeGen, // obstacleRelDeg

                uniform(0, MAX_DT),     // dt
                exponential(0.5, 1d)   // likelihood
        ).map(args -> {
            long sampleTimestamp = System.currentTimeMillis();

            Object[] ary = args.get();
            double robotX = ((Number) ary[0]).doubleValue();
            double robotY = ((Number) ary[1]).doubleValue();
            int robotDegDirection = ((Number) ary[2]).intValue();
            RobotAsset asset = RobotAsset.create(robotX, robotY, robotDegDirection);

            int sensorDegDirection = ((Number) ary[3]).intValue();
            ProxySample proxySample = ProxySample.create(sensorDegDirection, 0, asset);
            Timed<ProxySample> timedSample = new Timed<>(proxySample, sampleTimestamp, TimeUnit.MILLISECONDS);

            double obstacleDistance = ((Number) ary[4]).doubleValue();
            int obstacleRelDeg = ((Number) ary[5]).intValue();
            long dt = ((Number) ary[6]).longValue();
            double likelihood = ((Number) ary[7]).doubleValue();

            long timestamp = timedSample.time(TimeUnit.MILLISECONDS);
            double obstacleRadDirection = toNormalRadians(asset.directionDeg + proxySample.sensorRelativeDeg + obstacleRelDeg);

            Point2D obstacleLocation = snapToGrid(new Point2D.Double(
                            asset.location.getX() + obstacleDistance * cos(obstacleRadDirection),
                            asset.location.getY() + obstacleDistance * sin(obstacleRadDirection)),
                    THRESHOLD_DISTANCE);
            Obstacle obstacle = Obstacle.create(obstacleLocation, timestamp - dt, (float) likelihood);
            return new TestSet(timedSample, obstacle);
        });
    }

    class TestSet {
        public final Obstacle obstacle;
        public final Timed<ProxySample> proxySample;
        private final double obstacleSensorDistance;
        private final double obstacleSensorRad;
        private final double obstacleSensorDeg;
        private final double obstacleRad;
        private final double obstacleDeg;

        TestSet(Timed<ProxySample> proxySample, Obstacle obstacle) {
            this.proxySample = proxySample;
            this.obstacle = obstacle;
            ProxySample sample = proxySample.value();
            RobotAsset robot = sample.robotAsset;
            this.obstacleSensorDistance = obstacle.location.distance(robot.location);
            this.obstacleRad = direction(robot.location, obstacle.location);
            this.obstacleDeg = toDegrees(obstacleRad);
            this.obstacleSensorDeg = normalizeDegAngle(obstacleDeg - sample.getSampleDeg());
            this.obstacleSensorRad = toRadians(obstacleSensorDeg);
        }

        public Obstacle getObstacle() {
            return obstacle;
        }

        public double getObstacleSensorDeg() {
            return obstacleSensorDeg;
        }

        public double getObstacleSensorDistance() {
            return obstacleSensorDistance;
        }

        public double getObstacleSensorRad() {
            return obstacleSensorRad;
        }

        public Timed<ProxySample> getProxySample() {
            return proxySample;
        }

        public boolean isCentralArea() {
            return abs(obstacleSensorDeg) < MAX_SENSITIVITY_DEG;
        }

        public boolean isDistance1() {
            return obstacleSensorDistance - proxySample.value().distance < -THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE;
        }

        public boolean isDistance2() {
            double dist = obstacleSensorDistance - proxySample.value().distance;
            return dist > -THRESHOLD_DISTANCE - FUZZY_THRESHOLD_DISTANCE && dist < -THRESHOLD_DISTANCE;
        }

        public boolean isDistance3() {
            return abs(obstacleSensorDistance - proxySample.value().distance) < THRESHOLD_DISTANCE;
        }

        public boolean isDistance4() {
            double dist = obstacleSensorDistance - proxySample.value().distance;
            return dist < THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE && dist > THRESHOLD_DISTANCE;
        }

        public boolean isDistance9() {
            double dist = obstacleSensorDistance - proxySample.value().distance;
            return dist > THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE + D_EPSILON;
        }

        public boolean isExternalArea() {
            return abs(obstacleSensorDeg) > NO_SENSITIVITY_DEG;
        }

        public boolean isInnerArea() {
            return abs(obstacleSensorDeg) < NO_SENSITIVITY_DEG;
        }

        public boolean isLateralArea() {
            double deg = abs(obstacleSensorDeg);
            return deg > MAX_SENSITIVITY_DEG && deg < NO_SENSITIVITY_DEG;
        }
    }
}