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

package org.mmarini.wheelly.swing;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Function3;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.engines.statemachine.FindPathStatus;
import org.mmarini.wheelly.engines.statemachine.ProhibitedCellFinder;
import org.mmarini.wheelly.model.*;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Math.exp;
import static java.lang.String.format;
import static java.util.stream.IntStream.range;
import static org.mmarini.wheelly.engines.statemachine.FindPathStatus.DEFAULT_LIKELIHOOD_THRESHOLD;
import static org.mmarini.wheelly.engines.statemachine.FindPathStatus.DEFAULT_SAFE_DISTANCE;
import static org.mmarini.wheelly.model.AbstractScannerMap.LIKELIHOOD_TAU;
import static org.mmarini.wheelly.model.RobotController.STOP_DISTANCE;
import static org.mmarini.wheelly.model.RobotController.WARN_DISTANCE;
import static org.mmarini.wheelly.swing.Dashboard.INFO_DISTANCE;
import static org.mmarini.wheelly.swing.RxJoystick.NONE_CONTROLLER;
import static org.mmarini.wheelly.swing.TopographicMap.DEFAULT_MAX_DISTANCE;
import static org.mmarini.yaml.Utils.fromFile;

/**
 *
 */
public class UIController {
    public static final long FRAME_INTERVAL = 1000L / 60;
    public static final Color PROHIBITED_COLOR = new Color(0x4f432c);// new Color(0x7c4422);
    private static final String CONFIG_FILE = ".wheelly.yml";
    private static final Logger logger = LoggerFactory.getLogger(UIController.class);
    private static final int FORWARD_DIRECTION = 0;
    private static final Color CONTOUR_COLOR = new Color(0x008800);
    ;

    /**
     *
     */
    public static UIController create() {
        return new UIController();
    }

    private static Shape createCellShape(Point2D center, double size) {
        return new Rectangle2D.Double(center.getX() - size / 2, center.getY() - size / 2, size, size);
    }

    private static List<Tuple2<Color, Shape>> createContourShapes(GridScannerMap map, Point2D offset, double maxDistance) {
        Set<Point> cells = ProhibitedCellFinder.findContour(
                ProhibitedCellFinder.create(map, DEFAULT_SAFE_DISTANCE, DEFAULT_LIKELIHOOD_THRESHOLD).find());
        return cells.stream()
                .map(map::toPoint)
                .map(o -> Tuple2.of(o, o.distance(offset)))
                .filter(t -> t._2 <= maxDistance)
                .map(center -> {
                    Shape shape = createCellShape(center._1, map.gridSize);
                    return Tuple2.of(CONTOUR_COLOR, shape);
                })
                .collect(Collectors.toList());
    }

    private static List<Tuple2<Color, Shape>> createProhibitedShapes(GridScannerMap map, Point2D offset, double maxDistance) {
        Set<Point> cells = ProhibitedCellFinder.create(map, DEFAULT_SAFE_DISTANCE, DEFAULT_LIKELIHOOD_THRESHOLD).find();
        Set<Point> mapCells = map.getCells().collect(Collectors.toSet());
        cells.removeAll(mapCells);
        List<Tuple2<Color, Shape>> result = cells.stream()
                .map(map::toPoint)
                .map(o -> Tuple2.of(o, o.distance(offset)))
                .filter(t -> t._2 <= maxDistance)
                .map(center -> {
                    Shape shape = createCellShape(center._1, map.gridSize);
                    return Tuple2.of(PROHIBITED_COLOR, shape);
                })
                .collect(Collectors.toList());
        result.addAll(createScannerMapShapes(map, offset, maxDistance));
        return result;
    }

