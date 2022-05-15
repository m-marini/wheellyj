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
import org.mmarini.LazyValue;
import org.mmarini.Utils;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;
import static org.mmarini.wheelly.model.FuzzyFunctions.*;

public class GridScannerMap implements ScannerMap {
    public static final double MAX_DISTANCE = 3;
    public static final double THRESHOLD_DISTANCE = 0.2;
    public static final double FUZZY_THRESHOLD_DISTANCE = 0.01;
    public static final double MAX_SENSITIVITY_ANGLE = toRadians(30d / 2);
    public static final double NO_SENSITIVITY_ANGLE = toRadians(60d / 2);
    public static final double THRESHOLD_LIKELIHOOD = 10e-3;
    public static final long HOLD_DURATION = 60000;
    public static final double LIKELIHOOD_TAU = HOLD_DURATION / 2000.;

    public static Point cell(Point2D location, double gridSize) {
        double x = location.getX();
        double y = location.getY();
        long i = round(x / gridSize);
        long j = round(y / gridSize);
        return new Point((int) i, (int) j);
    }

    /**
     * Returns an empty map
     */
    public static GridScannerMap create(List<Obstacle> obstacles, double gridSize, double safeDistance, double likelihoodThreshold) {
        return new GridScannerMap(obstacles, gridSize, safeDistance, likelihoodThreshold);
    }

    public static Point2D snapToGrid(Point2D location, double gridSize) {
        Point cell = cell(location, gridSize);
        return new Point2D.Double(cell.x * gridSize, cell.y * gridSize);
    }

    public final double gridSize;
    public final List<Obstacle> obstacles;
    private final double safeDistance;
    private final double likelihoodThreshold;
    private final LazyValue<Set<Point>> prohibited;
    private final LazyValue<Set<Point>> contours;

    /**
     * Creates a scanner map
     *
     * @param obstacles           the list of obstacles
     * @param safeDistance        the safe distance m
     * @param likelihoodThreshold the likelihood threshold
     */
    protected GridScannerMap(List<Obstacle> obstacles, double gridSize, double safeDistance, double likelihoodThreshold) {
        this.obstacles = requireNonNull(obstacles);
        this.gridSize = gridSize;
        this.safeDistance = safeDistance;
        this.likelihoodThreshold = likelihoodThreshold;
        this.prohibited = new LazyValue<>(() ->
                ProhibitedCellFinder.create(this, this.safeDistance, this.likelihoodThreshold).find()
        );
        this.contours = new LazyValue<>(() ->
                ProhibitedCellFinder.findContour(getProhibited())
        );
    }

    protected Point2D arrangeLocation(Point2D location) {
        return snapToGrid(location, gridSize);
    }

    public Point cell(Point2D location) {
        return cell(location, gridSize);
    }

    /**
     * @param sample the sample
     */
    Stream<Obstacle> createObstacles(Timed<? extends ProxySample> sample) {
        requireNonNull(sample);
        Stream<ObstacleSampleProperties> obsProps = obstacleSampleProperties(sample);
        double sampleDistance = sample.value().getSampleDistance();
        // Split the eligible obstacles
        Map<Boolean, List<ObstacleSampleProperties>> split = obsProps.collect(Collectors.groupingBy(op ->
                abs(op.obstacleSensorRad) <= NO_SENSITIVITY_ANGLE
                        && (sampleDistance > 0
                        ? op.robotObstacleDistance < sampleDistance + THRESHOLD_DISTANCE + FUZZY_THRESHOLD_DISTANCE
                        : op.robotObstacleDistance < MAX_DISTANCE)
        ));
        List<ObstacleSampleProperties> eligibles = Utils.getValue(split, true).orElseGet(List::of);
        List<ObstacleSampleProperties> notEligibles = Utils.getValue(split, false).orElseGet(List::of);

        Optional<Point2D> arrangedLocation = sample.value().getSampleLocation().map(this::arrangeLocation);
        boolean hasTarget = arrangedLocation.stream().anyMatch(location ->
                eligibles.stream()
                        .map(ObstacleSampleProperties::getObstacle)

                        .map(Obstacle::getLocation)
                        .anyMatch(location::equals));

        long sampleTimestamp = sample.time(TimeUnit.MILLISECONDS);

        List<Obstacle> newObstacles = sample.value().getSampleLocation().map(sampleLocation -> {
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
        return concat(
                notEligibles.stream().map(ObstacleSampleProperties::getObstacle),
                newObstacles.stream());
    }

    public Stream<Point> getCells() {
        return obstacles.stream()
                .map(Obstacle::getLocation)
                .map(this::cell);
    }

    public Set<Point> getContours() {
        return contours.get();
    }

    @Override
    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public Set<Point> getProhibited() {
        return prohibited.get();
    }

    public boolean isObstacleAt(Point2D target) {
        Point cell = cell(target);
        return obstacles.stream()
                .map(Obstacle::getLocation)
                .map(this::cell)
                .anyMatch(cell::equals);
    }

    public boolean isProhibited(Point2D robotLocation) {
        return getProhibited().contains(cell(robotLocation));
    }

    protected GridScannerMap newInstance(List<Obstacle> obstacles) {
        return new GridScannerMap(obstacles, gridSize, safeDistance, likelihoodThreshold);
    }

    /**
     * Returns the stream of obstacle and sample properties for a given sample
     *
     * @param sample the sample
     */
    Stream<ObstacleSampleProperties> obstacleSampleProperties(Timed<? extends ProxySample> sample) {
        ProxySample value = sample.value();
        return obstacles.stream()
                .map(o -> ObstacleSampleProperties.from(o, value));
    }

    @Override
    public GridScannerMap process(Timed<? extends WheellyStatus> sample) {
        requireNonNull(sample);
        // Filter out the older obstacle and poor likelihood
        long holdTimestamp = sample.time(TimeUnit.MILLISECONDS) - HOLD_DURATION;
        Stream<Obstacle> scannerObstacles =
                createObstacles(sample)
                        .filter(o -> o.timestamp >= holdTimestamp)
                        .filter(o -> o.getLikelihood() >= THRESHOLD_LIKELIHOOD);
        Stream<Obstacle> contacts = sample.value().getContactObstacles().stream()
                .map(this::cell)
                .map(this::toPoint)
                .map(pt -> Obstacle.create(pt, sample.time(TimeUnit.MILLISECONDS), 1));
        return newInstance(
                new ArrayList<>(
                        concat(scannerObstacles, contacts).
                                collect(Collectors.toSet())));
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

    public GridScannerMap setLikelihoodThreshold(double likelihoodThreshold) {
        return this.likelihoodThreshold != likelihoodThreshold ? new GridScannerMap(obstacles, gridSize, safeDistance, likelihoodThreshold) : this;
    }

    public GridScannerMap setSafeDistance(double safeDistance) {
        return this.safeDistance != safeDistance ? new GridScannerMap(obstacles, gridSize, safeDistance, likelihoodThreshold) : this;
    }

    public Point2D toPoint(Point cell) {
        return new Point2D.Double(cell.x * gridSize, cell.y * gridSize);
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
