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
import org.mmarini.rltd.TDNetwork;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.rng.Random;

import java.util.List;
import java.util.Map;

import static org.mmarini.yaml.schema.Validator.*;

/**
 * Transpiller of simplified agent specification to internal agent specification
 */
public class AgentTraspiller {
    public static final Validator AGENT_VALIDATOR = objectPropertiesRequired(Map.of(
            "version", string(values("0.1")),
            "rewardAlpha", positiveNumber(),
            "policy", NetworkTranspiller.NETWORK_SPEC,
            "critic", NetworkTranspiller.NETWORK_SPEC
    ), List.of(
            "rewardAlpha", "policy", "critic"
    ));

    private final JsonNode spec;
    private final Map<String, SignalSpec> stateSpec;
    private final Map<String, SignalSpec> actionsSpec;
    private final Locator locator;
    private float rewardAlpha;
    private final Random random;
    private TDNetwork policy;
    private TDNetwork critic;

    public AgentTraspiller(JsonNode spec,
                           Locator locator,
                           Map<String, SignalSpec> stateSpec,
                           Map<String, SignalSpec> actionsSpec,
                           Random random) {
        this.spec = spec;
        this.stateSpec = stateSpec;
        this.actionsSpec = actionsSpec;
        this.locator = locator;
        this.random = random;
    }

    public TDAgent build() {
        parse();
        return new TDAgent(stateSpec, actionsSpec, 0, rewardAlpha, policy, critic, random);
    }

    void parse() {
        AGENT_VALIDATOR.apply(locator).accept(spec);
        this.rewardAlpha = (float) locator.path("rewardAlpha").getNode(spec).asDouble();
        Map<String, Long> stateSizes = TDAgent.getStateSizes(stateSpec);
        this.policy = new NetworkTranspiller(spec, locator.path("policy"), stateSizes, random).build();
        this.critic = new NetworkTranspiller(spec, locator.path("critic"), stateSizes, random).build();
    }
}