    private static List<Tuple2<Color, Shape>> createScannerMapShapes(GridScannerMap map, Point2D offset, double maxDistance) {
        long now = System.currentTimeMillis();
        return map.obstacles.stream()
                .map(o -> Tuple2.of(o, o.getLocation().distance(offset)))
                .filter(t -> t._2 <= maxDistance)
                .map(t -> {
                    Obstacle o = t._1;
                    Shape shape = createCellShape(o.location, map.gridSize);
                    double dt = (now - o.timestamp) * 1e-3;
                    double value = o.likelihood * exp(-dt / LIKELIHOOD_TAU);
                    return Tuple2.of(value, t.setV1(shape));
                })
                .sorted(Comparator.comparing(Tuple2::getV1))
                .map(t -> {
                    float bright = (float) ((t._1 * 0.8) + 0.2);
                    double distance = t._2._2;
                    double hue;
                    if (distance > INFO_DISTANCE) {
                        hue = 0;
                    } else if (distance > WARN_DISTANCE) {
                        hue = 1d / 3;
                    } else if (distance > STOP_DISTANCE) {
                        hue = 1d / 6;
                    } else {
                        hue = 0;
                    }
                    float saturation = distance > INFO_DISTANCE ? 0 : 1;

                    Color c = Color.getHSBColor((float) hue, saturation, bright);
                    return Tuple2.of(c, t._2._1);
                })
                .collect(Collectors.toList());
    }

    private final PreferencesPane preferencesPane;
    private final WheellyFrame frame;
    private final Dashboard dashboard;
    private final Radar radar;
    private final GlobalMap globalMap;
    private String joystickPort;
    private JsonNode configNode;
    private RobotAgent robotAgent;
    private ConfigParameters configParams;
    private Function3<
            GridScannerMap, Point2D, Double,
            List<Tuple2<Color, Shape>>> shapeBuilder;

