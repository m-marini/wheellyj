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

import org.mmarini.Tuple2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class StateMachineBuilder {

    public static final StateMachineBuilder SINGLETON = new StateMachineBuilder(
            List.of(StopStatus.finalStatus()),
            Map.of(), StateMachineContext.create());

    /**
     *
     */
    public static StateMachineBuilder create() {
        return SINGLETON;
    }

    private final List<EngineStatus> states;
    private final Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> transitions;
    private final StateMachineContext context;

    private StateMachineBuilder(List<EngineStatus> states, Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> transitions, StateMachineContext context) {
        this.states = requireNonNull(states);
        this.transitions = requireNonNull(transitions);
        this.context = context;
    }

    /**
     * @param status
     */
    public StateMachineBuilder addState(EngineStatus status) {
        requireNonNull(status);
        if (containsStatus(status)) {
            throw new IllegalArgumentException(format("Status %s already defined", status.getName()));
        }
        List<EngineStatus> newList = new ArrayList<>(states);
        newList.add(status);
        return new StateMachineBuilder(newList, transitions, context);
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
        if (!containsStatus(from)) {
            throw new IllegalArgumentException(format("Status %s undefined", from));
        }
        if (!containsStatus(to)) {
            throw new IllegalArgumentException(format("Status %s undefined", to));
        }
        Tuple2<String, String> key = Tuple2.of(from, exit);
        if (transitions.containsKey(key)) {
            throw new IllegalArgumentException(format("StateTransition %s, %s already defined", from, exit));
        }
        Tuple2<String, UnaryOperator<StateMachineContext>> value = Tuple2.of(to, contextChanger);
        Map<Tuple2<String, String>, Tuple2<String, UnaryOperator<StateMachineContext>>> newMap = new HashMap<>(transitions);
        newMap.put(key, value);
        return new StateMachineBuilder(states, newMap, context);
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
        if (!containsStatus(initialStatus)) {
            throw new IllegalArgumentException(format("Status %s undefined", initialStatus));
        }
        Map<String, EngineStatus> namedStatus = states.stream().collect(Collectors.toMap(
                EngineStatus::getName,
                x -> x
        ));
        return new StateMachineEngine(namedStatus, transitions, initialStatus, context);
    }

    private boolean containsStatus(EngineStatus status) {
        return containsStatus(status.getName());
    }

    private boolean containsStatus(String name) {
        return states.stream().map(EngineStatus::getName)
                .anyMatch(name::equals);
    }

    public StateMachineBuilder setInitialContext(StateMachineContext context) {
        return new StateMachineBuilder(states, transitions, context);
    }

    public <T> StateMachineBuilder setParams(String key, T value) {
        requireNonNull(key);
        requireNonNull(value);
        context.put(key, value);
        return this;
    }
}
