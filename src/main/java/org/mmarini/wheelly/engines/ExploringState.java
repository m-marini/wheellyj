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
import org.mmarini.wheelly.apis.MapSector;
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.lang.Math.*;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.Utils.linear;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Generates the behavior to explore environment.
 * <p>
 * Turns the sensor toward the unknown sectors if in front<br>
 * Turns the robot toward the unknown sectors.<br>
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
public class ExploringState extends AbstractStateNode {
    public static final Validator STATE_SPEC = Validator.objectPropertiesRequired(Map.of(
            "stopDistance", positiveNumber(),
            "turnDirectionRange", integer(minimum(0), maximum(90))
    ), List.of("stopDistance", "turnDirectionRange"));
    private static final Logger logger = LoggerFactory.getLogger(ExploringState.class);

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static ExploringState create(JsonNode root, Locator locator, String id) {
        BASE_STATE_SPEC.apply(locator).accept(root);
        STATE_SPEC.apply(locator).accept(root);
        double stopDistance = locator.path("stopDistance").getNode(root).asDouble();
        int turnDirectionRange = locator.path("turnDirectionRange").getNode(root).asInt();
        ProcessorCommand onInit = ProcessorCommand.concat(
                loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + ".stopDistance", stopDistance,
                        id + ".turnDirectionRange", turnDirectionRange
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new ExploringState(id, onInit, onEntry, onExit);
    }

    /**
     * Creates the exploring state
     *
     * @param id      the identifier
     * @param onInit  the initialize command
     * @param onEntry the entry command
     * @param onExit  the exit command
     */
    protected ExploringState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        super(id, onInit, onEntry, onExit);
    }

    /**
     * Returns the target sector index.
     * <p>
     * The target is in order:
     * <ul>
     *     <li>the front sector if empty or obstacle far away</li>
     *     <li>the middle sector of largest empty interval</li>
     *     <li>the sector with further obstacle</li>
     * </ul>
     * </p>
     *
     * @param context the process context
     */
    int findTargetSector(ProcessorContext context) {
        PolarMap polarMap = context.getPolarMap();
        CircularSector frontSector = polarMap.getSector(0);
        double stopDistance = getDouble(context, "stopDistance");
        if (!frontSector.isHindered() || frontSector.getDistance() >= stopDistance) {
            // returns the front sector if empty or obstacle far away
            return 0;
        }
        // Searches for empty sector intervals and furthest sector with obstacle
        int start = -1;
        int len = 0;
        int furthestIndex = -1;
        double distance = 0;
        List<int[]> intervals = new ArrayList<>();
        int n = polarMap.getSectorsNumber();
        for (int i = 0; i < n; i++) {
            CircularSector sector = polarMap.getSector(i);
            if (!sector.isHindered()) {
                // Sector without obstacle
                if (start >= 0) {
                    // contiguous empty interval
                    len++;
                } else {
                    // start new interval
                    start = i;
                    len = 1;
                }
            } else {
                if (start >= 0) {
                    // Stop interval
                    intervals.add(new int[]{start, len});
                    start = -1;
                    len = 0;
                }
                if (furthestIndex < 0 || sector.getDistance() > distance) {
                    furthestIndex = i;
                    distance = sector.getDistance();
                }
            }
        }
        // check for last interval
        if (start >= 0) {
            intervals.add(new int[]{start, len});
        }

        if (!intervals.isEmpty()) {
            // Find the larger empty sector interval
            int[] target = intervals.stream().max(Comparator.comparingInt(a -> a[1])).orElseThrow();
            // returns the middle sector of the largest empty interval
            return (target[0] + (target[1] - 1) / 2 + n) % n;
        }

        CircularSector furthestSector = polarMap.getSector(furthestIndex);
        return furthestSector.getDistance() > stopDistance
                // returns the sector with further obstacle
                ? furthestIndex
                // or front sector if further obstacle is near (near then stop distance)
                : 0;
    }

    /**
     * Returns the index of the nearest sector or -1 if none
     *
     * @param context the context
     */
    int findUnknownSector(ProcessorContext context) {
        PolarMap map = context.getPolarMap();
        CircularSector frontSector = map.getSector(0);
        return frontSector.getMapSector().isPresent() && !frontSector.isKnown()
                ? 0 // return front sector if unknown
                : IntStream.range(0, map.getSectorsNumber())
                .filter(i -> !map.getSector(i).isKnown())
                .filter(i -> map.getSector(i).getMapSector().isPresent())
                .mapToObj(i -> Tuple2.of(i, abs(map.sectorDirection(i))))
                .min(Comparator.comparingDouble(a -> a._2))
                .map(Tuple2::getV1)
                .orElse(-1);
    }

    @Override
    public String step(ProcessorContext context) {
        if (isBlocked(context)) {
            // Halt robot and move forward the sensor at block
            context.haltRobot();
            context.moveSensor(0);
            return BLOCKED_EXIT;
        }
        if (isTimeout(context)) {
            // Halt robot and move forward the sensor at timeout
            context.haltRobot();
            context.moveSensor(0);
            return TIMEOUT_EXIT;
        }

        // Find the unknown sector target direction
        int targetIndex = findUnknownSector(context);
        PolarMap map = context.getPolarMap();
        if (targetIndex >= 0) {
            int sectorDir = map.radarSectorDirection(targetIndex);
            if (abs(sectorDir) <= 90) {
                // Turns the scanner to the unknown sector
                double dist = map.getSector(targetIndex).getMapSector().map(MapSector::getLocation)
                        .map(map.getCenter()::distance)
                        .orElse(0D);
                logger.atDebug()
                        .setMessage("{}: scan {} DEG, distance {}")
                        .addArgument(this::getId)
                        .addArgument(sectorDir)
                        .addArgument(() -> format("%.2f", dist))
                        .log();
                context.moveSensor(sectorDir);
                context.haltRobot();
            } else {
                // Turns the robot to the unknown sector
                int robotDir = normalizeDegAngle(context.getRobotDirection() + sectorDir);
                logger.atDebug()
                        .setMessage("{}: turn to unknown sector {} DEG")
                        .addArgument(this::getId)
                        .addArgument(robotDir)
                        .log();
                context.moveSensor(0);
                context.moveRobot(robotDir, 0);
            }
            return null;
        }
        context.moveSensor(0);

        targetIndex = findTargetSector(context);
        CircularSector targetSector = map.getSector(targetIndex);

        int sectorDir = map.radarSectorDirection(targetIndex);
        int robotDir = normalizeDegAngle(context.getRobotDirection() + sectorDir);
        double stopDistance = getDouble(context, "stopDistance");
        int turnDirectionRange = getInt(context, "turnDirectionRange");
        double distance = context.getEchoDistance();
        distance = distance == 0 ? targetSector.getDistance()
                : min(distance, targetSector.getDistance());
        double speed = abs(sectorDir) > turnDirectionRange
                ? 0
                : !targetSector.isHindered()
                ? 1
                : min(max((double) round(linear(distance, 0, stopDistance, 1, 4)),
                1), 4) / 4;
        if (speed == 0) {
            logger.atDebug()
                    .setMessage("{}: turn to {} DEG")
                    .addArgument(this::getId)
                    .addArgument(robotDir)
                    .log();
        } else {
            logger.atDebug()
                    .setMessage("{}: move to {} DEG, speed {}")
                    .addArgument(this::getId)
                    .addArgument(robotDir)
                    .addArgument(speed)
                    .log();
        }
        context.moveRobot(robotDir, speed);
        return null;
    }
}