    /**
     *
     */
    protected UIController() {
        this.joystickPort = NONE_CONTROLLER;
        this.frame = new WheellyFrame();
        this.preferencesPane = new PreferencesPane();
        this.dashboard = frame.getDashboard();
        this.radar = frame.getRadar();
        this.globalMap = frame.getGlobalMap();
        this.shapeBuilder = UIController::createProhibitedShapes;

        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.setSize(1024, 800);
        this.frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                handleWindowOpened();
            }
        });
    }

    /**
     *
     */
    private UIController buildFlow() {
        SwingObservable.actions(frame.getPreferences())
                .toFlowable(BackpressureStrategy.BUFFER)
                .doOnNext(this::handlePreferences)
                .subscribe();
        dashboard.getResetFlow()
                .doOnNext(ev -> closeConfig()
                        .openConfig())
                .subscribe();
        frame.getScannerViewFlow().subscribe(x -> this.shapeBuilder = UIController::createScannerMapShapes);
        frame.getProhibitedViewFlow().subscribe(x -> this.shapeBuilder = UIController::createProhibitedShapes);
        frame.getContourViewFlow().subscribe(x -> this.shapeBuilder = UIController::createContourShapes);
        return this;
    }

    /**
     * Returns the UIController with current configuration closed
     */
    private UIController closeConfig() {
        if (robotAgent != null) {
            robotAgent.close();
            robotAgent = null;
        }
        return this;
    }

    /**
     * Returns the inference engine
     */
    private InferenceEngine createEngine() {
        String engineRef = Yaml.engine(configNode);
        JsonPointer ptr = JsonPointer.valueOf(engineRef);
        JsonNode engineNode = configNode.at(ptr);
        if (engineNode.isMissingNode()) {
            throw new IllegalArgumentException(format("Missing %s node", engineRef));
        }
        String builder = engineNode.path("builder").asText();
        if (builder.isEmpty()) {
            throw new IllegalArgumentException(format("Missing %s/builder node", engineRef));
        }
        Matcher m = Pattern.compile("^([a-zA-Z_]\\w*\\.)+([a-zA-Z_]\\w*)$").matcher(builder);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("builder %s does not match the format", builder));
        }
        String methodName = m.group(2);
        String className = builder.substring(0, builder.length() - methodName.length() - 1);
        try {
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(className);
            Method creator = clazz.getDeclaredMethod(methodName, JsonNode.class);
            int modifiers = creator.getModifiers();
            if (!Modifier.isStatic(modifiers)) {
                throw new IllegalArgumentException(format("builder %s is not static", builder));
            }
            Object result = creator.invoke(null, engineNode);
            return (InferenceEngine) result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void handleCps(Timed<Integer> cps) {
        dashboard.setCps(cps.value());
    }

    private void handleInferenceData(Tuple2<String, Optional<?>> data) {
        if (data._1.equals(FindPathStatus.PATH_KEY)) {
            List<Line2D> path = data._2.map(pts -> {
                List<Point2D> points = (List<Point2D>) pts;
                List<Line2D> x = range(0, points.size() - 1).mapToObj(i -> {
                            Point2D pa = points.get(i);
                            Point2D pb = points.get(i + 1);
                            return new Line2D.Double(pa, pb);
                        })
                        .collect(Collectors.toList());
                return x;
            }).orElse(List.of());
            radar.setPath(path);
            globalMap.setPath(path);
        }
    }

    private void handleInferenceMessage(String text) {
        logger.info(text);
        frame.log(text);
    }

    private void handleMapMessage(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> tuple) {
        WheellyStatus status = tuple._1.value();
        ProxySample sample = status.sample;
        if (sample.sensorRelativeDeg == FORWARD_DIRECTION) {
            dashboard.setObstacleDistance(sample.distance);
        }
        dashboard.setPower(status.voltage);
        dashboard.setMotors(status.motors._1, status.motors._2);
        dashboard.setForwardBlock(!status.canMoveForward);
        RobotAsset robotAsset = sample.robotAsset;
        dashboard.setAngle(robotAsset.getDirectionRad());
        dashboard.setRobotLocation(robotAsset.location);
        radar.setAsset(robotAsset.location, status.sample.robotAsset.getDirectionRad());
        globalMap.setAsset(robotAsset.location, robotAsset.getDirectionRad());
        dashboard.setImuFailure(status.imuFailure ? 1 : 0);

        List<Tuple2<Color, Shape>> shapes = shapeBuilder.apply((GridScannerMap) tuple._2, robotAsset.location, DEFAULT_MAX_DISTANCE);

        radar.setShapes(shapes);
        globalMap.setShapes(shapes);

    }

    /**
     * @param actionEvent the event
     */
    private void handlePreferences(ActionEvent actionEvent) {
        preferencesPane.setJoystickPort(joystickPort)
                .setBaseUrl("");
        int result = JOptionPane.showConfirmDialog(
                frame, preferencesPane, "Preferences",
                JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            joystickPort = preferencesPane.getJoystickPort();
        }
    }

    /**
     * Handles the application on window open.
     * Opens current configuration
     */
    private void handleWindowOpened() {
        openConfig();
    }

    /**
     * @throws IOException in case of error
     */
    private void loadConfig() throws IOException {
        this.configNode = fromFile(CONFIG_FILE);
        Yaml.config().apply(Locator.root()).accept(configNode);
        configParams = Yaml.configParams(configNode);
    }

    /**
     * Returns the UIController with current configuration opened
     */
    private void openConfig() {
        try {
            logger.debug("openConfig");
            InferenceEngine engine = createEngine();
            this.robotAgent = RobotAgent.create(configParams, engine);

            robotAgent.readConnection()
                    .subscribe(connected -> {
                        dashboard.setWifiLed(connected);
                        frame.log(connected ? "Connected" : "Disconnected");
                    });

            robotAgent.readErrors()
                    .subscribe(ex -> {
                        logger.error("Error on flow", ex);
                        frame.log(ex.getMessage());
                    });

            robotAgent.readMapFlow()
                    .throttleLatest(FRAME_INTERVAL, TimeUnit.MILLISECONDS)
                    .subscribe(this::handleMapMessage);

            robotAgent.readCps()
                    .subscribe(this::handleCps);

            robotAgent.readInferenceData().subscribe(this::handleInferenceData);

            robotAgent.readInferenceMessages().subscribe(this::handleInferenceMessage);

            robotAgent.start();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            frame.log(ex.getMessage());
        }
    }

    /**
     * Starts the application
     * Sets the window visible
     * Initializes the default configuration
     */
    public void start() {
        try {
            loadConfig();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            System.exit(1);
        }
        buildFlow();
        frame.setVisible(true);
    }
}
