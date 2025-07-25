package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Map;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.FuzzyFunctions.defuzzy;
import static org.mmarini.wheelly.apis.FuzzyFunctions.positive;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.RobotCommands.moveAndFrontScan;

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
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>timeout</code> is generated at timeout</li>
 * </ul>
 * </p>
 * <p>
 * Variables are:
 * <ul>
 *  <li><code>targetIndex</code> integer of current target</li>
 * </ul>
 * </p>
 *
 * @param id      the node identifier
 * @param onInit  the initialisation command or null if none
 * @param onEntry the entry command or null if none
 * @param onExit  the exit command or null if none
 */
public record MovePathState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit)
        implements ExtendedStateNode {
    public static final String TARGET_INDEX = "targetIndex";
    public static final String SPEED = "speed";
    public static final String APPROACH_DISTANCE = "approachDistance";
    public static final double DEFAULT_TARGET_DISTANCE = 0.5;
    public static final double NEAR_DISTANCE = 0.4;
    private static final Logger logger = LoggerFactory.getLogger(MovePathState.class);
    private static final int MIN_PPS = 10;
    private static final String SCHEMA_NAME = "https://mmarini.org/wheelly/state-move-path-schema-0.1";
    private static final double DEFAULT_APPROACH_DISTANCE = 0.4;

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static MovePathState create(JsonNode root, Locator locator, String id) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        int speed = locator.path(SPEED).getNode(root).asInt(MAX_PPS);
        double approachDistance = locator.path(APPROACH_DISTANCE).getNode(root).asDouble(DEFAULT_APPROACH_DISTANCE);
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + "." + SPEED, speed,
                        id + "." + APPROACH_DISTANCE, approachDistance
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new MovePathState(id, onInit, onEntry, onExit);
    }

    /**
     * Create the abstract node
     *
     * @param id      the node identifier
     * @param onInit  the initialisation command or null if none
     * @param onEntry the entry command or null if none
     * @param onExit  the exit command or null if none
     */
    public MovePathState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        ExtendedStateNode.super.entry(context);
        put(context, TARGET_INDEX, 0);
    }

    /**
     * Moves robot to current path location
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> move(ProcessorContextApi context) {
        List<Point2D> path = path(context);
        int index = targetIndex(context);
        if (index < 0 || index >= path.size()) {
            return COMPLETED_RESULT;
        }
        Point2D target = path.get(index);
        if (get(context, TARGET_ID) == null) {
            put(context, TARGET_ID, target);
        }
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Point2D robotLocation = robotStatus.location();
        double distance = robotLocation.distance(target);
        double approachDistance = getDouble(context, APPROACH_DISTANCE, DEFAULT_TARGET_DISTANCE);
        if (distance <= approachDistance) {
            // Target reached
            return nextLocation(context);
        }
        // Computes direction
        Complex direction = Complex.direction(robotLocation, target);
        // Computes speed
        double isFar = positive(distance - approachDistance, NEAR_DISTANCE);
        int maxSpeed = getInt(context, SPEED);
        int speed = (int) round(defuzzy(MIN_PPS, maxSpeed, isFar));
        logger.atDebug().log("move to {} DEG, speed {}", direction, speed);
        return Tuple2.of(NONE_EXIT, moveAndFrontScan(direction, speed));
    }

    /**
     * Sets the next location
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> nextLocation(ProcessorContextApi context) {
        remove(context, TARGET_ID);
        int targetIndex = targetIndex(context) + 1;
        List<Point2D> path = path(context);
        if (targetIndex >= path.size()) {
            remove(context, PATH_ID);
            logger.atDebug().log("Completed");
            return COMPLETED_RESULT;
        }
        put(context, TARGET_INDEX, targetIndex);
        logger.atDebug().log("Move to {}", path.get(targetIndex));
        return move(context);
    }

    /**
     * Returns the path
     *
     * @param context the context
     */
    private List<Point2D> path(ProcessorContextApi context) {
        return get(context, PATH_ID);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        Tuple2<String, RobotCommands> blockResult = getBlockResult(context);
        if (blockResult != null) {
            // Halt the robot and move forward the sensor at block
            return blockResult;
        }
        if (isTimeout(context)) {
            // Halt the robot and move forward the sensor at timeout
            return TIMEOUT_RESULT;
        }
        Tuple2<String, RobotCommands> result = validate(context);
        if (result != null) {
            return result;
        }
        return move(context);
    }

    /**
     * Returns the target index
     *
     * @param context the context
     */
    private int targetIndex(ProcessorContextApi context) {
        return getInt(context, TARGET_INDEX, -1);
    }

    /**
     * Validate thecontext variables
     *
     * @param context the context
     */
    private Tuple2<String, RobotCommands> validate(ProcessorContextApi context) {
        int index = targetIndex(context);
        if (index < 0) {
            logger.atError().log("Missing target in \"{}\" step", id());
            return COMPLETED_RESULT;
        }
        Object obj = get(context, PATH_ID);
        if (!(obj instanceof List<?>)) {
            logger.atError().log("Missing path in \"{}\" step", id());
            return COMPLETED_RESULT;
        }
        return null;
    }
}
