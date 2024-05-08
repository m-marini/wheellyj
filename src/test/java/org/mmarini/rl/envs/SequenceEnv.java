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

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.Map;

public class SequenceEnv implements Environment {
    private final int numStates;
    private int currentState;

    public SequenceEnv(int numStates) {
        this.numStates = numStates;
    }

    @Override
    public void close() {
    }

    @Override
    public ExecutionResult execute(Map<String, Signal> actions) {
        int action = actions.get("output").getInt(0);
        Map<String, Signal> signals0 = getSignals();
        if (currentState == action) {
            currentState++;
            Map<String, Signal> signals1 = getSignals();
            if (currentState >= numStates - 1) {
                currentState = 0;
                return null;
            }
            return new ExecutionResult(signals0, actions, 1, signals1);
        } else {
            return new ExecutionResult(signals0, actions, 0, signals0);
        }
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return Map.of(
                "action", new IntSignalSpec(new long[]{1}, numStates)
        );
    }

    private Map<String, Signal> getSignals() {
        INDArray signals = Nd4j.zeros(numStates);
        signals.putScalar(currentState, 1f);
        return Map.of("input", new ArraySignal(signals));
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return Map.of(
                "state", new FloatSignalSpec(new long[]{numStates}, 0, 1)
        );
    }

    @Override
    public Map<String, Signal> reset() {
        currentState = 0;
        return getSignals();
    }
}
