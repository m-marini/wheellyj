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
import org.mmarini.Utils;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.model.FuzzyFunctions.*;
import static org.mmarini.wheelly.model.Utils.direction;
import static org.mmarini.wheelly.model.Utils.normalizeAngle;

public abstract class AbstractScannerMap implements ScannerMap {
    public static final double MAX_DISTANCE = 3;
    public static final double THRESHOLD_DISTANCE = 0.2;
    public static final double FUZZY_THRESHOLD_DISTANCE = 0.01;
    public static final double MAX_SENSITIVITY_ANGLE = toRadians(30);
    public static final double NO_SENSITIVITY_ANGLE = toRadians(45);
    public static final double THRESHOLD_LIKELIHOOD = 10e-3;
    public static final long HOLD_DURATION = 60000;
    public static final double LIKELIHOOD_TAU = HOLD_DURATION / 5000.;

    /**
     * Returns the obstacle sample properties
     *
     * @param obstacle        the obstacle
     * @param sampleLocation  the sample location
     * @param robotLocation   the robot location
     * @param sensorDirection the robot direction
     */
    static ObstacleSampleProperties createSampleProperties(Obstacle obstacle,
                                                           Point2D sampleLocation,
                                                           Point2D robotLocation,
                                                           double sensorDirection) {
        double robotObstacleDistance = obstacle.getLocation().distance(robotLocation);
        double sampleObstacleDistance = sampleLocation != null ? sampleLocation.distance(obstacle.getLocation()) : Double.MAX_VALUE;
        double obstacleSensorDirection = normalizeAngle(direction(robotLocation, obstacle.getLocation()) - sensorDirection);
        return new ObstacleSampleProperties(obstacle, robotObstacleDistance, sampleObstacleDistance, obstacleSensorDirection);
    }

    public final List<Obstacle> obstacles;

    /**
     * Creates a scanner map
     *
     * @param obstacles the list of obstacles
     */
    protected AbstractScannerMap(List<Obstacle> obstacles) {
        this.obstacles = requireNonNull(obstacles);
    }

    /**
     * @param location the location
     */
    protected abstract Point2D arrangeLocation(Point2D location);

    /**
     * @param sample the sample
     */
    Stream<Obstacle> createObstacles(Timed<ProxySample> sample) {
        requireNonNull(sample);
        Stream<ObstacleSampleProperties> obsProps = obstacleSampleProperties(sample);
        double sampleDistance = sample.value().distance;
        // Split the eligible obstacles
        Map<Boolean, List<ObstacleSampleProperties>> split = obsProps.collect(Collectors.groupingBy(op ->
                abs(op.obstacleSensorRad) <= NO_SENSITIVITY_ANGLE
                        && (sampleDistance > 0
                        ? op.robotObstacleDistance < sampleDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE
                        : op.robotObstacleDistance < MAX_DISTANCE)
        ));
        List<ObstacleSampleProperties> eligibles = Utils.getValue(split, true).orElseGet(List::of);
        List<ObstacleSampleProperties> notEligibles = Utils.getValue(split, false).orElseGet(List::of);

        Optional<Point2D> arrangedLocation = sample.value().getLocation().map(this::arrangeLocation);
        boolean hasTarget = arrangedLocation.stream().anyMatch(location ->
                eligibles.stream()
                        .map(ObstacleSampleProperties::getObstacle)
                        .map(Obstacle::getLocation)
                        .anyMatch(location::equals));

        long sampleTimestamp = sample.time(TimeUnit.MILLISECONDS);

        List<Obstacle> newObstacles = sample.value().getLocation().map(sampleLocation -> {
            // sample present: reinforce the obstacles near the sample location and weakening the obstacles before the sample location
            List<Obstacle> obs = eligibles.stream()
                    .map(reinforcedObstacle(sampleTimestamp, sampleDistance))
                    .collect(Collectors.toList());
            if (!hasTarget) {
                // Add a new obstacle if target is not present
                Point2D location = arrangeLocation(sampleLocation);
                obs.add(Obstacle.create(location, sampleTimestamp, 1));
            }
            return obs;
        }).orElseGet(() -> {
            // Empty sample: weakening the obstacle in sensor trajectory
            return eligibles.stream()
                    .map(weakSample(sampleTimestamp))
                    .collect(Collectors.toList());
        });

        // Filter out the older obstacle and poor likelihood
        return Stream.concat(
                notEligibles.stream().map(ObstacleSampleProperties::getObstacle),
                newObstacles.stream());
    }

