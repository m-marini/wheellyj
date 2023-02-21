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
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class NetworkTraspillerTest {
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
        NetworkTranspiller tr = create(TANH_YAML);

        TDNetwork net = tr.build();

        assertThat(net.getLayers(), hasEntry(
                equalTo("output"),
                isA(TDTanh.class)
        ));
        assertThat(net.getForwardSeq(), contains(
                "output"));
        assertThat(net.getInputs(), hasEntry(
                equalTo("output"),
                containsInAnyOrder("input")
        ));
    }

    NetworkTranspiller create(String yaml) throws IOException {
        JsonNode agentSpec = fromText(yaml);
        Map<String, Long> stateSpec = Map.of(
                "input", 2L
        );
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return new NetworkTranspiller(agentSpec, Locator.root(), stateSpec, random);
    }

    @Test
    void parseConcat() throws IOException {
        NetworkTranspiller tr = create(CONCAT_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("input", "input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(4L)
        ));
        assertThat(tr.layers, contains(allOf(
                hasProperty("name", equalTo("output")),
                isA(TDConcat.class)
        )));
    }

    @Test
    void parseDense() throws IOException {
        NetworkTranspiller tr = create(DENSE_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(3L)
        ));
        assertThat(tr.layers, contains(allOf(
                hasProperty("name", equalTo("output")),
                isA(TDDense.class)
        )));
        assertThat(tr.layers, contains(
                hasProperty("maxAbsWeights", equalTo(Float.MAX_VALUE))
        ));
        assertThat(tr.layers, contains(
                hasProperty("dropOut", equalTo(0.5F))
        ));
        assertArrayEquals(new long[]{2, 3},
                ((TDDense) tr.layers.get(0)).getW().shape()
        );
    }

    @Test
    void parseDense1() throws IOException {
        NetworkTranspiller tr = create(DENSE_YAML1);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(3L)
        ));
        assertThat(tr.layers, contains(allOf(
                hasProperty("name", equalTo("output")),
                isA(TDDense.class)
        )));
        assertThat(tr.layers, contains(
                hasProperty("maxAbsWeights", equalTo(10F))
        ));
        assertArrayEquals(new long[]{2, 3},
                ((TDDense) tr.layers.get(0)).getW().shape()
        );
    }

    @Test
    void parseDropOut() throws IOException {
        NetworkTranspiller tr = create(DROP_OUT_YAML);
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

    @Test
    void parseLinear() throws IOException {
        NetworkTranspiller tr = create(LINEAR_YAML);
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
                isA(TDLinear.class),
                hasProperty("b", equalTo(2f)),
                hasProperty("w", equalTo(3f))
        )));
    }

    @Test
    void parseMultiDense() throws IOException {
        NetworkTranspiller tr = create(MULTIDENSE_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output[0]"));
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output[0]"),
                containsInAnyOrder("input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("output[0]")
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
                allOf(
                        hasProperty("name", equalTo("output[0]")),
                        isA(TDDense.class)
                ),
                allOf(
                        hasProperty("name", equalTo("output")),
                        isA(TDDense.class)
                )));
        assertArrayEquals(new long[]{2, 4},
                ((TDDense) tr.layers.get(0)).getW().shape()
        );
        assertArrayEquals(new long[]{4, 2},
                ((TDDense) tr.layers.get(1)).getW().shape()
        );
    }

    @Test
    void parseMultiSequence() throws IOException {
        NetworkTranspiller tr = create(MULTI_SEQ_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("hidden"));
        assertThat(tr.layerDef, hasKey("output.a"));
        assertThat(tr.layerDef, hasKey("output.b"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("hidden"),
                containsInAnyOrder("input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output.a"),
                containsInAnyOrder("hidden")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output.b"),
                containsInAnyOrder("hidden")
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
                allOf(
                        hasProperty("name", equalTo("hidden")),
                        isA(TDDense.class)
                ),
                allOf(
                        hasProperty("name", equalTo("output.a")),
                        isA(TDTanh.class)
                ),
                allOf(
                        hasProperty("name", equalTo("output.b")),
                        isA(TDRelu.class)
                )));
        assertArrayEquals(new long[]{2, 2},
                ((TDDense) tr.layers.get(0)).getW().shape()
        );
    }

    @Test
    void parseRelu() throws IOException {
        NetworkTranspiller tr = create(RELU_YAML);
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
                isA(TDRelu.class)
        )));
    }

    @Test
    void parseResnet() throws IOException {
        NetworkTranspiller tr = create(RESNET_MULTILAYER_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("hidden"));
        assertThat(tr.layerDef, hasKey("output[0]"));
        assertThat(tr.layerDef, hasKey("output[1]"));
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("hidden"),
                containsInAnyOrder("input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output[0]"),
                containsInAnyOrder("hidden", "input")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output[1]"),
                containsInAnyOrder("output[0]")
        ));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("output[1]")
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
                allOf(
                        hasProperty("name", equalTo("hidden")),
                        isA(TDDense.class)
                ),
                allOf(
                        hasProperty("name", equalTo("output[0]")),
                        isA(TDSum.class)
                ),
                allOf(
                        hasProperty("name", equalTo("output[1]")),
                        isA(TDRelu.class)
                ),
                allOf(
                        hasProperty("name", equalTo("output")),
                        isA(TDTanh.class)
                )));
        assertArrayEquals(new long[]{2, 2},
                ((TDDense) tr.layers.get(0)).getW().shape()
        );
    }

    @Test
    void parseSoftmax() throws IOException {
        NetworkTranspiller tr = create(SOFTMAX_YAML);
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
                isA(TDSoftmax.class),
                hasProperty("temperature", equalTo(1.2f))
        )));
    }

    @Test
    void parseSum() throws IOException {
        NetworkTranspiller tr = create(SUM_YAML);
        tr.parse();
        assertThat(tr.layerDef, hasKey("output"));
        assertThat(tr.inputsDef, hasEntry(
                equalTo("output"),
                containsInAnyOrder("input", "input")
        ));
        assertThat(tr.sorted, contains("output"));
        assertThat(tr.layerSizes, hasEntry(
                equalTo("output"),
                equalTo(2L)
        ));
        assertThat(tr.layers, contains(allOf(
                hasProperty("name", equalTo("output")),
                isA(TDSum.class)
        )));
    }

    @Test
    void parseTanh() throws IOException {
        NetworkTranspiller tr = create(TANH_YAML);
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
                isA(TDTanh.class)
        )));
    }
}