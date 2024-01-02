package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.Map;

import static java.lang.Math.round;
import static java.lang.Math.toDegrees;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.FuzzyFunctions.defuzzy;
import static org.mmarini.wheelly.apis.FuzzyFunctions.positive;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.RobotCommands.moveAndFrontScan;
import static org.mmarini.wheelly.apis.Utils.direction;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

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
        double x = locator.path("x").getNode(root).asDouble();
        double y = locator.path("y").getNode(root).asDouble();
        double stopDistance = locator.path("stopDistance").getNode(root).asDouble();
        ProcessorCommand onInit = ProcessorCommand.concat(
                ExtendedStateNode.loadTimeout(root, locator, id),
                ProcessorCommand.setProperties(Map.of(
                        id + ".stopDistance", stopDistance,
                        id + ".target", new Point2D.Double(x, y)
                )),
                ProcessorCommand.create(root, locator.path("onInit")));
        ProcessorCommand onEntry = ProcessorCommand.create(root, locator.path("onEntry"));
        ProcessorCommand onExit = ProcessorCommand.create(root, locator.path("onExit"));
        return new MoveToState(id, onInit, onEntry, onExit);
    }

    /**
     * Returns the state transition result to move to target
     *
     * @param robotStatus  the roboto status
     * @param target       the target
     * @param stopDistance the stopDistance
     */
    private static Tuple2<String, RobotCommands> moveTo(RobotStatus robotStatus, Point2D target, double stopDistance) {
        // Converts location to meters
        Point2D current = robotStatus.getLocation();

        double distance = current.distance(target);
        logger.atDebug().log("Distance to target {} m, stop at {} m", distance, stopDistance);
        if (distance <= stopDistance) {
            // Target reached
            return COMPLETED_RESULT;
        }
        // Computes the direction of target
        int dir = (int) normalizeDegAngle(round(toDegrees(direction(current, target))));
        // Computes the speed
        double nearDistance = 0.4;
        double isFar = positive(distance - stopDistance, nearDistance);
        logger.atDebug().log("isFar {}", isFar);

        int speed = (int) round(defuzzy(MIN_PPS, MAX_PPS, isFar));

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
    public Tuple2<String, RobotCommands> step(ProcessorContext context) {
        Tuple2<String, RobotCommands> blockResult = context.getBlockResult();
        if (blockResult != null) {
            // Halt robot and move forward the sensor at block
            return blockResult;
        }
        if (isTimeout(context)) {
            // Halt robot and move forward the sensor at timeout
            return TIMEOUT_RESULT;
        }

        double stopDistance = getDouble(context, "stopDistance");
        Point2D target = get(context, "target");
        return moveTo(context.getRobotStatus(), target, stopDistance);
    }
}
