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
import org.junit.jupiter.api.Test;
import org.mmarini.rl.nets.*;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class NetworkTranspilerTest {
    public static final int SEED = 1234;
    private static final String MULTIDENSE_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: dense",
            "    outputSize: 4",
            "  - type: dense",
            "    outputSize: 2"
    );
    private static final String MULTI_SEQ_YAML = text(
            "---",
            "hidden:",
            "  layers:",
            "    - type: dense",
            "      outputSize: 2",
            "output.a:",
            "  input: hidden",
            "  layers:",
            "    - type: tanh",
            "output.b:",
            "  input: hidden",
            "  layers:",
            "    - type: relu"
    );
    private static final String CONCAT_YAML = text(
            "---",
            "output:",
            "  inputs: ",
            "    type: concat",
            "    inputs: [input, input]",
            "  layers: []"
    );
    private static final String RESNET_MULTILAYER_YAML = text(
            "---",
            "hidden:",
            "  layers:",
            "    - type: dense",
            "      outputSize: 2",
            "output:",
            "  inputs: ",
            "    type: sum",
            "    inputs: [hidden, input]",
            "  layers:",
            "    - type: relu",
            "    - type: tanh"
    );
    private static final String SUM_YAML = text(
            "---",
            "output:",
            "  inputs: ",
            "    type: sum",
            "    inputs: [input, input]",
            "  layers: []"
    );
    private static final String LINEAR_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: linear",
            "    b: 2",
            "    w: 3"
    );
    private static final String DENSE_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: dense",
            "    outputSize: 3",
            "    dropOut: 0.5"
    );
    private static final String DENSE_YAML1 = text(
            "---",
            "output:",
            "  layers:",
            "  - type: dense",
            "    outputSize: 3",
            "    maxAbsWeights: 10"
    );
    private static final String RELU_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: relu"
    );
    private static final String DROP_OUT_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: dropout",
            "    dropOut: 0.5"
    );
    private static final String TANH_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: tanh"
    );
    private static final String SOFTMAX_YAML = text(
            "---",
            "output:",
            "  layers:",
            "  - type: softmax",
            "    temperature: 1.2"
    );

    @Test
    void build() throws IOException {
        NetworkTranspiler tr = create(TANH_YAML);

        TDNetwork net = tr.build();

        assertThat(net.layers(), hasEntry(
                equalTo("output"),
                isA(TDTanh.class)
        ));
        assertThat(net.forwardSequence(), contains(
                "output"));
        assertThat(net.layers().get("output").inputs(),
                arrayContainingInAnyOrder("input")
        );
    }

    NetworkTranspiler create(String yaml) throws IOException {
        JsonNode agentSpec = fromText(yaml);
        Map<String, Long> stateSpec = Map.of(
                "input", 2L
        );
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return new NetworkTranspiler(agentSpec, Locator.root(), stateSpec, random);
    }

    @Test
    void parseConcat() throws IOException {
        NetworkTranspiler tr = create(CONCAT_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input", "input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(4L)
        ));
        assertThat(tr.layers, contains(isA(TDConcat.class)));
        assertEquals("output", tr.layers.getFirst().name());
        assertThat(tr.layers.getFirst().inputs(), arrayContainingInAnyOrder("input", "input"));
    }

    @Test
    void parseDense() throws IOException {
        NetworkTranspiler tr = create(DENSE_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(3L)
        ));
        assertThat(tr.layers, contains(isA(TDDense.class)));
        assertEquals(Float.MAX_VALUE, ((TDDense) tr.layers.getFirst()).maxAbsWeights());
        assertEquals(0.5f, ((TDDense) tr.layers.getFirst()).dropOut());
    }

    @Test
    void parseDense1() throws IOException {
        NetworkTranspiler tr = create(DENSE_YAML1);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(3L)
        ));
        assertThat(tr.layers, contains(isA(TDDense.class)));
        assertEquals(10F, ((TDDense) tr.layers.getFirst()).maxAbsWeights());
        assertEquals(1F, ((TDDense) tr.layers.getFirst()).dropOut());
    }

    /* TODO
    @Test
    void parseDropOut() throws IOException {
        NetworkTranspiler tr = create(DROP_OUT_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(allOf(
                hasProperty("name", equalTo("output")),
                isA(TDDropOut.class)
        )));
        assertThat(tr.layers, contains(allOf(
                hasProperty("name", equalTo("output")),
                hasProperty("dropOut", equalTo(0.5F))
        )));
    }
*/

    @Test
    void parseLinear() throws IOException {
        NetworkTranspiler tr = create(LINEAR_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(isA(TDLinear.class)));

        assertEquals(2f, ((TDLinear) tr.layers.getFirst()).bias());
        assertEquals(3f, ((TDLinear) tr.layers.getFirst()).weight());
    }

    @Test
    void parseMultiDense() throws IOException {
        NetworkTranspiler tr = create(MULTIDENSE_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output[0]"));
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output[0]"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("output[0]")
        ));
        assertThat(tr.sorted, contains("output[0]", "output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output[0]"),
                equalTo(4L)
        ));
        assertThat(tr.layers, contains(
                isA(TDDense.class),
                isA(TDDense.class)
        ));
        assertEquals("output[0]", tr.layers.getFirst().name());
        assertEquals("output", tr.layers.get(1).name());
    }

    @Test
    void parseMultiSequence() throws IOException {
        NetworkTranspiler tr = create(MULTI_SEQ_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("hidden"));
        assertThat(tr.layerDef, hasKey("output.a"));
        assertThat(tr.layerDef, hasKey("output.b"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("hidden"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output.a"),
                arrayContainingInAnyOrder("hidden")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output.b"),
                arrayContainingInAnyOrder("hidden")
        ));
        assertThat(tr.sorted, contains("hidden", "output.a", "output.b"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("hidden"),
                equalTo(2L)
        ));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output.a"),
                equalTo(2L)
        ));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output.b"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(
                isA(TDDense.class),
                isA(TDTanh.class),
                isA(TDRelu.class)
        ));

        assertEquals("hidden", tr.layers.getFirst().name());
        assertEquals("output.a", tr.layers.get(1).name());
        assertEquals("output.b", tr.layers.get(2).name());
    }

    @Test
    void parseRelu() throws IOException {
        NetworkTranspiler tr = create(RELU_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(isA(TDRelu.class)));
        assertEquals("output", tr.layers.getFirst().name());
    }

    @Test
    void parseResnet() throws IOException {
        NetworkTranspiler tr = create(RESNET_MULTILAYER_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("hidden"));
        assertThat(tr.layerDef, hasKey("output[0]"));
        assertThat(tr.layerDef, hasKey("output[1]"));
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("hidden"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output[0]"),
                arrayContainingInAnyOrder("hidden", "input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output[1]"),
                arrayContainingInAnyOrder("output[0]")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("output[1]")
        ));
        assertThat(tr.sorted, contains("hidden", "output[0]", "output[1]", "output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("hidden"),
                equalTo(2L)
        ));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output[0]"),
                equalTo(2L)
        ));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output[1]"),
                equalTo(2L)
        ));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(
                isA(TDDense.class),
                isA(TDSum.class),
                isA(TDRelu.class),
                isA(TDTanh.class)
        ));
        assertEquals("hidden", tr.layers.getFirst().name());
        assertEquals("output[0]", tr.layers.get(1).name());
        assertEquals("output[1]", tr.layers.get(2).name());
        assertEquals("output", tr.layers.get(3).name());
    }

    @Test
    void parseSoftmax() throws IOException {
        NetworkTranspiler tr = create(SOFTMAX_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(isA(TDSoftmax.class)));
        assertEquals("output", tr.layers.getFirst().name());
        assertEquals(1.2f, ((TDSoftmax) tr.layers.getFirst()).temperature());
    }

    @Test
    void parseSum() throws IOException {
        NetworkTranspiler tr = create(SUM_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input", "input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(isA(TDSum.class)));
        assertEquals("output", tr.layers.getFirst().name());
    }

    @Test
    void parseTanh() throws IOException {
        NetworkTranspiler tr = create(TANH_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                arrayContainingInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(isA(TDTanh.class)));
        assertEquals("output", tr.layers.getFirst().name());
    }
}