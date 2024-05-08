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

    record ExecutionResult(Map<String, Signal> state0, Map<String, Signal> actions, double reward,
                           Map<String, Signal> state1) {
        /**
         * Creates an execution result
         *
         * @param state1  the result state
         * @param actions the action
         * @param reward  the reward
         * @param state0  the resulting state
         */
        public ExecutionResult(Map<String, Signal> state0, Map<String, Signal> actions, double reward, Map<String, Signal> state1) {
            this.state1 = requireNonNull(state1);
            this.actions = requireNonNull(actions);
            this.reward = reward;
            this.state0 = requireNonNull(state0);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", ExecutionResult.class.getSimpleName() + "[", "]")
                    .add("state0=" + state0)
                    .add("action=" + actions)
                    .add("reward=" + reward)
                    .add("state1=" + state1)
                    .toString();
        }
    }
}
