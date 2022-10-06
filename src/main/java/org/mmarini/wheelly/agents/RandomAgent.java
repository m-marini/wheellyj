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

package org.mmarini.wheelly.agents;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.envs.ArraySignal;
import org.mmarini.wheelly.envs.Environment;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.io.File;
import java.util.Map;
import java.util.Random;

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
        this.random = random;
        this.state = state;
        this.actions = actions;
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        return Map.of(
                "halt", ArraySignal.create(random.nextDouble() < 0.5 ? 0 : 1),
                "direction", ArraySignal.create(random.nextInt(360) - 180),
                "speed", ArraySignal.create((random.nextInt(21) - 10) * 0.1f),
                "sensorAction", ArraySignal.create(random.nextInt(181) - 90)
        );
    }

    @Override
    public void close() {
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return actions;
    }

    @Override
    public JsonNode getSpec() {
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
    public void save(File path) {
        throw new RuntimeException("Not implemented");
    }
}
