/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.ToIntFunction;

import static java.lang.Math.max;
import static java.lang.String.format;

/**
 * Markov Decision process
 */
public record MDP(double[][][] prob, double[][][] reward) {

    /**
     * Returns random integer for the given the probability distribution
     *
     * @param prob   the probability distribution
     * @param random the random generator
     */
    private static int nextRandom(double[] prob, Random random) {
        double x = random.nextDouble();
        double cp = 0;
        for (int i = 0; i < prob.length - 1; i++) {
            cp += prob[i];
            if (x < cp) {
                return i;
            }
        }
        return prob.length - 1;
    }

    /**
     * Returns the sequence MDP
     *
     * @param numStates the number of states
     */
    public static MDP sequence(int numStates) {
        double[][][] prob = new double[numStates][numStates][numStates];
        double[][][] reward = new double[numStates][numStates][numStates];
        for (int i = 0; i < numStates; i++) {
            for (int j = 0; j < numStates; j++) {
                for (int k = 0; k < numStates; k++) {
                    prob[i][j][k] = j == i && k == (i + 1) % numStates
                            ? 1
                            : j != i && k == i
                              ? 1 : 0;
                }
                if (i == j) {
                    reward[i][j][(i + 1) % numStates] = 1;
                }
            }
        }
        return new MDP(prob, reward);
    }

    /**
     * Returns the action signals
     *
     * @param action the action index
     */
    public Map<String, Signal> action(int action) {
        return Map.of("action", IntSignal.create(new long[]{1, 1}, action));
    }

    /**
     *
     * Returns the action specification
     */
    public Map<String, SignalSpec> actionSpec() {
        return Map.of("action", new IntSignalSpec(new long[]{1, 1}, numActions()));
    }

    /**
     * Interact the mdp with agent policy
     * Returns the next state and the execution result
     *
     * @param state     the initial state
     * @param actionFun the policy
     * @param random    the mdp random generator
     */
    public Tuple2<Integer, ExecutionResult> interact(int state, ToIntFunction<Map<String, Signal>> actionFun, Random random) {
        Map<String, Signal> s0Signal = state(state);
        int action = actionFun.applyAsInt(s0Signal);
        Map<String, Signal> aSignal = action(action);
        int s1 = nextState(state, action, random);
        double reward = reward(state, action, s1);
        Map<String, Signal> s1Signal = state(s1);
        return Tuple2.of(s1, new ExecutionResult(s0Signal, aSignal, reward, s1Signal));
    }

    /**
     * Returns the next random state for the give state,action
     *
     * @param state  state index
     * @param action action index
     * @param random the random number generator
     */
    public int nextState(int state, int action, Random random) {
        return nextRandom(probability(state, action), random);
    }

    /**
     *
     * Returns the number of available actions
     */
    public int numActions() {
        return prob.length > 0
                ? prob[0].length
                : 0;
    }

    /**
     *
     * Returns the number of available states
     */
    public int numStates() {
        return prob.length;
    }

    /**
     * Returns the transition probability distribution
     *
     * @param state  the initial state index
     * @param action the action index
     */
    public double[] probability(int state, int action) {
        return prob[state][action];
    }

    /**
     * Returns the reward of transition
     *
     * @param state0 the initial state index
     * @param action the action index
     * @param state1 the final state index
     */
    public double reward(int state0, int action, int state1) {
        return reward[state0][action][state1];
    }

    /**
     * Returns the state signals
     *
     * @param state the state index
     */
    public Map<String, Signal> state(int state) {
        int[] values = new int[numActions()];
        values[state] = 1;
        return Map.of("state", IntSignal.create(new long[]{1, numStates()}, values));
    }

    /**
     *
     * Returns the state specification
     */
    public Map<String, SignalSpec> stateSpec() {
        return Map.of("state", new IntSignalSpec(new long[]{1, numStates()}, 2));
    }

    public static class Builder {
        private final List<Transition> transitions;

        public Builder() {
            this.transitions = new ArrayList<>();
        }

        public Builder add(int state0, int action, int state1, double preference, double reward) {
            return add(new Transition(state0, action, state1, preference, reward));
        }

        public Builder add(Transition transition) {
            transitions.add(transition);
            return this;
        }

        public MDP build() {
            // number of states
            int n = transitions.stream()
                    .mapToInt(t -> max(t.state0, t.state1) + 1)
                    .max()
                    .orElse(0);
            // number of actions
            int m = transitions.stream()
                    .mapToInt(t -> t.action() + 1)
                    .max()
                    .orElse(0);
            double[][][] prob = new double[n][m][n];
            double[][][] reward = new double[n][m][n];
            for (Transition transition : transitions) {
                prob[transition.state0][transition.action][transition.state1] = transition.preference;
                reward[transition.state0][transition.action][transition.state1] = transition.reward;
            }
            // Normalise probabilities
            for (int i = 0; i < prob.length; i++) {
                for (int j = 0; j < prob[i].length; j++) {
                    double t = 0;
                    for (int k = 0; k < prob[i][j].length; k++) {
                        t += prob[i][j][k];
                    }
                    if (t > 0) {
                        for (int k = 0; k < prob[i][j].length; k++) {
                            prob[i][j][k] /= t;
                        }
                    }
                }
            }
            return new MDP(prob, reward);
        }
    }

    public record Transition(int state0, int action, int state1, double preference, double reward) {
        public Transition {
            if (state0 < 0) {
                throw new IllegalArgumentException(format("Wrong state0 %d", state0));
            }
            if (state1 < 0) {
                throw new IllegalArgumentException(format("Wrong state1 %d", state1));
            }
            if (action < 0) {
                throw new IllegalArgumentException(format("Wrong action %d", action));
            }
            if (preference < 0) {
                throw new IllegalArgumentException(format("Wrong preference %f", preference));
            }
        }
    }

}
