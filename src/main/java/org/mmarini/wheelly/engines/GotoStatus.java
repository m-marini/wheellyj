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

package org.mmarini.wheelly.engines;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.MoveCommand;
import org.mmarini.wheelly.model.RobotAsset;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.StringJoiner;

import static java.lang.Math.round;
import static java.lang.Math.toDegrees;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.model.FuzzyFunctions.*;
import static org.mmarini.wheelly.model.Utils.direction;

public class GotoStatus implements EngineStatus {
    public static final String TIMEOUT_EXIT = "Timeout";
    public static final String TARGET_REACHED_EXIT = "TargetReached";
    public static final String OBSTACLE_EXIT = "Obstacle";
    private static final Logger logger = LoggerFactory.getLogger(GotoStatus.class);
    private static final double APPROACH_DISTANCE = 0.5;
    private static final double FINAL_DISTANCE = 0.2;
    private static final double FINAL_SPEED = 0.5;
    private static final double APPROACH_SPEED = 1;

    public static GotoStatus create(Point2D target, double targetDistance, long timeout) {
        return new GotoStatus(target, targetDistance, timeout);
    }

    public static GotoStatus create(Point2D target, double targetDistance) {
        return new GotoStatus(target, targetDistance, 0);
    }

    public final Point2D target;
    public final double targetDistance;
    private final long timeout;


    protected GotoStatus(Point2D target, double targetDistance, long timeout) {
        this.target = requireNonNull(target);
        this.targetDistance = targetDistance;
        this.timeout = timeout;
    }

    @Override
    public EngineStatus activate(StateMachineContext context) {
        if (timeout > 0) {
            context.put("timeout", System.currentTimeMillis() + timeout);
        } else {
            context.remove("timeout");
        }
        return this;
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ScannerMap> data, StateMachineContext context) {
        WheellyStatus wheellyStatus = data._1.value();
        RobotAsset robot = wheellyStatus.sample.robotAsset;
        double distance = robot.location.distance(target);
        // Check for target reached
        if (distance <= targetDistance) {
            return StateTransition.create(TARGET_REACHED_EXIT, context, StopStatus.STOP_COMMAND);
        }
        boolean canMove = wheellyStatus.canMoveForward;
        // Check for obstacles
        if (!canMove) {
            return StateTransition.create(OBSTACLE_EXIT, context, StopStatus.STOP_COMMAND);
        }
        // Check for timeout
        if (context.<Long>get("timeout").filter(x -> System.currentTimeMillis() >= x).isPresent()) {
            return StateTransition.create(TIMEOUT_EXIT, context, StopStatus.STOP_COMMAND);
        }

        // Check for movement
        int dirDeg = (int) round(toDegrees(direction(robot.location, target)));
        logger.debug("Robot: {}, {} m, {} DEG",
                robot.location.getX(),
                robot.location.getY(),
                robot.getDirectionDeg());
        logger.debug("Motor: {}", wheellyStatus.motors);
        logger.debug("target at: {} m, {} DEG", distance, dirDeg);

        // Check for disytance
        double isApproach = positive(distance, APPROACH_DISTANCE);
        double isFinal = and(positive(distance, FINAL_DISTANCE), not(isApproach));
        logger.debug("isApproaching: {}, isFinal: {}", isApproach, isFinal);

        double speed = defuzzy(
                FINAL_SPEED, isFinal,
                APPROACH_SPEED, isApproach,
                0, not(or(isFinal, isApproach)));

        logger.debug("speed: {}", speed);
        return StateTransition.create(STAY_EXIT, context, Tuple2.of(MoveCommand.create(dirDeg, speed), 0));
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", GotoStatus.class.getSimpleName() + "[", "]")
                .add("target=" + target)
                .add("targetDistance=" + targetDistance)
                .toString();
    }
}