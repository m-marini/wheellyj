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
import org.mmarini.wheelly.apis.Angle;
import org.mmarini.wheelly.apis.MotionStatus;
import org.mmarini.wheelly.apis.RobotStatus;
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
public class MovePathState extends AbstractContactEventState<MotionStatus> {
    private static final Logger logger = LoggerFactory.getLogger(MovePathState.class);
    private final MoveState moveState;
    private volatile List<Point2D> path;
    private int currentTargetIdx;
    private volatile Throwable error;
    private Disposable disposable;

    /**
     * Constructs a {@code MovePathState} and initialises its internal nested movement state,
     * registering the respective collision and completion callbacks.
     */
    public MovePathState() {
        super();
        this.moveState = new MoveState()
                .onContact(this::onContact)
                .onCompletion(this::onMoveCompletion);
    }

    /**
     * Initialises the state context, resetting the path tracking pointers, disposing of
     * any stale streams, and subscribing to the reactive path computation provider.
     *
     * @param ctx          the current operational context of the finite state machine
     * @param pathSupplier the reactive {@link Single} stream emitting the computed list of co-ordinates
     * @throws NullPointerException if the provided context or path stream is null
     */
    public void init(EnvFSMContext ctx, Single<List<Point2D>> pathSupplier) {
        super.init(ctx);
        this.currentTargetIdx = 0;
        if (disposable != null) {
            disposable.dispose();
        }
        this.path = null;
        this.error = null;
        this.currentTargetIdx = -1;
        this.disposable = pathSupplier.subscribe(
                this::onPath,
                this::onError);
    }

    /**
     * Guards against invalid standard state initialisation by forcing the usage of the
     * target-specified initialiser.
     *
     * @param context the environment finite state machine context
     * @throws IllegalStateException always thrown to prohibit initialisation without a valid target position
     */
    @Override
    public void init(EnvFSMContext context) {
        throw new IllegalStateException("Initialization without path supplier is not allowed");
    }

    /**
     * Configures and initialises the nested movement state towards a specific spatial target.
     * <p>
     * This method evaluates the relative direction of the target point compared to the
     * robot's current heading. If the target falls within a front-facing threshold of 91 degrees,
     * a forward motion profile is selected; otherwise, a backward movement profile is applied.
     * </p>
     *
     * @param context the current operational context of the finite state machine
     * @param target  the {@link Point2D} co-ordinates of the target waypoint
     */
    private void moveTo(EnvFSMContext context, Point2D target) {
        RobotStatus robotStatus = context.worldModel().robotStatus();
        Angle targetRelDir = Angle.direction(robotStatus.location(), target)
                .sub(robotStatus.direction());
        MotionStatus status = targetRelDir.isFront(91)
                ? MotionStatus.forward(target)
                : MotionStatus.backward(target);
        moveState.init(context, status);
    }

    /**
     * Internal callback that intercepts a contact or collision event from the nested
     * move state, marks this container state as completed, and propagates the contact event.
     *
     * @param context      the current finite state machine context
     * @param defaultValue the default motion status assigned during the contact threshold
     * @return the resulting {@link MotionStatus} triggered by the contact event
     */
    private MotionStatus onContact(EnvFSMContext context, MotionStatus defaultValue) {
        defaultValue = complete(context, defaultValue);
        return triggerContact(context, defaultValue);
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
     * If the final point in the path sequence has been reached, the state completes.
     * Otherwise, it initialises navigation to the next waypoint in the queue and processes its execution tick.
     * </p>
     *
     * @param context      the current finite state machine context
     * @param defaultValue the motion status at the moment of waypoint completion
     * @return the subsequent execution {@link MotionStatus}
     */
    private MotionStatus onMoveCompletion(EnvFSMContext context, MotionStatus defaultValue) {
        if (currentTargetIdx == path.size()) {
            // final target reached
            return complete(context, defaultValue);
        } else {
            // go to next point
            moveTo(context, path.get(currentTargetIdx++));
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
     * <p>
     * This method evaluates safety conditions (such as physical contact events or computation errors)
     * to immediately halt the platform if triggered. While waiting for the path stream to emit data,
     * it maintains a safe halt instruction. Once valid co-ordinates are available, it sequentially
     * steps through each waypoint.
     * </p>
     *
     * @param context the {@link EnvFSMContext} tracking the shared operational data
     * @return the {@link MotionStatus} to be dispatched to the robot platform during this cycle
     * @throws NullPointerException if the provided context is null
     */
    public MotionStatus tick(EnvFSMContext context) {
        if (contacted()) {
            return triggerContact(context, MotionStatus.halt());
        }
        if (completed()) {
            return complete(context, MotionStatus.halt());
        }
        if (error != null) {
            return complete(context, MotionStatus.halt());
        }
        List<Point2D> path = this.path;
        if (path == null) {
            // Waiting for path
            return MotionStatus.halt();
        } else if (path.isEmpty()) {
            return complete(context, MotionStatus.halt());
        } else if (currentTargetIdx >= 0) {
            return moveState.tick(context);
        } else {
            currentTargetIdx = 0;
            moveTo(context, path.getFirst());
            return moveState.tick(context);
        }
    }
}