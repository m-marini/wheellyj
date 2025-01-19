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

import org.mmarini.MapStream;
import org.mmarini.Tuple2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

/**
 * The deterministic sequences Markov Decision Process
 * It is defined by map of next state and reward by state and action
 *
 * @param nextState
 * @param reward
 */
public record TestSequenceMDP(int[][] nextState, double[][] reward) {

    /**
     * Returns the builder with default reward = 0
     */
    public static Builder builder() {
        return builder(0);
    }


    /**
     * Returns the builder with given default reward
     *
     * @param defaultReward the default reward
     */
    public static Builder builder(double defaultReward) {
        return new Builder(defaultReward);
    }

    /**
     * Returns the circulare sequence MDP for the given number of states
     *
     * @param numStates the number of states
     */
    public static TestSequenceMDP circularSequence(int numStates) {
        int[][] nextMap = new int[numStates][numStates];
        double[][] rewardMap = new double[numStates][numStates];
        for (int i = 0; i < numStates; i++) {
            for (int j = 0; j < numStates; j++) {
                nextMap[i][j] = i == j
                        ? (i + 1) % numStates
                        : i;
                rewardMap[i][j] = i == j
                        ? 1 : -1;
            }
        }
        return new TestSequenceMDP(nextMap, rewardMap);
    }

    /**
     * Returns the circular sequence MDP for the given sequence of actions
     *
     * @param numActions the number of actions
     * @param sequence   the sequence of action
     */
    public static TestSequenceMDP create(int numActions, int... sequence) {
        int numStates = requireNonNull(sequence).length;
        int[][] nextMap = new int[numStates][numActions];
        double[][] rewardMap = new double[numStates][numActions];
        for (int i = 0; i < sequence.length; i++) {
            for (int j = 0; j < sequence[i]; j++) {
                nextMap[i][j] = i == j
                        ? (i + 1) % numStates
                        : i;
                rewardMap[i][j] = i == j
                        ? 1 : -1;
            }
        }
        return new TestSequenceMDP(nextMap, rewardMap);
    }

    /**
     * Creates the sequence MDP
     *
     * @param nextState the next state map
     * @param reward    the reward map
     */
    public TestSequenceMDP(int[][] nextState, double[][] reward) {
        this.nextState = requireNonNull(nextState);
        this.reward = requireNonNull(reward);
        int numStates = nextState.length;
        if (numStates == 0) {
            throw new IllegalArgumentException("Illegal number of states");
        }
        if (numStates != reward.length) {
            throw new IllegalArgumentException("Illegal number of states in reward map");
        }
        for (int i = 0; i < numStates; i++) {
            requireNonNull(nextState[i]);
            requireNonNull(reward[i]);
        }
        int numActions = nextState[0].length;
        if (numActions == 0) {
            throw new IllegalArgumentException("Illegal number of actions");
        }
        for (int i = 0; i < numStates; i++) {
            if (nextState[i].length != numActions) {
                throw new IllegalArgumentException("Illegal number of actions in next state map");
            }
            for (int j = 0; j < nextState[i].length; j++) {
                if (nextState[i][j] < 0 || nextState[i][j] >= numStates) {
                    throw new IllegalArgumentException("Illegal next state map");
                }
            }
            if (reward[i].length != numActions) {
                throw new IllegalArgumentException("Illegal number of actions in reward map");
            }
        }
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
     * Returns the action specification
     */
    public Map<String, SignalSpec> actionSpec() {
        return Map.of("action", new IntSignalSpec(new long[]{1}, numActions()));
    }

    /**
     * Returns the next state
     *
     * @param state  the initial state
     * @param action the action
     */
    public int next(int state, int action) {
        return nextState[state][action];
    }

    /**
     * Returns the number of states
     */
    public int numActions() {
        return nextState[0].length;
    }

    /**
     * Returns the number of states
     */
    public int numStates() {
        return nextState.length;
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
        return reward[state][action];
    }

    /**
     * Returns signal specification
     */
    public Map<String, SignalSpec> signalSpec() {
        return Map.of("input", new IntSignalSpec(new long[numStates()], 2));
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

    /**
     * Returns the trajectory for the given initial state and action sequence
     *
     * @param initialState the initial state
     * @param actions      the actions
     */
    public List<Environment.ExecutionResult> trajectory(int initialState, int... actions) {
        List<Environment.ExecutionResult> result = new ArrayList<>();
        int state = initialState;
        for (int action : actions) {
            int next = next(state, action);
            double reward = reward(state, action);
            result.add(new Environment.ExecutionResult(state(state), action(action), reward, state(next)));
            state = next;
        }
        return result;
    }


    /**
     * Returns the trajectory for the given initial state and action sequence
     *
     * @param numStep      the number of steps
     * @param initialState the initial state
     * @param act          the action function
     */
    public List<Environment.ExecutionResult> trajectory(int numStep, int initialState, IntUnaryOperator act) {
        List<Environment.ExecutionResult> result = new ArrayList<>();
        int state = initialState;
        for (int i = 0; i < numStep; i++) {
            int action = act.applyAsInt(state);
            int next = next(state, action);
            double reward = reward(state, action);
            result.add(new Environment.ExecutionResult(state(state), action(action), reward, state(next)));
            state = next;
        }
        return result;
    }

    public static class Builder {
        private final Map<Tuple2<Integer, Integer>, Tuple2<Integer, Double>> transition;
        private final double defaultReward;

        public Builder(double defaultReward) {
            this.defaultReward = defaultReward;
            this.transition = new HashMap<>();
        }

        /**
         * Returns the builder with new transition
         *
         * @param startState  the start state
         * @param action      the action
         * @param targetState target state
         * @param reward      the reward
         */
        public Builder add(int startState, int action, int targetState, double reward) {
            if (startState < 0) {
                throw new IllegalArgumentException("Illegal start state");
            }
            if (action < 0) {
                throw new IllegalArgumentException("Illegal action");
            }
            if (targetState < 0) {
                throw new IllegalArgumentException("Illegal target state");
            }
            transition.put(Tuple2.of(startState, action), Tuple2.of(targetState, reward));
            return this;
        }

        public TestSequenceMDP build() {
            int numActions = MapStream.of(transition).keys()
                    .mapToInt(Tuple2::getV2)
                    .max()
                    .orElseThrow() + 1;
            int numStates = MapStream.of(transition).tuples()
                    .flatMapToInt(t -> IntStream.of(t._1._1, t._2._1))
                    .max()
                    .orElseThrow() + 1;
            int[][] nextMap = new int[numStates][numActions];
            double[][] reward = new double[numStates][numActions];
            for (int i = 0; i < numStates; i++) {
                for (int j = 0; j < numActions; j++) {
                    nextMap[i][j] = i;
                    reward[i][j] = defaultReward;
                }
            }
            for (Map.Entry<Tuple2<Integer, Integer>, Tuple2<Integer, Double>> entry : transition.entrySet()) {
                int start = entry.getKey()._1;
                int action = entry.getKey()._2;
                nextMap[start][action] = entry.getValue()._1;
                reward[start][action] = entry.getValue()._2;
            }
            return new TestSequenceMDP(nextMap, reward);
        }
    }
}
