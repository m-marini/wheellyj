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
import io.reactivex.rxjava3.disposables.Disposable;
import org.mmarini.wheelly.apis.RobotCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;


/**
 * Represents a concrete FSM state that handles the asynchronous navigation of the robot
 * along a computed path of sequential co-ordinates.
 * <p>
 * This state subscribes to a reactive stream supplying a list of target waypoints.
 * It delegates individual waypoint traversal to an internal {@link MoveState} instance, advancing the target
 * index upon each waypoint completion and monitoring tactile contact sensors to safely intercept
 * and handle collisions during the movement lifecycle.
 * </p>
 */
public class AsyncMovePathState extends AbstractContactEventState {
    private static final Logger logger = LoggerFactory.getLogger(AsyncMovePathState.class);
    private final MoveState moveState;
    private volatile List<Point2D> path;
    private int currentTargetIdx;
    private volatile Throwable error;
    private Disposable disposable;

    /**
     * Constructs an {@code AsyncMovePathState} with a specified commitment duration window.
     *
     * @param commitmentTime the length of time in milliseconds that the state must remain active
     */
    public AsyncMovePathState(long commitmentTime) {
        super(commitmentTime);
        this.moveState = new MoveState(0)
                .onContact(this::onContact)
                .onCompletion(this::onMoveCompletion);
    }

    /**
     * Initialises the state context, resetting the path tracking pointers, disposing of
     * any stale streams, and subscribing to the reactive path computation provider.
     *
     * @param ctx  the current operational context of the finite state machine
     * @param path the reactive {@link Single} stream emitting the computed list of co-ordinates
     * @throws NullPointerException if the provided context or path stream is null
     */
    public void init(EnvFSMContext ctx, Single<List<Point2D>> path) {
        super.init(ctx);
        this.currentTargetIdx = 0;
        if (disposable != null) {
            disposable.dispose();
        }
        this.path = null;
        this.error = null;
        this.currentTargetIdx = -1;
        this.disposable = path.subscribe(this::onPath,
                this::onError);
    }

    /**
     * Internal callback that intercepts a contact or collision event from the nested
     * move state and propagates it to this state container.
     *
     * @param context the current finite state machine context
     * @return the reactive {@link RobotCommands} triggered by the contact event
     */
    private RobotCommands onContact(EnvFSMContext context) {
        return triggerContact(context);
    }

    /**
     * Reactive callback invoked when the path processing stream throws an error.
     *
     * @param error the exception encountered during path calculation
     */
    private void onError(Throwable error) {
        logger.atError().setCause(error).log("Error computing path");
        this.error = error;
    }

    /**
     * Internal callback triggered when the delegated {@link MoveState} successfully reaches
     * the current waypoint target.
     * <p>
     * If the final point in the path sequence has been touched, the macro-action finishes
     * and flags completion.
     * Otherwise, it initialises the next waypoint and processes its tick.
     * </p>
     *
     * @param context the current finite state machine context
     * @return the next set of execution {@link RobotCommands}
     */
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

    /**
     * Reactive callback invoked when the path sequence is successfully computed and emitted.
     *
     * @param path the ordered list of spatial waypoint co-ordinates
     */
    private void onPath(List<Point2D> path) {
        logger.atDebug().log("Moving path {}", path);
        this.path = path;
    }

    /**
     * Processes a single periodic execution step within this state, coordinating asynchronous
     * stream readiness, path sequencing tracking, and goal termination boundaries.
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} to be dispatched to the robot hardware during this cycle
     * @throws NullPointerException if the provided context is null
     */
    @Override
    public RobotCommands tick(EnvFSMContext context) {
        if (contacted()) {
            return triggerContact(context);
        }
        if (completed()) {
            return complete(context);
        }
        if (error != null) {
            return complete(context);
        }
        List<Point2D> path = this.path;
        if (path == null) {
            // Waiting for path
            return RobotCommands.halt();
        } else if (path.isEmpty()) {
            return complete(context);
        } else if (currentTargetIdx >= 0) {
            return moveState.tick(context);
        } else {
            currentTargetIdx = 0;
            moveState.init(context, path.getFirst());
            return moveState.tick(context);
        }
    }
}