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
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.processors.InputProcessor;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.rng.Random;

import java.io.File;
import java.util.Map;

/**
 * Transpiler of simplified agent specification to internal agent specification
 */
public class AgentTranspiler {
    private final JsonNode spec;
    private final Map<String, SignalSpec> stateSpec;
    private final Map<String, SignalSpec> actionsSpec;
    private final Locator locator;
    private final Random random;
    private final File path;
    private final int savingIntervalStep;
    private float rewardAlpha;
    private TDNetwork policy;
    private TDNetwork critic;
    private float policyAlpha;
    private float criticAlpha;
    private float lambda;
    private InputProcessor processor;

    public AgentTranspiler(JsonNode spec,
                           Locator locator,
                           File path,
                           int savingIntervalStep,
                           Map<String, SignalSpec> stateSpec,
                           Map<String, SignalSpec> actionsSpec,
                           Random random) {
        this.spec = spec;
        this.stateSpec = stateSpec;
        this.actionsSpec = actionsSpec;
        this.path = path;
        this.savingIntervalStep = savingIntervalStep;
        this.locator = locator;
        this.random = random;
    }


    public TDAgent build() {
        parse();
        return new TDAgent(stateSpec, actionsSpec, 0,
                rewardAlpha, policyAlpha, criticAlpha, lambda,
                policy, critic, processor,
                random, path, savingIntervalStep);
    }

    void parse() {
        this.rewardAlpha = (float) locator.path("rewardAlpha").getNode(spec).asDouble();
        this.policyAlpha = (float) locator.path("policyAlpha").getNode(spec).asDouble();
        this.criticAlpha = (float) locator.path("criticAlpha").getNode(spec).asDouble();
        this.lambda = (float) locator.path("lambda").getNode(spec).asDouble();
        this.processor = !locator.path("inputProcess").getNode(spec).isMissingNode()
                ? InputProcessor.create(spec, locator.path("inputProcess"), this.stateSpec)
                : null;
        Map<String, SignalSpec> postProcSpec = processor != null ? processor.getSpec() : stateSpec;
        Map<String, Long> stateSizes = TDAgent.getStateSizes(postProcSpec);
        this.policy = new NetworkTranspiler(spec, locator.path("policy"), stateSizes, random).build();
        this.critic = new NetworkTranspiler(spec, locator.path("critic"), stateSizes, random).build();
    }
}
