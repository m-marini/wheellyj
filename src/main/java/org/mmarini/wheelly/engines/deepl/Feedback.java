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

package org.mmarini.wheelly.engines.deepl;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Objects;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;

/**
 * The feedback of reinforcement learning
 */
public class Feedback {

    /**
     * Creates a feedback
     *
     * @param s0       signals at t0
     * @param action   action
     * @param reward   reward
     * @param s1       signals at t1
     * @param interval time interval between s0 and s1
     */
    public static Feedback create(INDArray s0, INDArray action, double reward, INDArray s1, double interval) {
        return new Feedback(s0, action, reward, s1, interval);
    }

    private final INDArray s0;
    private final INDArray s1;
    private final INDArray action;
    private final double reward;
    private final double interval;

    /**
     * Creates a feedback
     *
     * @param s0       signals at t0
     * @param action   action
     * @param reward   reward
     * @param s1       signals at t1
     * @param interval time interval between s0 and s1
     */
    protected Feedback(INDArray s0, INDArray action, double reward, INDArray s1, double interval) {
        this.s0 = requireNonNull(s0);
        this.s1 = requireNonNull(s1);
        this.action = requireNonNull(action);
        this.reward = reward;
        this.interval = interval;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Feedback feedback = (Feedback) o;
        return Double.compare(feedback.reward, reward) == 0 && Double.compare(feedback.interval, interval) == 0 && s0.equals(feedback.s0) && s1.equals(feedback.s1) && action.equals(feedback.action);
    }

    public INDArray getAction() {
        return action;
    }

    public double getInterval() {
        return interval;
    }

    public double getReward() {
        return reward;
    }

    public INDArray getS0() {
        return s0;
    }

    public INDArray getS1() {
        return s1;
    }

    @Override
    public int hashCode() {
        return Objects.hash(s0, s1, action, reward, interval);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Feedback.class.getSimpleName() + "[", "]")
                .add("s0=" + s0)
                .add("action=" + action)
                .add("reward=" + reward)
                .add("s1=" + s1)
                .toString();
    }
}
