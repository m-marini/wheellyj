/*
 *
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

package org.mmarini.wheelly.engines.statemachine;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.ProhibitedCellFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Random;
import java.util.Set;

import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TARGET_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.COMPLETED_TRANSITION;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

public class RandomTargetStatus extends AbstractEngineStatus {
    public static final String NOT_FOUND_EXIT = "NotFound";
    public static final StateTransition NOT_FOUND_TRANSTION = StateTransition.create(NOT_FOUND_EXIT, HALT_COMMAND);
    public static final String SAFE_DISTANCE_KEY = "safeDistance";
    public static final String LIKELIHOOD_THRESHOLD_KEY = "likelihoodThreshold";
    public static final String MAX_DISTANCE_KEY = "maxDistance";
    public static final String CENTER_KEY = "center";
    public static final double DEFAULT_SAFE_DISTANCE = 1.5 * STOP_DISTANCE;
    public static final double DEFAULT_LIKELIHOOD_THRESHOLD = 0;
    public static final double DEFAULT_MAX_DISTANCE = 3;
    public static final int NUM_TRY = 100;

    private static final Logger logger = LoggerFactory.getLogger(RandomTargetStatus.class);

    public static RandomTargetStatus create(String name, Random random) {
        return new RandomTargetStatus(name, random);
    }

    private final Random random;
    private double safeDistance;
    private double likelihoodThreshold;
    private double maxDistance;
    private Point2D center;

    protected RandomTargetStatus(String name, Random random) {
        super(name);
        this.random = random;
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        this.safeDistance = context.getDouble(SAFE_DISTANCE_KEY, DEFAULT_SAFE_DISTANCE);
        this.likelihoodThreshold = context.getDouble(LIKELIHOOD_THRESHOLD_KEY, DEFAULT_LIKELIHOOD_THRESHOLD);
        this.maxDistance = context.getDouble(MAX_DISTANCE_KEY, DEFAULT_MAX_DISTANCE);
        this.center = context.<Point2D>get(CENTER_KEY).orElse(new Point2D.Double());
        context.remove(TARGET_KEY);
        return this;
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        GridScannerMap map = data.value().getMap();
        Set<Point> prohibited = ProhibitedCellFinder.create(map, safeDistance, likelihoodThreshold).find();
        Point2D robotLocation = data.value().getWheelly().getRobotLocation();

        for (int i = 0; i < NUM_TRY; i++) {
            Point2D target = new Point2D.Double(
                    center.getX() + (random.nextDouble() - 0.5) * maxDistance * 2,
                    center.getY() + (random.nextDouble() - 0.5) * maxDistance * 2);
            Point cell = map.cell(target);
            if (target.distance(robotLocation) > safeDistance && !prohibited.contains(cell)) {
                logger.debug("Target {}", target);
                context.setTarget(target);
                return COMPLETED_TRANSITION;
            }
        }
        logger.warn("Target not found");
        return NOT_FOUND_TRANSTION;
    }
}
