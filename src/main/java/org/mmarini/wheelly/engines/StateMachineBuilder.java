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

import org.mmarini.Tuple2;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.StateMachineEngine.END_STATUS;

public class StateMachineBuilder {
    private static StateMachineBuilder builder = new StateMachineBuilder(
            Map.of(END_STATUS, StopStatus.finalStop()),
            Map.of());

    /**
     * @return
     */
    public static StateMachineBuilder create() {
        return builder;
    }

    private final Map<String, EngineStatus> states;
    private final Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> transitions;

    private StateMachineBuilder(Map<String, EngineStatus> states, Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> transitions) {
        this.states = requireNonNull(states);
        this.transitions = requireNonNull(transitions);
    }

    /**
     * @param name
     * @param status
     */
    public StateMachineBuilder addState(String name, EngineStatus status) {
        requireNonNull(name);
        requireNonNull(status);
        if (states.containsKey(name)) {
            throw new IllegalArgumentException(format("Status %s already defined", name));
        }
        Map<String, EngineStatus> newMap = new HashMap<>(states);
        newMap.put(name, status);
        return new StateMachineBuilder(newMap, transitions);
    }

    /**
     * @param from
     * @param exit
     * @param to
     * @param contextChanger
     */
    public StateMachineBuilder addTransition(String from, String exit, String to, UnaryOperator<StateMachineContext> contextChanger) {
        requireNonNull(from);
        requireNonNull(exit);
        requireNonNull(to);
        requireNonNull(contextChanger);
        if (!states.containsKey(from)) {
            throw new IllegalArgumentException(format("Status %s undefined", from));
        }
        if (!states.containsKey(to)) {
            throw new IllegalArgumentException(format("Status %s undefined", to));
        }
        Tuple2<String, String> key = Tuple2.of(from, exit);
        if (transitions.containsKey(key)) {
            throw new IllegalArgumentException(format("StateTransition %s, s already defined", from, exit));
        }
        Tuple2<String, UnaryOperator<StateMachineContext>> value = Tuple2.of(to, contextChanger);
        Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> newMap = new HashMap<>(transitions);
        newMap.put(key, value);
        return new StateMachineBuilder(states, newMap);
    }

    /**
     * @param from
     * @param exit
     * @param to
     */
    public StateMachineBuilder addTransition(String from, String exit, String to) {
        return addTransition(from, exit, to, UnaryOperator.identity());
    }

    /**
     * Returns the state machine
     */
    public StateMachineEngine build(String initialStatus) {
        if (!states.containsKey(initialStatus)) {
            throw new IllegalArgumentException(format("Status %s undefined", initialStatus));
        }

        return new StateMachineEngine(states, transitions, initialStatus);
    }
}
