/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.rrt;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.GridTopology;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.swing.BaseShape;
import org.mmarini.wheelly.swing.CompositeShape;
import org.mmarini.wheelly.swing.MapPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mmarini.wheelly.apis.Complex.DEG0;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.DEFAULT_GROWTH_DISTANCE;
import static org.mmarini.wheelly.engines.SearchRefreshState.FREE_PROB;
import static org.mmarini.wheelly.engines.SearchRefreshState.NEAREST_TARGET_PROB;
import static org.mmarini.wheelly.swing.BaseShape.BORDER_STROKE;
import static org.mmarini.wheelly.swing.BaseShape.createCircle;
import static org.mmarini.wheelly.swing.MapPanel.Layers.TARGETS;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;

public class RRTPathFinderTestApp {
    public static final int RADAR_SIZE = 33;
    public static final double GRID_SIZE = 0.2;
    public static final double MAX_RADAR_DISTANCE = 0.0;
    public static final GridTopology DEFAULT_TOPOLOGY = GridTopology.create(new Point2D.Double(), RADAR_SIZE, RADAR_SIZE, GRID_SIZE);
    public static final int SEED = 1234;
    public static final int TICK_INTERVAL = 10;
    public static final Color EDGE_COLOR = new Color(0f, 0.5f, 0);
    public static final double KNWON_RADIUS = 2;
    private static final Logger logger = LoggerFactory.getLogger(RRTPathFinderTestApp.class);
    private static final double AREA_LIMITS = 2;

    @NotNull
    private static RadarMap createMap(Point2D robotLocation) {
        return RadarMap.empty(DEFAULT_TOPOLOGY)
                .map(cell -> {
                            Point2D location = cell.location();
                            return location.getX() == -AREA_LIMITS || location.getX() == AREA_LIMITS
                                    || location.getY() == -AREA_LIMITS || location.getY() == AREA_LIMITS
                                    || location.getX() == 1 && location.getY() == 1
                                    ? cell.addEchogenic(1, 1)
                                    : location.distance(robotLocation) <= KNWON_RADIUS
                                    ? cell.addAnechoic(1, 1)
                                    : cell;
                        }
                );
    }

    public static void main(String[] args) {
        new RRTPathFinderTestApp().run();
    }

    private final MapPanel mapPanel;
    private final Flowable<Long> ticks;
    private final Point2D.Double robotLocation;
    private final RadarMap radarMap;
    private RRTPathFinder pathFinder;
    private Disposable disposable;

    public RRTPathFinderTestApp() {
        mapPanel = new MapPanel();
        this.robotLocation = new Point2D.Double();
        radarMap = createMap(robotLocation);

        ticks = Flowable.interval(TICK_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation());
    }

    private void run() {
        JFrame frame = createFrame("Test RRT", new JScrollPane(mapPanel), true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        layHorizontally(frame);
        frame.setVisible(true);
        mapPanel.robot(robotLocation, DEG0, DEG0, MAX_RADAR_DISTANCE);
        mapPanel.radarMap(radarMap);

        Random random = new Random(SEED);
        RRTPathFinder.Config config1 = new RRTPathFinder.Config(DEFAULT_GROWTH_DISTANCE, NEAREST_TARGET_PROB, FREE_PROB, robotLocation);
        this.pathFinder = RRTPathFinder.createUnknownTargets(config1, radarMap, 0.221, random);
        pathFinder.init();
        disposable = ticks.subscribe(this::tick);
    }

    private void tick(long t) {
        logger.atDebug().log("Tick #" + t);

        RRT<Point2D> rrt = pathFinder.rrt();
        Set<Point2D> goals = rrt.goals();
        if (pathFinder.isCompleted()) {
            logger.atInfo().log("found {} goals", goals.size());
            disposable.dispose();
            return;
        }
        pathFinder.grow();
        /*
        pathFinder.freeLocations();
        pathFinder.last();
         */
        Stream<BaseShape> goalShapes = goals.stream()
                .map(center ->
                        createCircle(new Color(1f, 1f, 0, 0.5f), BORDER_STROKE, true, center, 0.1f)
                );
        Stream<BaseShape> targetShapes = pathFinder.targets().stream()
                .map(center ->
                        createCircle(new Color(0f, 1f, 0, 0.5f), BORDER_STROKE, true, center, 0.05f)
                );
        Stream<BaseShape> free = pathFinder.freeLocations().stream()
                .map(center ->
                        createCircle(new Color(0f, 1f, 1f, 0.5f), BORDER_STROKE, true, center, 0.03f)
                );

        mapPanel.setLayer(TARGETS.ordinal(), CompositeShape.create(
                Stream.concat(goalShapes, Stream.concat(
                                targetShapes, free
                        ))
                        .toList()
        ));

        Set<Tuple2<Point2D, Point2D>> edges = rrt.edges();
        mapPanel.edges(EDGE_COLOR, edges);

        List<Point2D> path = pathFinder.path();
        if (path == null || path.isEmpty()) {
            mapPanel.path(Color.GREEN);
        } else {
            mapPanel.path(Color.GREEN, path.stream());
        }
    }
}
