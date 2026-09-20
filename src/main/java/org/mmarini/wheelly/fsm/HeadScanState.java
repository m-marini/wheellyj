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

import static java.util.Objects.requireNonNull;

/**
 * Represents a concrete FSM state that performs a sequential scan by rotating
 * the robot's head through an array of angular degrees.
 * <p>
 * This state transitions the head through multiple predefined angles, holding each
 * orientation for a fixed scan interval. It tracks the step execution and, upon
 * completion of the final angular target, flags itself as complete and requests
 * the next macro-action from the inference framework to coordinate subsequent <b>behaviour</b>.
 * </p>
 */
public class HeadScanState extends AbstractCommitmentState {

    /**
     * The temporal duration in milliseconds allocated to each individual scan step.
     */
    private final long scanInterval;

    /**
     * The array of target angles in degrees through which the head will rotate.
     */
    private int[] headDeg;

    /**
     * The robot timestamp recorded at the beginning of the current step interval.
     */
    private long startStepTime;

    /**
     * The index pointing to the active target angle within the scan sequence.
     */
    private int currentStepIndex;

    /**
     * Constructs a {@code HeadScanState} with the specified commitment duration
     * and step interval duration.
     *
     * @param commitmentDuration the length of time in milliseconds that the state must remain active
     * @param scanInterval       the time duration allocated to each individual angle look
     */
    public HeadScanState(long commitmentDuration, long scanInterval) {
        super(commitmentDuration);
        this.scanInterval = scanInterval;
        currentStepIndex = -1;
    }

    /**
     * Initialises the scanning sequence with the provided target head angles, setting up
     * step parameters and tracking timers.
     * <p>
     * This method resets the operational tracking markers, validates the target orientation
     * parameters, and anchors the initial step timestamp using the current timeline of the
     * robot status to <b>optimise</b> sequencing steps.
     * </p>
     *
     * @param context the {@link EnvironmentFSMContext} tracking the shared operational data
     * @param headDeg the sequence of target head directions in degrees, must contain at least one element
     * @throws NullPointerException     if the provided context or {@code headDeg} array is null
     * @throws IllegalArgumentException if the {@code headDeg} array contains no elements
     */
    public void init(EnvironmentFSMContext context, int... headDeg) {
        super.init(context);
        this.headDeg = requireNonNull(headDeg);
        if (headDeg.length < 1) {
            throw new IllegalArgumentException("headDeg must have at least one element");
        }
        currentStepIndex = 0;
        startStepTime = context.worldModel().robotStatus().robotTime();
    }

    /**
     * Executes the sequential scanning logic for the current execution tick.
     * <p>
     * This method monitors the elapsed robot time against the active step marker. When the interval
     * elapses, it shifts the array index to the subsequent target angle. Once the final step expires,
     * it invokes {@link #complete()}, flags a request to trigger macro-action inference at the
     * subsequent clock tick, and returns a general halt instruction.
     * </p>
     *
     * @param context the {@link EnvironmentFSMContext} tracking the shared operational data
     * @return the {@link RobotCommands} restricting execution to the active head target angle,
     *         or a stationary halt profile upon sequence completion
     * @throws NullPointerException if the provided context is null
     */
    @Override
    public RobotCommands tick(EnvironmentFSMContext context) {
        long time = context.worldModel().robotStatus().robotTime();
        if (time >= startStepTime + scanInterval) {
            // Scan interval elapsed
            if (currentStepIndex < headDeg.length - 1) {
                // Next scan
                currentStepIndex++;
                startStepTime = time;
            } else {
                complete();
                context.requestNextAction();
                return RobotCommands.halt();
            }
        }
        // Handles commitment interval
        return RobotCommands.halt(headDeg[currentStepIndex]);
    }
}