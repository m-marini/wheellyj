package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Map;

import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.FuzzyFunctions.defuzzy;
import static org.mmarini.wheelly.apis.FuzzyFunctions.positive;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.RobotCommands.moveAndFrontScan;

/**
 * Generates the behavior to move roboto to target position
 * <p>
 * Turns the sensor front<br>
 * Turns the robot toward the target direction<br>
 * Moves ahead till obstacles or target position.<br/>
 * <p>
 * Parameters are:
 * <ul>
 *     <li><code>timeout</code> the timeout interval (ms)</li>
 *     <li><code>stopDistance</code> the stop distance (m)</li>
 *     <li><code>x</code> the x absolute position (m)</li>
 *     <li><code>y</code> the y absolute position (m)</li>
 * </ul>
 * <p>
 * Returns are:
 * <ul>
 *  <li><code>completed</code> is generated when the target is reached</li>
 *  <li><code>blocked</code> is generated at contact sensors signals</li>
 *  <li><code>timeout</code> is generated at timeout</li>
 * </ul>
 * </p>
 *
 * @param id      the node identifier
 * @param onInit  the initialization command or null if none
 * @param onEntry the entry command or null if none
 * @param onExit  the exit command or null if none
 */
public record MoveToState(String id, ProcessorCommand onInit, ProcessorCommand onEntry,
                          ProcessorCommand onExit) implements ExtendedStateNode {
    public static final String MAX_SPEED = "maxSpeed";
    public static final String STOP_DISTANCE = "stopDistance";
    public static final String TARGET = "target";
    public static final double NEAR_DISTANCE = 0.4;
    public static final int NO_DIRECTION_VALUE = -1000;
    public static final int DEFAULT_DIRECTION_RANGE = 10;
    private static final double DEFAULT_STOP_DISTANCE = 0.4;
    private static final Logger logger = LoggerFactory.getLogger(MoveToState.class);
    private static final int MIN_PPS = 10;

    /**
     * Returns the exploring state from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of exploring state spec
     * @param id      the state identifier
     */
    public static MoveToState create(JsonNode root, Locator locator, String id) {
        Point2D.Double target = null;
        if (locator.path("x").getNode(root).isMissingNode() || locator.path("yx").getNode(root).isMissingNode()) {
            double x = locator.path("x").getNode(root).asDouble();
            double y = locator.path("y").getNode(root).asDouble();
            target = new Point2D.Double(x, y);
        }
        int direction = locator.path("direction").getNode(root).asInt(NO_DIRECTION_VALUE);
        int directionRange = locator.path("directionRange").getNode(root).asInt(NO_DIRECTION_VALUE);
        double stopDistance = locator.path(STOP_DISTANCE).getNode(root).asDouble(DEFAULT_STOP_DISTANCE);
        double maxSpeed = locator.path(MAX_SPEED).getNode(root).asInt(MAX_PPS);
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + "." + STOP_DISTANCE, stopDistance,
                        id + "." + MAX_SPEED, maxSpeed
                )),
                target != null ? ProcessorCommand.put(id + "." + TARGET, target) : null,
                direction != NO_DIRECTION_VALUE ? ProcessorCommand.put(id + "." + "direction", direction) : null,
                directionRange != NO_DIRECTION_VALUE ? ProcessorCommand.put(id + "." + "directionRange", direction) : null,
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new MoveToState(id, onInit, onEntry, onExit);
    }

    /**
     * Returns the state transition result to move to target
     *
     * @param robotStatus    the roboto status
     * @param target         the target
     * @param direction      the target direction
     * @param directionRange the direction range
     * @param stopDistance   the stopDistance
     * @param maxSpeed       the maximum speed (pps)
     */
    private static Tuple2<String, RobotCommands> moveTo(RobotStatus robotStatus, Point2D target, Complex direction, Complex directionRange, double stopDistance, double maxSpeed) {
        // Converts location to meters
        Point2D current = robotStatus.location();

        double distance = current.distance(target);
        logger.atDebug().log("Distance to target {} m, stop at {} m", distance, stopDistance);
        if (distance <= stopDistance) {
            if (direction == null || direction.isCloseTo(robotStatus.direction(), directionRange)) {
                // Target reached
                return COMPLETED_RESULT;
            } else {
                // Rotate to target direction
                return Tuple2.of(NONE_EXIT, moveAndFrontScan(direction, 0));
            }
        }
        double echoDistance = robotStatus.echoDistance();
        // Computes the direction of target
        Complex dir = Complex.direction(current, target);
        //int dir = (int) normalizeDegAngle(round(toDegrees(direction(current, target))));
        // Computes the speed basing on distance between target and obstacle
        double isFar = (echoDistance > 0 && echoDistance < distance && robotStatus.sensorDirection().toIntDeg() == 0)
                ? positive(min(distance, echoDistance) - stopDistance, NEAR_DISTANCE)
                : positive(distance - stopDistance, NEAR_DISTANCE);

        logger.atDebug().log("isFar {}", isFar);

        int speed = (int) round(defuzzy(MIN_PPS, maxSpeed, isFar));

        logger.atDebug().log("move to {} DEG, speed {}", dir, speed);

        return Tuple2.of(NONE_EXIT, moveAndFrontScan(dir, speed));
    }

    /**
     * Create the abstract node
     *
     * @param id      the node identifier
     * @param onInit  the initialization command or null if none
     * @param onEntry the entry command or null if none
     * @param onExit  the exit command or null if none
     */
    public MoveToState(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public void entry(ProcessorContextApi context) {
        ExtendedStateNode.super.entry(context);
        Point2D target = get(context, TARGET);
        if (target != null) {
            context.setTarget(target);
        } else {
            remove(context, TARGET);
        }
    }

    @Override
    public void exit(ProcessorContextApi context) {
        ExtendedStateNode.super.exit(context);
        context.setTarget(null);
    }

    @Override
    public Tuple2<String, RobotCommands> step(ProcessorContextApi context) {
        Tuple2<String, RobotCommands> blockResult = getBlockResult(context);
        if (blockResult != null) {
            // Halt robot and move forward the sensor at block
            return blockResult;
        }
        if (isTimeout(context)) {
            // Halt robot and move forward the sensor at timeout
            return TIMEOUT_RESULT;
        }
        Point2D target = get(context, TARGET);
        if (target == null) {
            logger.atError().log("Missing target in \"{}\" step", id());
            return COMPLETED_RESULT;
        }

        double stopDistance = getDouble(context, STOP_DISTANCE);
        Complex direction = switch (get(context, "direction")) {
            case Number d -> Complex.fromDeg(d.doubleValue());
            case null, default -> null;
        };
        int maxSpeed = getInt(context, MAX_SPEED);
        Complex directionRange = switch (get(context, "directionRange")) {
            case Number d -> Complex.fromDeg(d.doubleValue());
            case null, default -> Complex.fromDeg(DEFAULT_DIRECTION_RANGE);
        };
        return moveTo(context.worldModel().robotStatus(), target, direction, directionRange, stopDistance, maxSpeed);
    }
}
