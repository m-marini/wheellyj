/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.rl.envs;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * The environment interface
 */
public interface Environment extends Closeable, WithSignalsSpec {

    /**
     * Returns the result of execution of actions
     *
     * @param actions the actions
     */
    ExecutionResult execute(Map<String, Signal> actions);

    /**
     * Returns the initial state of an episode
     */
    Map<String, Signal> reset();

    class ExecutionResult {
        public final Map<String, Signal> actions;
        public final double reward;
        public final Map<String, Signal> state0;
        public final Map<String, Signal> state1;
        public final boolean terminal;

        /**
         * Creates an execution result
         *
         * @param state1   the result state
         * @param actions  the action
         * @param reward   the reward
         * @param state0   the resulting state
         * @param terminal true if terminal state
         */
        public ExecutionResult(Map<String, Signal> state0, Map<String, Signal> actions, double reward, Map<String, Signal> state1, boolean terminal) {
            this.state1 = requireNonNull(state1);
            this.actions = requireNonNull(actions);
            this.reward = requireNonNull(reward);
            this.state0 = requireNonNull(state0);
            this.terminal = terminal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExecutionResult that = (ExecutionResult) o;
            return Double.compare(that.reward, reward) == 0 && terminal == that.terminal && actions.equals(that.actions) && state0.equals(that.state0) && state1.equals(that.state1);
        }

        public Map<String, Signal> getActions() {
            return actions;
        }

        /**
         * Returns the reward
         */
        public double getReward() {
            return reward;
        }

        /**
         * Returns the state pre-action
         */
        public Map<String, Signal> getState0() {
            return state0;
        }

        /**
         * Returns the state post action
         */
        public Map<String, Signal> getState1() {
            return state1;
        }

        public ExecutionResult setState1(Map<String, Signal> state1) {
            return new ExecutionResult(state1, actions, reward, state0, terminal);
        }

        @Override
        public int hashCode() {
            return Objects.hash(actions, reward, state0, state1, terminal);
        }

        /**
         * Returns true if terminal state
         */
        public boolean isTerminal() {
            return terminal;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ExecutionResult.class.getSimpleName() + "[", "]")
                    .add("state0=" + state0)
                    .add("action=" + actions)
                    .add("reward=" + reward)
                    .add("state1=" + state1)
                    .add("terminal=" + terminal)
                    .toString();
        }

    }
}