    @Override
    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    /**
     * @param obstacles the obstacle list
     */
    protected abstract AbstractScannerMap newInstance(List<Obstacle> obstacles);

    /**
     * Returns the stream of obstacle and sample properties for a given sample
     *
     * @param sample the sample
     */
    Stream<ObstacleSampleProperties> obstacleSampleProperties(Timed<ProxySample> sample) {
        ProxySample value = sample.value();
        return obstacles.stream()
                .map(o -> ObstacleSampleProperties.from(o, value));
    }

    @Override
    public GridScannerMap process(Timed<ProxySample> sample) {
        requireNonNull(sample);
        // Filter out the older obstacle and poor likelihood
        long holdTimestamp = sample.time(TimeUnit.MILLISECONDS) - HOLD_DURATION;
        List<Obstacle> resultingList = createObstacles(sample)
                .filter(o -> o.timestamp >= holdTimestamp)
                .filter(o -> o.getLikelihood() >= THRESHOLD_LIKELIHOOD)
                .collect(Collectors.toList());
        return (GridScannerMap) newInstance(resultingList);
    }

    /**
     * Returns the creator of a reinforced obstacle
     *
     * @param sampleTimestamp the sample timestamp
     * @param sampleDistance  the sample distance
     */
    private Function<ObstacleSampleProperties, Obstacle> reinforcedObstacle(long sampleTimestamp, double sampleDistance) {
        return o -> {
            double robotObstacleDistance = o.robotObstacleDistance;
            double isBeforeSample = negative(robotObstacleDistance - (sampleDistance - THRESHOLD_DISTANCE), FUZZY_THRESHOLD_DISTANCE);
            double isAfterSample = positive(robotObstacleDistance - (sampleDistance + THRESHOLD_DISTANCE), FUZZY_THRESHOLD_DISTANCE);
            double isNearSample = not(or(isBeforeSample, isAfterSample));
            double sampleRelDirection = o.obstacleSensorRad;
            double isOnDirection = between(sampleRelDirection, -NO_SENSITIVITY_ANGLE, -MAX_SENSITIVITY_ANGLE, MAX_SENSITIVITY_ANGLE, NO_SENSITIVITY_ANGLE);

            double reinforce = and(isNearSample, isOnDirection);
            double weakening = and(isBeforeSample, isOnDirection);
            double hold = not(or(reinforce, weakening));

            double t = (sampleTimestamp - o.obstacle.timestamp) * 1e-3;
            double currentLikelihood = o.obstacle.likelihood * exp(-t / LIKELIHOOD_TAU);

            double likelihood = defuzzy(
                    1, reinforce,
                    currentLikelihood, hold,
                    0, weakening);
            return Obstacle.create(o.obstacle.location, sampleTimestamp, likelihood);
        };
    }

    /**
     * Returns the creator of a weak sample
     *
     * @param sampleTimestamp the sample timestamp
     */
    private Function<ObstacleSampleProperties, Obstacle> weakSample(long sampleTimestamp) {
        return o -> {
            double isOnDirection = between(abs(o.obstacleSensorRad), -NO_SENSITIVITY_ANGLE, -MAX_SENSITIVITY_ANGLE, MAX_SENSITIVITY_ANGLE, NO_SENSITIVITY_ANGLE);
            double isInRange = negative(o.robotObstacleDistance - MAX_DISTANCE, FUZZY_THRESHOLD_DISTANCE);
            double weakening = and(isOnDirection, isInRange);
            double t = (sampleTimestamp - o.obstacle.timestamp) * 1e-3;
            double currentLikelihood = o.obstacle.likelihood * exp(-t / LIKELIHOOD_TAU);
            double likelihood = defuzzy(currentLikelihood, not(weakening),
                    0, weakening);
            return o.obstacle.setLikelihood(likelihood).setTimestamp(sampleTimestamp);
        };
    }
}
