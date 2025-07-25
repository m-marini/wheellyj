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

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.MapStream;
import org.mmarini.rl.envs.*;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Agent that produces a random behavior
 */
public class RandomAgent implements Agent {
    /**
     * Returns the  random agent from spec
     *
     * @param root    the spec document
     * @param locator the agent spec locator
     * @param env     the environment
     */
    public static RandomAgent create(JsonNode root, Locator locator, WithSignalsSpec env) {
        long seed = locator.path("seed").getNode(root).asLong(0);
        Random random = seed > 0 ? new Random(seed) : new Random();
        return new RandomAgent(env.stateSpec(), env.actionSpec(), random);
    }

    private final Random random;
    private final Map<String, SignalSpec> state;
    private final Map<String, SignalSpec> actions;

    /**
     * Creates a random behavior agent
     *
     * @param state   the states
     * @param actions the actions
     * @param random  the random generator
     */
    public RandomAgent(Map<String, SignalSpec> state, Map<String, SignalSpec> actions, Random random) {
        this.random = requireNonNull(random);
        this.state = requireNonNull(state);
        this.actions = requireNonNull(actions);
        for (Map.Entry<String, SignalSpec> entry : actions.entrySet()) {
            String key = entry.getKey();
            SignalSpec spec = entry.getValue();
            if (!(spec instanceof IntSignalSpec)) {
                throw new IllegalArgumentException(format(
                        "Action \"%s\" must be integer (%s)",
                        key, spec.getClass().getSimpleName()
                ));
            }
            long[] shape = spec.shape();
            if (!(Arrays.equals(shape, new long[]{1}))) {
                throw new IllegalArgumentException(format(
                        "Action \"%s\" shape must be [1]] (%s)",
                        key, Arrays.toString(shape)
                ));
            }
        }
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        return MapStream.of(actions)
                .mapValues(value -> {
                    IntSignalSpec spec = ((IntSignalSpec) value);
                    int action = random.nextInt(spec.numValues());
                    return (Signal) IntSignal.create(action);
                })
                .toMap();
    }

    @Override
    public Map<String, SignalSpec> actionSpec() {
        return actions;
    }

    @Override
    public Agent alphas(Map<String, Float> alphas) {
        return null;
    }

    @Override
    public Map<String, Float> alphas() {
        return Map.of();
    }

    @Override
    public Agent backup() {
        return null;
    }

    @Override
    public int batchSize() {
        return 0;
    }

    @Override
    public void close() {
    }

    @Override
    public float eta() {
        return 0;
    }

    @Override
    public Agent eta(float eta) {
        return null;
    }

    @Override
    public RandomAgent init() {
        return this;
    }

    @Override
    public boolean isReadyForTrain() {
        return false;
    }

    @Override
    public JsonNode json() {
        return null;
    }

    @Override
    public TDNetwork network() {
        return null;
    }

    @Override
    public int numEpochs() {
        return 0;
    }

    @Override
    public int numSteps() {
        return 0;
    }

    @Override
    public RandomAgent observe(ExecutionResult result) {
        return this;
    }

    @Override
    public Flowable<Map<String, INDArray>> readKpis() {
        return Flowable.empty();
    }

    @Override
    public void save() {

    }

    @Override
    public Map<String, SignalSpec> stateSpec() {
        return state;
    }

    @Override
    public Agent trainByTrajectory(List<ExecutionResult> trajectory) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Agent trainMiniBatch(long epoch, long startStep, long numStepsParm, Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, Map<String, INDArray> actionProb0) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public List<ExecutionResult> trajectory() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Agent trajectory(List<ExecutionResult> trajectory) {
        throw new RuntimeException("Not implemented");
    }
}
