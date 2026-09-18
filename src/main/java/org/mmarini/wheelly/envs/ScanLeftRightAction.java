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

package org.mmarini.wheelly.envs;

import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;

import static java.util.Objects.requireNonNull;


/**
 * An action that scans the robot's head through a sequence of angles
 * from left to right.
 *
 * <p>The action progressively move the
 * head to each angle specified by {@code headDeg}. Each position is
 * maintained for {@code scanInterval} milliseconds before advancing
 * to the next one.</p>
 *
 * <p>The action terminates when the last head position has been reached
 * but not before the commitment interval expires.</p>
 *
 * @author Marco Marini
 */
public class ScanLeftRightAction extends AbstractCommitmentAction {

    /**
     * The time interval between two consecutive scan positions.
     */
    private final long scanInterval;
    /**
     * The sequence of head angles, in degrees, used during the scan.
     */
    private final int[] headDeg;
    private boolean completed;
    /**
     * The robot time at which the current scan step started.
     */
    private long startStepTime;
    /**
     * The index of the current scan position in {@link #headDeg}.
     *
     * <p>A value of {@code -1} indicates that the action has not
     * been initialized yet.</p>
     */
    private int currentStepIndex;

    /**
     * Creates a left-to-right head scanning action.
     *
     * @param commitmentInstant the instant at which the action commitment
     *                          expires
     * @param scanInterval      the time interval, in milliseconds, between
     *                          consecutive scan positions
     * @param headDeg           the sequence of head angles, in degrees, to scan
     * @throws NullPointerException     if {@code headDeg} is {@code null}
     * @throws IllegalArgumentException if {@code headDeg} is empty
     */
    public ScanLeftRightAction(long commitmentInstant, long scanInterval, int... headDeg) {
        super(commitmentInstant);
        this.headDeg = requireNonNull(headDeg);
        if (headDeg.length < 1) {
            throw new IllegalArgumentException("headDeg must have at least one element");
        }
        this.scanInterval = scanInterval;
        currentStepIndex = -1;
    }

    @Override
    public boolean completed() {
        return completed;
    }

    /**
     * Executes the current scan step.
     *
     * <p>On the first invocation, the scan is initialized at the first
     * angle. Once the configured scan interval has elapsed, the action
     * advances to the next angle. After the last angle has been reached,
     * the context is requested to proceed to the next action.</p>
     *
     * <p>The robot is halted while its head is positioned at the current
     * scan angle.</p>
     *
     * @param context the macro-action execution context
     * @param state   the current world model
     * @return a halt command with the current head angle
     */
    @Override
    protected RobotCommands executeAction(MacroActionContext context, WorldModel state) {
        long time = state.robotStatus().robotTime();
        if (currentStepIndex == -1) {
            // Initialise current action
            currentStepIndex = 0;
            startStepTime = time;
        }
        if (time >= startStepTime + scanInterval) {
            // Scan interval elapsed
            if (currentStepIndex >= headDeg.length - 1) {
                // Reached end of scan
                completed = true;
                context.requestNextAction();
            } else {
                // Next scan
                currentStepIndex++;
                startStepTime = time;
            }
        }
        // Handles commitment interval
        if (expired()) {
            context.requestNextAction();
        }
        return RobotCommands.halt(headDeg[currentStepIndex]);
    }
}
