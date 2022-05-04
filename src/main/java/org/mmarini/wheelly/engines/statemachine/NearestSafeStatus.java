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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.GridScannerMap;
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Comparator;
import java.util.Set;

import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;

public class NearestSafeStatus implements EngineStatus {
    public static final String SAFE_DISTANCE_KEY = "NearestSafeStatus.safeDistance";
    public static final String LIKELIHOOD_THRESHOLD_KEY = "NearestSafeStatus.lilelihoodThreshold";
    public static final String TARGET_KEY = "NearestSafeStatus.target";
    public static final double DEFAULT_SAFE_DISTANCE = 1.5 * STOP_DISTANCE;
    public static final double DEFAULT_LIKELIHOOD_THRESHOLD = 0;
    private static final NearestSafeStatus SINGLETON = new NearestSafeStatus();

    public static NearestSafeStatus create() {
        return SINGLETON;
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        GridScannerMap map = (GridScannerMap) data._2;
        double safeDistance = context.<Number>get(SAFE_DISTANCE_KEY).orElse(DEFAULT_SAFE_DISTANCE).doubleValue();
        double likelihoodThreshold = context.<Number>get(LIKELIHOOD_THRESHOLD_KEY).orElse(DEFAULT_LIKELIHOOD_THRESHOLD).doubleValue();
        Set<Point> prohibited = ProhibitedCellFinder.create(map, safeDistance, likelihoodThreshold).find();
        Point2D robotLocation = data._1.value().sample.robotAsset.location;
        Point2D target = ProhibitedCellFinder.findContour(prohibited).stream()
                .map(map::toPoint)
                .min(Comparator.comparingDouble(a -> a.distance(robotLocation)))
                .orElse(robotLocation);
        context.put(TARGET_KEY, target);
        return StateTransition.create(COMPLETED_EXIT, context, ALT_COMMAND);
    }
}
