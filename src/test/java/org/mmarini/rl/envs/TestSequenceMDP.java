/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.envs;

import java.util.Map;
import java.util.stream.IntStream;

/**
 * The deterministic sequences Markov Decision Process
 * It is defined by number of action and the correct action sequence by state
 *
 * @param numActions the number of actions
 * @param sequence   the sequence of action
 * @param signalSpec the signal spec
 * @param actionSpec the action spec
 */
public record TestSequenceMDP(int numActions, int[] sequence, Map<String, SignalSpec> signalSpec,
                              Map<String, SignalSpec> actionSpec) {
    /**
     * Returns the MDP for the given sequence of actions
     *
     * @param numActions the number of actions
     * @param sequence   the sequence of action
     */
    public static TestSequenceMDP create(int numActions, int... sequence) {
        Map<String, SignalSpec> signalSpec = Map.of("input", new IntSignalSpec(new long[sequence.length], 2));
        Map<String, SignalSpec> actionSpec = Map.of("action", new IntSignalSpec(new long[]{1}, numActions));
        return new TestSequenceMDP(numActions, sequence, signalSpec, actionSpec);
    }

    /**
     * Returns the MDP for a number of states
     *
     * @param numStates the number of states
     */
    public static TestSequenceMDP sequence(int numStates) {
        return create(numStates, IntStream.range(0, numStates).toArray());
    }

    /**
     * Returns the action signal for the action
     *
     * @param action action
     */
    public Map<String, Signal> action(int action) {
        return Map.of("action", IntSignal.create(action));
    }

    /**
     * Returns the next state
     *
     * @param state  the initial state
     * @param action the action
     */
    public int next(int state, int action) {
        return action == sequence[state] ? (state + 1) % numStates() : state;
    }

    /**
     * Returns the number of states
     */
    public int numStates() {
        return sequence.length;
    }

    /**
     * Returns the result
     *
     * @param state  the initial state
     * @param action the action
     */
    public Environment.ExecutionResult result(int state, int action) {
        int next = next(state, action);
        double reward = reward(state, action);
        return new Environment.ExecutionResult(state(state), action(action), reward, state(next));
    }

    /**
     * Returns the next state
     *
     * @param state  the initial state
     * @param action the action
     */
    public double reward(int state, int action) {
        return action == sequence[state] ? 1 : -1;
    }

    /**
     * Returns the signal state for the state
     *
     * @param state the state
     */
    public Map<String, Signal> state(int state) {
        int[] data = new int[numStates()];
        data[state] = 1;
        return Map.of("input", new IntSignal(new long[]{numStates()}, data));
    }
}
