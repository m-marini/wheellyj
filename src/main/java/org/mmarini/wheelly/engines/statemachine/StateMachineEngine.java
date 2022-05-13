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
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.statemachine.EngineStatus.STAY_EXIT;
import static org.mmarini.wheelly.engines.statemachine.StopStatus.END_STATUS;

public class StateMachineEngine implements InferenceEngine {
    private static final Logger logger = LoggerFactory.getLogger(StateMachineEngine.class);

    private final Map<String, EngineStatus> states;
    private final Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> transitions;
    private StateMachineContext context;
    private String status;

    protected StateMachineEngine(Map<String, EngineStatus> states, Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> transitions, String initialStatus, StateMachineContext context) {
        this.states = requireNonNull(states);
        this.transitions = requireNonNull(transitions);
        this.status = requireNonNull(initialStatus);
        this.context = requireNonNull(context);
        logger.debug("State machine created");
    }

    @Override
    public InferenceEngine init(InferenceMonitor monitor) {
        states.get(status).activate(this.context, monitor);
        return this;
    }

    @Override
    public Tuple2<MotionComand, Integer> process(Timed<MapStatus> data, InferenceMonitor monitor) {
        StateTransition result = states.get(status).process(data, context, monitor);

        if (!STAY_EXIT.equals(result.exit)) {
            Tuple2<String, String> key = Tuple2.of(status, result.exit);
            Tuple2<String, UnaryOperator<StateMachineContext>> tx = transitions.get(key);
            if (tx == null) {
                monitor.show("Missing transition %s: %s", result.exit, status);
                monitor.show("%s: %s -> %s", result.exit, status, END_STATUS);
                status = END_STATUS;
            } else {
                Tuple2<String, UnaryOperator<StateMachineContext>> next = transitions.get(key);
                monitor.show("%s: %s -> %s", result.exit, status, next._1);
                status = next._1;
                context = next._2.apply(context);
            }
            context.setStatusName(status);
            states.get(status).activate(context, monitor);
        }
        return result.commands;
    }

}