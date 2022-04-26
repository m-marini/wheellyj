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
import org.mmarini.wheelly.model.AltCommand;
import org.mmarini.wheelly.model.MotionComand;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;

public class StopStatus implements EngineStatus {
    public static final String TIMEOUT_EXIT = "TimeoutExit";
    public static final Tuple2<MotionComand, Integer> STOP_COMMAND = Tuple2.of(AltCommand.create(), 0);

    private static final StopStatus finalStop = new StopStatus(0);

    public static StopStatus create(long timeout) {
        return new StopStatus(timeout);
    }

    public static StopStatus finalStop() {
        return finalStop;
    }

    private final long timeout;

    protected StopStatus(long timeout) {
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
        return (context.<Long>get("timeout").filter(x -> System.currentTimeMillis() >= x).isPresent())
                ? StateTransition.create(TIMEOUT_EXIT, context, STOP_COMMAND)
                : StateTransition.create(STAY_EXIT, context, STOP_COMMAND);
    }
}
