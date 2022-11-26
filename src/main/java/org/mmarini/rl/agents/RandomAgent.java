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
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.objectProperties;

/**
 * Agent that produces a random behavior
 */
public class RandomAgent implements Agent {
    private static final Validator RANDOM_AGENT_SPEC = objectProperties(Map.of("seed", Validator.positiveInteger()));

    /**
     * Returns the  random agent from spec
     *
     * @param root    the spec document
     * @param locator the agent spec locator
     * @param env     the environment
     */
    public static RandomAgent create(JsonNode root, Locator locator, Environment env) {
        RANDOM_AGENT_SPEC.apply(locator).accept(root);
        long seed = locator.path("seed").getNode(root).asLong(0);
        Random random = seed > 0 ? new Random(seed) : new Random();
        return new RandomAgent(env.getState(), env.getActions(), random);
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
            long[] shape = spec.getShape();
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
        return Tuple2.stream(actions).map(t -> {
            IntSignalSpec spec = ((IntSignalSpec) t._2);
            int action = random.nextInt(spec.getNumValues());
            return Tuple2.of(t._1, (Signal) IntSignal.create(action));
        }).collect(Tuple2.toMap());
    }

    @Override
    public void close() {
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return actions;
    }

    @Override
    public JsonNode getJson() {
        return null;
    }

    @Override
    public Map<String, SignalSpec> getState() {
        return state;
    }

    @Override
    public void observe(Environment.ExecutionResult result) {
    }

    @Override
    public Flowable<Map<String, INDArray>> readKpis() {
        return Flowable.empty();
    }

    @Override
    public void save(File path) {
        throw new RuntimeException("Not implemented");
    }
}