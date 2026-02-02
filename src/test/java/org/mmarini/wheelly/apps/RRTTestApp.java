/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.engines.RRTPathFinder;
import org.mmarini.wheelly.swing.MapPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Graphical AStar test
 */
public class RRTTestApp {

    public static final GridTopology TOPOLOGY = GridTopology.create(new Point2D.Double(), 33, 33, 0.2);
    public static final long SEED = 1234L;
    public static final int NUM_LABELS = 1;
    public static final int NUM_OBSTACLES = 8;
    public static final int CLEAN_TIME = 10000;
    public static final Point2D.Double ROBOT_LOCATION = new Point2D.Double(-1.2, -2.6);
    public static final Complex ROBOT_DIRECTION = Complex.DEG0;
    public static final int PERIOD = 50;
    public static final double DISTANCE = 0.6;
    public static final int MIN_GOALS = 4;
    public static final Color SECTOR_COLOR = new Color(0, 1, 0, 0.3F);
    public static final double GROWTH_DISTANCE = 0.4;
    private static final double WORLD_WIDTH = TOPOLOGY.width() * TOPOLOGY.gridSize();
    private static final double MAX_OBSTACLE_DISTANCE = WORLD_WIDTH / 2;
    private static final double WORLD_HEIGHT = TOPOLOGY.height() * TOPOLOGY.gridSize();
    private static final double MIN_OBSTACLE_DISTANCE = 0.4;
    private static final double DECAY = 10 * 1000;
    private static final Logger logger = LoggerFactory.getLogger(RRTTestApp.class);
    public static final double SAFETY_DISTANCE = 0.3;

    private static ObstacleMap createObstacles() {
        Random random = new Random(SEED);
        return MapBuilderOld.create(TOPOLOGY.gridSize())
                .rect(false, -WORLD_WIDTH / 2,
                        -WORLD_HEIGHT / 2, WORLD_WIDTH / 2, WORLD_HEIGHT / 2)
                .rand(NUM_OBSTACLES, NUM_LABELS, new Point2D.Double(), MAX_OBSTACLE_DISTANCE,
                        new Point2D.Double(), MIN_OBSTACLE_DISTANCE, random)
                .build();
    }

    private static RRTPathFinder createPathFinder(RadarMap map, ObstacleMap obstacles, Point2D location) {
        //return RRTPathFinder.createUnknownTargets(map, location, GROWTH_DISTANCE, new Random(SEED));
//        return RRTPathFinder.createLeastEmptyTargets(map, location, GROWTH_DISTANCE, 3, new Random(SEED));
        return RRTPathFinder.createLabelTargets(map, location, DISTANCE, SAFETY_DISTANCE, GROWTH_DISTANCE, new Random(SEED), obstacles.labeled());
    }

    private static RadarMap createRadarMap(ObstacleMap obstacles) {
        long t0 = 1;
        // Creates the radar map
        return RadarMap.empty(TOPOLOGY).map(cell -> {
            Point2D point = cell.location();
            if (point.distance(ROBOT_LOCATION) > 3) {
                return cell;
            } else {
                Optional<ObstacleMap.ObstacleCell> obstacleOpt = obstacles.cells().stream()
                        .filter(obstacleCell -> obstacleCell.location().distanceSq(point) <= TOPOLOGY.gridSize() / 2)
                        .findAny();
                return obstacleOpt.map(obs ->
                        cell.addEchogenic(t0, DECAY)
                ).orElse(cell.addAnechoic(t0, DECAY));
            }
        });
    }

    public static void main(String[] args) {
        new RRTTestApp().run();
    }

    private final JFrame frame;
    private final MapPanel mapPanel;
    private final RadarMap map;
    private final ObstacleMap obstacles;
    private final Point2D target;
    private final Point2D robotLocation;
    private final Complex robotDirection;
    private final JButton restartButton;
    private RRTPathFinder pathFinder;

    /**
     * Creates the test
     */
    public RRTTestApp() {
        this.frame = new JFrame();
        this.mapPanel = new MapPanel();
        this.restartButton = new JButton("Restart");
        this.obstacles = createObstacles();
        this.map = createRadarMap(obstacles);
        this.target = obstacles.labeled().findAny().orElse(null);
        this.robotLocation = ROBOT_LOCATION;
        this.robotDirection = ROBOT_DIRECTION;
        this.pathFinder = createPathFinder();

        frame.setSize(800, 600);
        frame.setTitle("AStar test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(restartButton);

        Container content = frame.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(BorderLayout.CENTER, new JScrollPane(mapPanel));
        content.add(BorderLayout.SOUTH, buttonPanel);

        restartButton.addActionListener(ev -> restart());
    }

    private RRTPathFinder createPathFinder() {
        return createPathFinder(map, obstacles, robotLocation).init();
    }

    private void restart() {
        showStatus();
        restartButton.setEnabled(false);
        pathFinder = createPathFinder();
        Flowable.interval(PERIOD, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.computation())
                .takeUntil((Long i) ->
                        pathFinder.isFound()
                                && pathFinder.rrt().goals().size() >= MIN_GOALS
                )
                .subscribe(ignored -> {
                            pathFinder.grow();
                            showStatus();
                        },
                        err -> {
                        },
                        () -> restartButton.setEnabled(true)
                );
    }

    /**
     * Runs the test
     */
    private void run() {
        mapPanel.radarMap(map);
        mapPanel.obstacles(obstacles);
        mapPanel.markers(obstacles.labeled()
                .map(pt -> new LabelMarker("A", pt, 1, 1, CLEAN_TIME))
                .toList());
        mapPanel.target(target);
        mapPanel.robot(robotLocation, robotDirection, null, 2);
        frame.setVisible(true);
        restart();
    }

    private void showStatus() {
        List<Point2D> path = pathFinder.path();
        if (pathFinder.isFound()) {
            logger.atInfo().log("found path:");
            Point2D prev = null;
            double tot = 0;
            for (Point2D point2D : path) {
                double cost = prev != null ? prev.distance(point2D) : 0;
                tot += cost;
                prev = point2D;
                logger.atInfo().log("   {} cost={} tot={}", point2D, cost, tot);
            }
        }
        mapPanel.path(Color.RED, path != null ? path.stream() : null);
        mapPanel.edges(Color.WHITE, pathFinder.rrt().edges().stream().toList());
        mapPanel.sectors((float) map.topology().gridSize(), SECTOR_COLOR,
                pathFinder.targets());
        /*
        mapPanel.sectors((float) map.topology().robotMapSize(), SECTOR_COLOR,
                pathFinder.freeLocations());
                */
        mapPanel.pingLocations(path != null ? path.getLast() : pathFinder.last());
    }
}
