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

package org.mmarini.wheelly.envs;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * The environment interface
 */
public interface Environment extends Closeable {

    /**
     * Returns the result of execution of actions
     *
     * @param actions the actions
     */
    ExecutionResult execute(Map<String, Signal> actions);

    /**
     * Returns the actions specification
     */
    Map<String,SignalSpec> getActions();

    /**
     * Returns the state specification
     */
    Map<String,SignalSpec> getState();

    /**
     * Returns the initial state of an episode
     */
    Map<String, Signal> reset();

    class ExecutionResult {
        public final float reward;
        public final Map<String, Signal> state;
        public final boolean terminal;

        /**
         * Creates an execution result
         *
         * @param state    the result state
         * @param reward   the reward
         * @param terminal true if terminal state
         */
        public ExecutionResult(Map<String, Signal> state, float reward, boolean terminal) {
            this.state = requireNonNull(state);
            this.reward = reward;
            this.terminal = terminal;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExecutionResult that = (ExecutionResult) o;
            return Float.compare(that.reward, reward) == 0 && terminal == that.terminal && state.equals(that.state);
        }

        /**
         * Returns the reward
         */
        public float getReward() {
            return reward;
        }

        /**
         * Returns the state
         */
        public Map<String, Signal> getState() {
            return state;
        }

        @Override
        public int hashCode() {
            return Objects.hash(reward, state, terminal);
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
                    .add("state=" + state)
                    .add("reward=" + reward)
                    .add("terminal=" + terminal)
                    .toString();
        }

    }
}
