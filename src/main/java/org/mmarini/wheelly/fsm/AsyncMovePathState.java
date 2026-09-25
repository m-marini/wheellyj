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

package org.mmarini.wheelly.fsm;

import io.reactivex.rxjava3.core.Single;
import org.mmarini.wheelly.apis.RobotCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;

public class AsyncMovePathState extends AbstractContactEventState {
    private static final Logger logger = LoggerFactory.getLogger(AsyncMovePathState.class);
    private final MoveState moveState;
    private volatile List<Point2D> path;
    private volatile int currentTargetIdx;

    /**
     * Constructs a new {@code MovePathState} with a defined commitment duration.
     * <p>
     * Internally configures the substate {@link MoveState} by linking its completion
     * and contact event listeners to this state's internal handlers.
     * </p>
     *
     * @param commitmentTime the maximum time execution threshold allotted for this state
     */
    public AsyncMovePathState(int commitmentTime) {
        super(commitmentTime);
        this.moveState = new MoveState(0)
                .onContact(this::onContact)
                .onCompletion(this::onMoveCompletion);
    }

    /**
     * Initialises the state with the current execution context and a static trajectory path.
     * <p>
     * <b>Warning:</b> No defensive deep copy of the path list is created. External mutations
     * to the injected list will alter subsequent target coordinate lookups in real-time.
     * </p>
     *
     * @param ctx  the state machine environment context
     * @param path the ordered list of 2D waypoints defining the trajectory. Must not be null.
     * @throws NullPointerException if the path parameter is null
     */
    public void init(EnvFSMContext ctx, Single<List<Point2D>> path) {
        super.init(ctx);
        this.currentTargetIdx = 0;
        this.path = null;
        path.subscribe(path1 ->
                        onPath(ctx, path1),
                err ->
                        onError(ctx, err));
    }

    /**
     * Internally handles contact or collision events intercepted from the underlying {@link MoveState}.
     * <p>
     * Forces immediate path completion, flags the failure state via {@code hasContact} to short-circuit
     * further processing, and delegates control to the configured contact handler callback.
     * </p>
     *
     * @param context the state machine environment context
     * @return the resulting robot commands to execution, defaulting to a halt command if no callback is registered
     */
    private RobotCommands onContact(EnvFSMContext context) {
        return triggerContact(context);
    }

    private void onError(EnvFSMContext ctx, Throwable error) {
        logger.atError().setCause(error).log("Error computing path");
        complete(ctx);
    }

    private RobotCommands onMoveCompletion(EnvFSMContext context) {
        if (currentTargetIdx == path.size()) {
            // final target reached
            return complete(context);
        } else {
            // go to next point
            moveState.init(context, path.get(currentTargetIdx++));
            return moveState.tick(context);
        }
    }

    private void onPath(EnvFSMContext context, List<Point2D> point2DS) {
        this.currentTargetIdx = 0;
        moveState.init(context, path.getFirst());
    }

    /**
     * Executes cyclic periodic processing logic for the path state machine.
     * <p>
     * Checks for short-circuit conditions (prior contact triggers or empty/completed configurations)
     * before delegating cyclic ticks downstream to the current segment worker.
     * </p>
     *
     * @param context the state machine environment context
     * @return the resulting action commands destined for the robot architecture
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        if (contacted()) { // Line 112: Intercepts active collision flags to short-circuit nominal execution
            return triggerContact(context);
        }
        if (completed()) {
            return complete(context);
        }
        List<Point2D> path = this.path;
        if (path == null) {
            // Waiting for path
            return RobotCommands.halt();
        }
        return moveState.tick(context);
    }
}