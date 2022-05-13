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

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.MapStatus;
import org.mmarini.wheelly.model.WheellyStatus;

import static org.mmarini.wheelly.engines.statemachine.StateTransition.COMPLETED_TRANSITION;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.TIMEOUT_TRANSITION;

public class WaitForUnblockedStatus extends AbstractEngineStatus {

    public static final StateTransition STAY_TRANSITION = StateTransition.create(STAY_EXIT, HALT_COMMAND);

    /**
     * Returns named enegine status
     *
     * @param name the name
     */
    public static WaitForUnblockedStatus create(String name) {
        return new WaitForUnblockedStatus(name);
    }

    /**
     * Creates named enegine status
     *
     * @param name the name
     */
    protected WaitForUnblockedStatus(String name) {
        super(name);
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        WheellyStatus sample = data.value().getWheelly();
        return !sample.isBlocked() ? COMPLETED_TRANSITION
                : isExpired() ? TIMEOUT_TRANSITION : STAY_TRANSITION;
    }
}
