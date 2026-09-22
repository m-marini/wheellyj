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

import org.mmarini.wheelly.apis.RobotCommands;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * State that manages the movement of the robot along a multipoint path trajectory.
 * <p>
 * This state coordinates a sequential trajectory using a nested {@link MoveState}
 * to handle individual path segments. The path is injected and bound directly
 * during the state initialisation phase.
 * </p>
 *
 * <p><b>Lifecycle &amp; Event Flow:</b></p>
 * <ul>
 *   <li><b>Initialisation:</b> The path reference is set in {@link #init(EnvFSMContext, List)}.
 *       If the path is empty, execution completes immediately.</li>
 *   <li><b>Nominal Flow:</b> As each segment completes, {@link #onMoveCompletion(EnvFSMContext)}
 *       advances the target index and chains execution to the next point.</li>
 *   <li><b>Exception Flow:</b> If a collision occurs, {@link #onContact(EnvFSMContext)}
 *       marks the execution as completed and permanently halts or diverts the robot via a registered callback.</li>
 * </ul>
 *
 * @see MoveState
 * @see AbstractCommitmentState
 */
public class MovePathState extends AbstractCommitmentState {
    private final MoveState moveState;
    private Function<EnvFSMContext, RobotCommands> onCompletion;
    private Function<EnvFSMContext, RobotCommands> onContact;
    private List<Point2D> path;
    private int currentTargetIdx;
    private boolean hasContact;

    /**
     * Constructs a new {@code MovePathState} with a defined commitment duration.
     * <p>
     * Internally configures the substate {@link MoveState} by linking its completion
     * and contact event listeners to this state's internal handlers.
     * </p>
     *
     * @param commitmentTime the maximum time execution threshold allotted for this state
     */
    public MovePathState(int commitmentTime) {
        super(commitmentTime);
        this.moveState = new MoveState(0)
                .onCompletion(this::onMoveCompletion)
                .onContact(this::onContact);
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
    public void init(EnvFSMContext ctx, List<Point2D> path) {
        super.init(ctx);
        this.path = requireNonNull(path);
        this.currentTargetIdx = 0;
        this.hasContact = false; // Line 58: Resets contact flag for the new execution cycle
        if (path.isEmpty()) {
            complete();
        } else {
            moveState.init(ctx, path.getFirst());
        }
    }

    /**
     * Customises the state by assigning a callback function for successful completion.
     *
     * @param callback the function to execute upon reaching the destination
     * @return this state instance to allow method chaining
     */
    public MovePathState onCompletion(Function<EnvFSMContext, RobotCommands> callback) {
        this.onCompletion = callback;
        return this;
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
        complete();
        this.hasContact = true; // Line 79: Latches the contact state to trigger immediate abort procedures
        return onContact != null
                ? onContact.apply(context)
                : RobotCommands.halt();
    }

    /**
     * Customises the state by assigning a callback function for contact or obstacle events.
     *
     * @param callback the function to execute if a contact is detected
     * @return this state instance to allow method chaining
     */
    public MovePathState onContact(Function<EnvFSMContext, RobotCommands> callback) {
        this.onContact = callback;
        return this;
    }

    /**
     * Internally processes segment transitions triggered by the underlying {@link MoveState}.
     * <p>
     * Increments the path index to route toward successive coordinates. If all targets
     * have been exhausted, finalises the entire trajectory.
     * </p>
     *
     * @param context the state machine environment context
     * @return the robot commands issued by the next step or termination callback
     */
    private RobotCommands onMoveCompletion(EnvFSMContext context) {
        if (currentTargetIdx == path.size()) {
            // final target reached
            complete();
            return onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        } else {
            // go to next point
            moveState.init(context, path.get(currentTargetIdx++));
            return moveState.tick(context);
        }
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
        if (hasContact) { // Line 112: Intercepts active collision flags to short-circuit nominal execution
            return onContact != null
                    ? onContact.apply(context)
                    : RobotCommands.halt();
        }
        if (path.isEmpty() || completed()) {
            complete();
            return onCompletion != null
                    ? onCompletion.apply(context)
                    : RobotCommands.halt();
        }
        return moveState.tick(context);
    }
}