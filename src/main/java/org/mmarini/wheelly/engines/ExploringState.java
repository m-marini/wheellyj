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
import org.mmarini.wheelly.envs.CircularSector;
import org.mmarini.wheelly.envs.PolarMap;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.IntStream;

import static java.lang.Math.*;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * Generates the behavior to explore environment.
 * <p>
 * Turns the robot toward the unknown sectors or moves the robot to the furthest obstacle.
 * The front sector is prioritized on the other sectors.
 * Auto scan mey be activated.<br>
 * <code>blocked</code> is generated at contact sensors signals.<br>
 * <code>timeout</code> is generated at timeout.
 * </p>
 */
public class ExploringState extends AbstractStateNode {
    public static final Validator STATE_SPEC = Validator.objectPropertiesRequired(Map.of(
            "minMoveDistance", positiveNumber(),
            "maxMoveDirection", integer(minimum(0), maximum(90))
    ), List.of("minMoveDistance", "maxMoveDirection"));
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
        double minMoveDistance = locator.path("minMoveDistance").getNode(root).asDouble();
        int maxMoveDirection = locator.path("maxMoveDirection").getNode(root).asInt();
        ProcessorCommand onInit = ProcessorCommand.concat(
                loadTimeout(root, locator, id),
                loadAutoScanOnInit(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + ".minMoveDistance", minMoveDistance,
                        id + ".maxMoveDirection", maxMoveDirection
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new ExploringState(id, onInit, onEntry, onExit);
    }

    /**
     * Returns the target sector index.
     * <p>
     * The target is in order:
     * <ul>
     *     <li>the front sector if unknown</li>
     *     <li>the unknown sector nearest the front</li>
     *     <li>the front sector if empty</li>
     *     <li>the middle sector of largest empty interval</li>
     *     <li>the front sector if obstacle distance >= minMoveDistance</li>
     *     <li>the sector with further obstacle</li>
     * </ul>
     * </p>
     *
     * @param map             the polar map
     * @param minMoveDistance the minimum moving distance
     */
    static int findSectorTarget(PolarMap map, double minMoveDistance) {
        CircularSector[] sectors = map.getSectors();
        CircularSector frontSector = sectors[0];
        if (!frontSector.isKnown()) {
            // Returns the front sector if unknown
            return 0;
        }
        int n = sectors.length;
        // Searches for known sector nearest the front
        Optional<Integer> unknownSector1 = IntStream.range(0, n)
                .filter(i -> !sectors[i].isKnown())
                .mapToObj(i -> Tuple2.of(i, abs(map.sectorDirection(i))))
                .min(Comparator.comparingDouble(a -> a._2))
                .map(Tuple2::getV1);
        if (unknownSector1.isPresent()) {
            // Returns the known sector nearest the front
            return unknownSector1.get();
        }
        // All sectors are known
        if (!frontSector.hasObstacle()) {
            // Returns the front sector if empty
            return 0;
        }
        // Search for empty sector intervals and furthest sector with obstacle
        int start = -1;
        int len = 0;
        int furthest = -1;
        double distance = 0;
        List<int[]> intervals = new ArrayList<>();
        for (int i = 0; i < sectors.length; i++) {
            CircularSector sector = sectors[i];
            if (!sector.hasObstacle()) {
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
                if (furthest < 0 || sector.getDistance() > distance) {
                    furthest = i;
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
            // returns the middle sector in the interval
            return (target[0] + (target[1] - 1) / 2 + sectors.length) % sectors.length;
        }
        if (frontSector.getDistance() >= minMoveDistance) {
            // returns the front sector if obstacle distance is >= minMoveDistance
            return 0;
        }
        // returns the further obstacle sector if no interval available
        return furthest;
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

    @Override
    public void entry(ProcessorContext context) {
        super.entry(context);
        entryAutoScan(context);
    }

    @Override
    public String step(ProcessorContext context) {
        tickAutoScan(context);
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
        PolarMap polarMap = context.getPolarMap();
        double minMoveDistance = getDouble(context, "minMoveDistance");
        int sectorIndex = findSectorTarget(polarMap, minMoveDistance);
        CircularSector[] sectors = polarMap.getSectors();
        CircularSector sector = sectors[sectorIndex];
        int maxMoveDirection = getInt(context, "maxMoveDirection");
        // Compute speed
        int dDirection = (int) round(toDegrees(polarMap.sectorDirection(sectorIndex)));
        double distance = sector.getDistance();
        float speed = abs(dDirection) < maxMoveDirection && (distance == 0 || distance >= minMoveDistance)
                ? 1 : 0;
        int robotDir = context.getRobotStatus().getDirection();
        int dir = normalizeDegAngle(robotDir + dDirection);
        String sectorAttribute;
        if (!sector.isKnown()) {
            sectorAttribute = "unknown";
        } else if (sector.hasObstacle()) {
            sectorAttribute = "furthest obstacle";
        } else {
            sectorAttribute = "empty";
        }
        if (speed == 0) {
            logger.debug("{}: turn to {} sector {} DEG, {}", getId(), sectorAttribute, dir, speed);
        } else {
            logger.debug("{}: move to {} sector {} DEG, {}", getId(), sectorAttribute, dir, speed);
        }
        context.moveRobot(dir, speed);
        return null;
    }
}
