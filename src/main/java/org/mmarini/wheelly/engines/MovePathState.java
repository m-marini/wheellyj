package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;

import static java.lang.Math.round;
import static org.mmarini.wheelly.apis.FuzzyFunctions.defuzzy;
import static org.mmarini.wheelly.apis.FuzzyFunctions.positive;
import static org.mmarini.wheelly.apis.RobotCommands.moveAndFrontScan;
import static org.mmarini.wheelly.apis.RobotSpec.MAX_PPS;

/**
 * Generates the behaviour to move robot through path
 * <p>
 * Turns the sensor front<br>
 * Moves through path locations.<br/>
 * <p>
 * Parameters are:
 * <ul>
 *     <li><code>timeout</code> the timeout interval (ms) </li>
 *     <li><code>path</code> the list of path locations </li>
 *     <li><code>speed</code> the move speed (pps) </li>
 *     <li><code>approachDistance</code> the approach distance (m) </li>
 * </ul>
 * </p>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>completed</code> is generated when the target is reached</li>
 *  <li><code>notFound</code> is generated when the path is not found or not free</li>
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>frontBlocked</code> is generated at contact sensors signals</li>
 *  <li><code>rearBlocked</code> is generated at contact sensors signals</li>
 *  <li><code>timeout</code> is generated at timeout</li>
 * </ul>
 * </p>
 * <p>
 * Variables are:
 * <ul>
 *  <li><code>targetIndex</code> integer of current target</li>
 * </ul>
 * </p>
 */
public class MovePathState extends TimeOutState {
    public static final String SPEED_ID = "speed";
    public static final String APPROACH_DISTANCE_ID = "approachDistance";
    public static final double NEAR_DISTANCE = 0.4;
    public static final String TIMEOUT_ID = "timeout";
    public static final String PATH_ID = "path";
    public static final String NOT_FOUND_EXIT = "notFound";
    public static final Tuple2<String, RobotCommands> NOT_FOUND_RESULT = Tuple2.of(NOT_FOUND_EXIT, RobotCommands.haltCommand());
    private static final Logger logger = LoggerFactory.getLogger(MovePathState.class);
    private static final int MIN_PPS = 10;
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-move-path-schema-0.1";
    private static final double DEFAULT_APPROACH_DISTANCE = 0.4;
    public static final String SAFETY_DISTANCE_ID = "safetyDistance";
    private static final double DEFAULT_SAFETY_DISTANCE = 0.5;

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static MovePathState create(JsonNode root, Locator locator, String id) {
        WheellyJsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        long timeout = locator.path(TIMEOUT_ID).getNode(root).asLong(DEFAULT_TIMEOUT);
        int speed = locator.path(SPEED_ID).getNode(root).asInt(MAX_PPS);
        double approachDistance = locator.path(APPROACH_DISTANCE_ID).getNode(root).asDouble(DEFAULT_APPROACH_DISTANCE);
        double safetyDistance = locator.path(SAFETY_DISTANCE_ID).getNode(root).asDouble(DEFAULT_SAFETY_DISTANCE);
        ProcessorCommand onInit = ProcessorCommand.create(root, locator.path("onInit"));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        List<Point2D> path = locator.path(PATH_ID).elements(root)
                .<Point2D>map(pointLocator -> {
                    double[] coords = pointLocator.elements(root)
                            .mapToDouble(coordLoc ->
                                    coordLoc.getNode(root).asDouble())
                            .toArray();
                    return new Point2D.Double(coords[0], coords[1]);
                })
                .toList();
        return new MovePathState(id, onInit, onEntry, onExit, timeout, approachDistance, speed, safetyDistance, path.isEmpty() ? null : path);
    }

    private final double approachDistance;
    private final int speed;
    private final double safetyDistance;
    private final List<Point2D> defaultPath;
    private int targetIndex;
    private List<Point2D> path;

    /**
     * Create the abstract node
     *
     * @param id               the node identifier
     * @param onInit           the initialisation command or null if none
     * @param onEntry          the entry command or null if none
     * @param onExit           the exit command or null if none
     * @param timeout          the timeout (ms)
     * @param approachDistance the approach distance (m)
     * @param speed            the maximum speed (pps)
     * @param safetyDistance   the safety distance (m)
     * @param defaultPath      the default path
     */
    public MovePathState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit, long timeout, double approachDistance, int speed, double safetyDistance, List<Point2D> defaultPath) {
        super(id, onInit, onEntry, onExit, timeout);
        this.approachDistance = approachDistance;
        this.speed = speed;
        this.safetyDistance = safetyDistance;
        this.defaultPath = defaultPath;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        super.entry(context);
        targetIndex = 0;
        path = get(context, PATH_ID, defaultPath);
        context.path(path);
        if (path != null && !path.isEmpty()) {
            context.target(path.getLast());
        }
    }

    /**
     * Moves robot to current path location
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> move(ProcessorContextApi context) {
        if (path == null) {
            context.path(null).target(null);
            return NOT_FOUND_RESULT;
        }
        WorldModel worldModel = context.worldModel();
        RadarMap map = worldModel.radarMap();
        RobotStatus robotStatus = worldModel.robotStatus();
        Point2D robotLocation = robotStatus.location();
        Point2D target = path.get(targetIndex);
        if (!map.freeTrajectory(robotLocation, target, safetyDistance)) {
            context.path(null).target(null);
            return NOT_FOUND_RESULT;
        }
        double distance = robotLocation.distance(target);
        if (distance <= approachDistance) {
            // Target reached
            return nextLocation(context);
        }
        // Computes direction
        Complex direction = Complex.direction(robotLocation, target);
        // Computes speed
        double isFar = positive(distance - approachDistance, NEAR_DISTANCE);
        int speed = (int) round(defuzzy(MIN_PPS, this.speed, isFar));
        logger.atDebug().log("move to {} DEG, speed {}", direction, speed);
        return Tuple2.of(NONE_EXIT, moveAndFrontScan(direction, speed));
    }

    /**
     * Sets the next location
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> nextLocation(ProcessorContextApi context) {
        if (++targetIndex >= path.size()) {
            context.path(null).target(null);
            logger.atDebug().log("Completed");
            return COMPLETED_RESULT;
        }
        logger.atDebug().log("Move to {}", path.get(targetIndex));
        return move(context);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        Tuple2<String, RobotCommands> result = super.step(context);
        return result != null
                // Halt the robot and move forward the sensor at block
                ? result
                : move(context);
    }
}
