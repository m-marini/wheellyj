/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.CircularSector;
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;

import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * Generates the behavior to explore environment.
 * <p>
 * Turns the sensor toward the unknown cells if in front<br>
 * Turns the robot toward the unknown cells.<br>
 * Moves ahead till obstacles.<br/>
 * Turns to largest and nearest the front interval if any.<br/>
 * Turns to the further obstacle if far away enough.<br/>
 * Move ahead in any other cases.<br/>
 * <p>
 * Parameters are:
 * <ul>
 *     <li><code>timeout</code> the timeout interval (ms)</li>
 *     <li><code>stopDistance</code> the stop distance (m)</li>
 *     <li><code>turnDirectionRange</code> the turn direction range (DEG)</li>
 * </ul>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>timeout</code> is generated at timeout</li>
 * </ul>
 * </p>
 */
public record ExploringPointState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                                  ProcessorCommand onExit) implements ExtendedStateNode {
    private static final Logger logger = LoggerFactory.getLogger(ExploringPointState.class);
    private static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of("notFound", RobotCommands.idle());

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static ExploringPointState create(JsonNode root, Locator locator, String id) {
        double distance = locator.path("distance").getNode(root).asDouble();
        double safeDistance = locator.path("safeDistance").getNode(root).asDouble();
        double maxDistance = locator.path("maxDistance").getNode(root).asDouble();
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + ".distance", distance,
                        id + ".safeDistance", safeDistance,
                        id + ".maxDistance", maxDistance
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new ExploringPointState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the exploring state
     *
     * @param id      the identifier
     * @param onInit  the initialize command
     * @param onEntry the entry command
     * @param onExit  the exit command
     */
    public ExploringPointState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContext context) {
        double distance = getDouble(context, "distance");
        double maxDistance = getDouble(context, "maxDistance");
        double safeDistance = getDouble(context, "safeDistance");
        PolarMap map = context.getPolarMap();
        Point2D robotLocation = context.getRobotStatus().getLocation();
        // Callects all obstacles from hindered circular cells
        List<Point2D> obstacles = map.sectorStream()
                .filter(CircularSector::knownHindered)
                .map(CircularSector::location)
                .toList();
        // Collects all target points
        int n = map.sectorsNumber();
        List<Point2D> targets = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            CircularSector sector = map.sector(i);
            if (sector.knownHindered()) {
                Point2D p = sector.location();
                double d1 = p.distance(robotLocation);
                double d = d1 - safeDistance;
                if (d >= distance) {
                    double k = d / d1;
                    double x0 = robotLocation.getX();
                    double y0 = robotLocation.getY();
                    double x1 = p.getX();
                    double y1 = p.getY();
                    double x = (x1 - x0) * k + x0;
                    double y = (y1 - y0) * k + y0;
                    targets.add(new Point2D.Double(x, y));
                }
            } else {
                double sectorDirRad = normalizeDegAngle(map.sectorDirection(i) + toRadians(map.direction()));
                double x0 = robotLocation.getX();
                double y0 = robotLocation.getY();
                double x = maxDistance * sin(sectorDirRad) + x0;
                double y = maxDistance * cos(sectorDirRad) + y0;
                targets.add(new Point2D.Double(x, y));
            }
        }

        Optional<Point2D> target = targets.stream()
                //collects points whose trajectory is not too close to obstacles
                .filter(p ->
                        obstacles.stream().allMatch(obs -> obs.distance(p) >= safeDistance)
                ).max(Comparator.comparingDouble(robotLocation::distanceSq));
        target.ifPresentOrElse(p -> {
                    logger.atDebug().log("Target {}", p);
                    put(context, "target", p);
                },
                () -> {
                    logger.atDebug().log("Target not found");
                    remove(context, "target");
                });
        return target.map(t -> COMPLETED_RESULT).orElse(NOT_FOUND_RESULT);
    }
}
