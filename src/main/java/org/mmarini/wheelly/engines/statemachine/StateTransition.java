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

import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.HaltCommand;
import org.mmarini.wheelly.model.MotionCommand;
import org.mmarini.wheelly.model.MoveCommand;
import org.mmarini.wheelly.model.WheellyStatus;

import java.awt.geom.Point2D;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.mmarini.wheelly.engines.statemachine.EngineStatus.*;

public class StateTransition {
    public final static StateTransition OBSTACLE_TRANSITION = create(OBSTACLE_EXIT, HALT_COMMAND);
    public final static StateTransition BLOCKED_TRANSITION = create(BLOCKED_EXIT, HALT_COMMAND);
    public final static StateTransition COMPLETED_TRANSITION = create(COMPLETED_EXIT, HALT_COMMAND);
    public final static StateTransition TIMEOUT_TRANSITION = create(TIMEOUT_EXIT, HALT_COMMAND);

    /**
     * @param exit     exit code
     * @param commands commands
     */
    public static StateTransition create(String exit, Tuple2<MotionCommand, Integer> commands) {
        return new StateTransition(exit, commands);
    }

    public static StateTransition createHalt(String exit, int sensor) {
        return StateTransition.create(exit, Tuple2.of(HaltCommand.create(), sensor));
    }

    public static StateTransition createHalt(String exit, WheellyStatus status, Point2D target) {
        int direction = min(max(status.getRobotRelativeDeg(target), -90), 90);
        return StateTransition.create(exit, Tuple2.of(HaltCommand.create(), direction));
    }

    public static StateTransition createMove(String exit, WheellyStatus status, Point2D target, double speed) {
        int direction = status.getRobotDeg(target);
        int sensor = min(max(status.getRobotRelativeDeg(target), -90), 90);

        return StateTransition.create(exit, Tuple2.of(MoveCommand.create(direction, speed), sensor));
    }

    public static StateTransition createMove(String exit, int direction, double speed, int sensor) {
        return StateTransition.create(exit, Tuple2.of(MoveCommand.create(direction, speed), sensor));
    }

    public final Tuple2<MotionCommand, Integer> commands;
    public final String exit;

    /**
     * @param exit     exit code
     * @param commands commands
     */
    protected StateTransition(String exit, Tuple2<MotionCommand, Integer> commands) {
        this.commands = commands;
        this.exit = exit;
    }
}
