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

package org.mmarini.rltd;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.exp;
import static java.lang.Math.tanh;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.text;


class TDNetworkTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    private static final String YAML = text("---",
            "alpha: 1e-3",
            "lambda: 0.5",
            "layers:",
            "- name: layer1",
            "  type: dense",
            "  inputSize: 2",
            "  outputSize: 3",
            "- name: layer2",
            "  type: tanh",
            "- name: layer3",
            "  type: softmax",
            "  temperature: 1.1",
            "inputs:",
            "  layer1: [inputs]",
            "  layer2: [layer1]",
            "  layer3: [layer2]"
    );

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                exponential(1e-3f, 100e-3f), // alpha
                uniform(0f, 0.5f), // lambda
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // eb2
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 3, 2)), // ew2
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // b2
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 3, 2)), // w2
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // eb4
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // ew4
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // b4
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 2, 2)), // w4
                gaussian(0f, 1f), // b7
                gaussian(0f, 1f), // w7
                exponential(0.3f, 3f), // temperature
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 1)), // input0
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // input1
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // grad7
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // grad8
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 1)) // delta
        );
    }

    static TDNetwork createNet(float alpha,
                               float lambda,
                               INDArray eb2,
                               INDArray ew2,
                               INDArray b2,
                               INDArray w2,
                               INDArray eb4,
                               INDArray ew4,
                               INDArray b4,
                               INDArray w4,
                               float b7,
                               float w7,
                               float temperature) {
        Map<String, TDLayer> layers = Stream.of(
                new TDConcat("layer1"),
                new TDDense("layer2", eb2, ew2, b2, w2),
                new TDRelu("layer3"),
                new TDDense("layer4", eb4, ew4, b4, w4),
                new TDSum("layer5"),
                new TDTanh("layer6"),
                new TDLinear("layer7", b7, w7),
                new TDSoftmax("layer8", temperature)
        ).collect(Collectors.toMap(
                TDLayer::getName,
                l -> l
        ));
        List<String> forward = List.of(
                "layer1",
                "layer2",
                "layer3",
                "layer4",
                "layer5",
                "layer6",
                "layer7",
                "layer8"
        );
        Map<String, List<String>> inputs = Map.of(
                "layer1", List.of("input0", "input1"),
                "layer2", List.of("layer1"),
                "layer3", List.of("layer2"),
                "layer4", List.of("layer3"),
                "layer5", List.of("layer3", "layer4"),
                "layer6", List.of("layer5"),
                "layer7", List.of("layer6"),
                "layer8", List.of("layer6")
        );
        return new TDNetwork(layers, forward, inputs);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void create(float alpha,
                float lambda,
                INDArray eb2,
                INDArray ew2,
                INDArray b2,
                INDArray w2,
                INDArray eb4,
                INDArray ew4,
                INDArray b4,
                INDArray w4,
                float b7,
                float w7,
                float temperature,
                INDArray input0,
                INDArray input1,
                INDArray grad7,
                INDArray grad8,
                INDArray delta) {

        TDNetwork net = createNet(alpha, lambda,
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature);

        assertThat(net.getBackwardSeq(), contains(
                "layer8",
                "layer7",
                "layer6",
                "layer5",
                "layer4",
                "layer3",
                "layer2",
                "layer1"
        ));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer1"),
                containsInAnyOrder("layer2")));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer2"),
                containsInAnyOrder("layer3")));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer3"),
                containsInAnyOrder("layer4", "layer5")));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer4"),
                containsInAnyOrder("layer5")));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer5"),
                containsInAnyOrder("layer6")));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer6"),
                containsInAnyOrder("layer7", "layer8")));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer7"),
                empty()));
        assertThat(net.getOutputs(), hasEntry(
                equalTo("layer7"),
                empty()));
        assertThat(net.getSourceLabels(), containsInAnyOrder("input0", "input1"));
        assertThat(net.getSinkLabels(), containsInAnyOrder("layer7", "layer8"));
    }

    @Test
    void createBySpec() throws IOException {
        JsonNode spec = Utils.fromText(YAML);
        Locator locator = Locator.root();
        Map<String, INDArray> props = Map.of();
        String prefix = "";
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDNetwork net = TDNetwork.create(spec, locator, prefix, props, random);
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer1"),
                isA(TDDense.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer2"),
                isA(TDTanh.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer3"),
                isA(TDSoftmax.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer3"),
                isA(TDSoftmax.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer3"),
                hasProperty(
                        "temperature",
                        equalTo(1.1f))
        ));
        assertThat(net.getInputs(), hasEntry(
                equalTo("layer1"),
                containsInAnyOrder("inputs")
        ));
        assertThat(net.getInputs(), hasEntry(
                equalTo("layer2"),
                containsInAnyOrder("layer1")
        ));
        assertThat(net.getInputs(), hasEntry(
                equalTo("layer3"),
                containsInAnyOrder("layer2")
        ));
        assertThat(net.getLayers().get("layer1"),
                hasProperty("b",
                        equalTo(Nd4j.zeros(1, 3))));

        long[] ws = ((TDDense) net.getLayers().get("layer1")).getW().shape();
        assertEquals(2, ws.length);
        assertEquals(2, ws[0]);
        assertEquals(3, ws[1]);
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(float alpha,
                 float lambda,
                 INDArray eb2,
                 INDArray ew2,
                 INDArray b2,
                 INDArray w2,
                 INDArray eb4,
                 INDArray ew4,
                 INDArray b4,
                 INDArray w4,
                 float b7,
                 float w7,
                 float temperature,
                 INDArray input0,
                 INDArray input1,
                 INDArray grad7,
                 INDArray grad8,
                 INDArray delta) {
        float in00 = input0.getFloat(0, 0);
        float in10 = input1.getFloat(1, 0);
        float in11 = input1.getFloat(1, 1);
        float b20 = b2.getFloat(0, 0);
        float b21 = b2.getFloat(0, 1);
        float w200 = w2.getFloat(0, 0);
        float w201 = w2.getFloat(0, 1);
        float w210 = w2.getFloat(1, 0);
        float w211 = w2.getFloat(1, 1);
        float w220 = w2.getFloat(2, 0);
        float w221 = w2.getFloat(2, 1);
        float b40 = b4.getFloat(0, 0);
        float b41 = b4.getFloat(0, 1);
        float w400 = w4.getFloat(0, 0);
        float w401 = w4.getFloat(0, 1);
        float w410 = w4.getFloat(1, 0);
        float w411 = w4.getFloat(1, 1);
        float l20 = in00 * w200 + in10 * w210 + in11 * w220 + b20;
        float l21 = in00 * w201 + in10 * w211 + in11 * w221 + b21;
        float l30 = l20 > 0 ? l20 : 0;
        float l31 = l21 > 0 ? l21 : 0;
        float l40 = l30 * w400 + l31 * w410 + b40;
        float l41 = l30 * w401 + l31 * w411 + b41;
        float l50 = l30 + l40;
        float l51 = l31 + l41;
        float l60 = (float) tanh(l50);
        float l61 = (float) tanh(l51);
        float l70 = l60 * w7 + b7;
        float l71 = l61 * w7 + b7;
        double ez80 = exp(l60 / temperature);
        double ez81 = exp(l61 / temperature);
        double ez8 = ez80 + ez81;

        float l80 = (float) (ez80 / ez8);
        float l81 = (float) (ez81 / ez8);

        TDNetwork net = createNet(alpha, lambda,
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature);
        Map<String, INDArray> in = Map.of(
                "input0", input0,
                "input1", input1);
        INDArray input0Org = input0.dup();
        INDArray input1Org = input1.dup();

        Map<String, INDArray> out = net.forward(in);

        assertEquals(input0, input0Org);
        assertEquals(input1, input1Org);
        assertThat(out.get("layer1"), matrixCloseTo(new float[][]{
                {in00, in10, in11}
        }, EPSILON));
        assertThat(out, hasEntry(
                equalTo("layer2"),
                matrixCloseTo(new float[][]{
                        {l20, l21}
                }, EPSILON)));
        assertThat(out, hasEntry(
                equalTo("layer3"),
                matrixCloseTo(new float[][]{
                        {l30, l31}
                }, EPSILON)));
        assertThat(out, hasEntry(
                equalTo("layer4"),
                matrixCloseTo(new float[][]{
                        {l40, l41}
                }, EPSILON)));
        assertThat(out, hasEntry(
                equalTo("layer5"),
                matrixCloseTo(new float[][]{
                        {l50, l51}
                }, EPSILON)));
        assertThat(out, hasEntry(
                equalTo("layer6"),
                matrixCloseTo(new float[][]{
                        {l60, l61}
                }, EPSILON)));
        assertThat(out, hasEntry(
                equalTo("layer7"),
                matrixCloseTo(new float[][]{
                        {l70, l71}
                }, EPSILON)));
        assertThat(out, hasEntry(
                equalTo("layer8"),
                matrixCloseTo(new float[][]{
                        {l80, l81}
                }, EPSILON)));
    }

    @Test
    void load() throws IOException {
        JsonNode spec = Utils.fromText(YAML);
        Locator locator = Locator.root();
        Map<String, INDArray> props = Map.of(
                "net.layer1.b", Nd4j.randn(1, 3),
                "net.layer1.w", Nd4j.randn(2, 3)
        );
        String prefix = "net";
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDNetwork net = TDNetwork.create(spec, locator, prefix, props, random);
        assertEquals(props, net.getProps("net"));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer1"),
                isA(TDDense.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer2"),
                isA(TDTanh.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer3"),
                isA(TDSoftmax.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer3"),
                isA(TDSoftmax.class)
        ));
        assertThat(net.getLayers(), hasEntry(
                equalTo("layer3"),
                hasProperty(
                        "temperature",
                        equalTo(1.1f))
        ));
        assertThat(net.getInputs(), hasEntry(
                equalTo("layer1"),
                containsInAnyOrder("inputs")
        ));
        assertThat(net.getInputs(), hasEntry(
                equalTo("layer2"),
                containsInAnyOrder("layer1")
        ));
        assertThat(net.getInputs(), hasEntry(
                equalTo("layer3"),
                containsInAnyOrder("layer2")
        ));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void props(float alpha,
               float lambda,
               INDArray eb2,
               INDArray ew2,
               INDArray b2,
               INDArray w2,
               INDArray eb4,
               INDArray ew4,
               INDArray b4,
               INDArray w4,
               float b7,
               float w7,
               float temperature,
               INDArray input0,
               INDArray input1,
               INDArray grad7,
               INDArray grad8,
               INDArray delta) {

        Map<String, INDArray> props = createNet(alpha, lambda,
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature).getProps("net");

        assertThat(props, hasEntry("net.layer2.b", b2));
        assertThat(props, hasEntry("net.layer2.w", w2));
        assertThat(props, hasEntry("net.layer4.b", b4));
        assertThat(props, hasEntry("net.layer4.w", w4));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void spec(float alpha,
              float lambda,
              INDArray eb2,
              INDArray ew2,
              INDArray b2,
              INDArray w2,
              INDArray eb4,
              INDArray ew4,
              INDArray b4,
              INDArray w4,
              float b7,
              float w7,
              float temperature,
              INDArray input0,
              INDArray input1,
              INDArray grad7,
              INDArray grad8,
              INDArray delta) {

        JsonNode spec = createNet(alpha, lambda,
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature).getSpec();

        JsonNode layers = spec.path("layers");
        assertTrue(layers.isArray());
        assertThat(layers.size(), equalTo(8));

        JsonNode layer1 = layers.path(0);
        assertEquals("layer1", layer1.path("name").asText());
        assertEquals("concat", layer1.path("type").asText());

        JsonNode layer2 = layers.path(1);
        assertEquals("layer2", layer2.path("name").asText());
        assertEquals("dense", layer2.path("type").asText());
        assertEquals(3, layer2.path("inputSize").asInt());
        assertEquals(2, layer2.path("outputSize").asInt());

        JsonNode layer3 = layers.path(2);
        assertEquals("layer3", layer3.path("name").asText());
        assertEquals("relu", layer3.path("type").asText());

        JsonNode layer4 = layers.path(3);
        assertEquals("layer4", layer4.path("name").asText());
        assertEquals("dense", layer4.path("type").asText());
        assertEquals(2, layer4.path("inputSize").asInt());
        assertEquals(2, layer4.path("outputSize").asInt());

        JsonNode layer5 = layers.path(4);
        assertEquals("layer5", layer5.path("name").asText());
        assertEquals("sum", layer5.path("type").asText());

        JsonNode layer6 = layers.path(5);
        assertEquals("layer6", layer6.path("name").asText());
        assertEquals("tanh", layer6.path("type").asText());

        JsonNode layer7 = layers.path(6);
        assertEquals("layer7", layer7.path("name").asText());
        assertEquals("linear", layer7.path("type").asText());
        assertEquals(b7, (float) (layer7.path("b").asDouble()));
        assertEquals(w7, (float) (layer7.path("w").asDouble()));

        JsonNode layer8 = layers.path(7);
        assertEquals("layer8", layer8.path("name").asText());
        assertEquals("softmax", layer8.path("type").asText());
        assertEquals(temperature, (float) (layer8.path("temperature").asDouble()));

        layers = spec.path("inputs");
        assertTrue(layers.isObject());

        assertEquals(2, layers.path("layer1").size());
        assertEquals("input0", layers.path("layer1").path(0).asText());
        assertEquals("input1", layers.path("layer1").path(1).asText());

        assertEquals(1, layers.path("layer2").size());
        assertEquals("layer1", layers.path("layer2").path(0).asText());

        assertEquals(1, layers.path("layer3").size());
        assertEquals("layer2", layers.path("layer3").path(0).asText());

        assertEquals(1, layers.path("layer4").size());
        assertEquals("layer3", layers.path("layer4").path(0).asText());

        assertEquals(2, layers.path("layer5").size());
        assertEquals("layer3", layers.path("layer5").path(0).asText());
        assertEquals("layer4", layers.path("layer5").path(1).asText());

        assertEquals(1, layers.path("layer6").size());
        assertEquals("layer5", layers.path("layer6").path(0).asText());

        assertEquals(1, layers.path("layer7").size());
        assertEquals("layer6", layers.path("layer7").path(0).asText());

        assertEquals(1, layers.path("layer8").size());
        assertEquals("layer6", layers.path("layer8").path(0).asText());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(float alpha,
               float lambda,
               INDArray eb2,
               INDArray ew2,
               INDArray b2,
               INDArray w2,
               INDArray eb4,
               INDArray ew4,
               INDArray b4,
               INDArray w4,
               float b7,
               float w7,
               float temperature,
               INDArray input0,
               INDArray input1,
               INDArray grad7,
               INDArray grad8,
               INDArray delta) {
        float in00 = input0.getFloat(0, 0);
        float in10 = input1.getFloat(1, 0);
        float in11 = input1.getFloat(1, 1);

        float eb20 = eb2.getFloat(0, 0);
        float eb21 = eb2.getFloat(0, 1);
        float ew200 = ew2.getFloat(0, 0);
        float ew201 = ew2.getFloat(0, 1);
        float ew210 = ew2.getFloat(1, 0);
        float ew211 = ew2.getFloat(1, 1);
        float ew220 = ew2.getFloat(2, 0);
        float ew221 = ew2.getFloat(2, 1);
        float b20 = b2.getFloat(0, 0);
        float b21 = b2.getFloat(0, 1);
        float w200 = w2.getFloat(0, 0);
        float w201 = w2.getFloat(0, 1);
        float w210 = w2.getFloat(1, 0);
        float w211 = w2.getFloat(1, 1);
        float w220 = w2.getFloat(2, 0);
        float w221 = w2.getFloat(2, 1);

        float eb40 = eb4.getFloat(0, 0);
        float eb41 = eb4.getFloat(0, 1);
        float ew400 = ew4.getFloat(0, 0);
        float ew401 = ew4.getFloat(0, 1);
        float ew410 = ew4.getFloat(1, 0);
        float ew411 = ew4.getFloat(1, 1);
        float b40 = b4.getFloat(0, 0);
        float b41 = b4.getFloat(0, 1);
        float w400 = w4.getFloat(0, 0);
        float w401 = w4.getFloat(0, 1);
        float w410 = w4.getFloat(1, 0);
        float w411 = w4.getFloat(1, 1);

        float fdelta = delta.getFloat(0, 0);

        float l20 = in00 * w200 + in10 * w210 + in11 * w220 + b20;
        float l21 = in00 * w201 + in10 * w211 + in11 * w221 + b21;
        float l30 = l20 > 0 ? l20 : 0;
        float l31 = l21 > 0 ? l21 : 0;
        float l40 = l30 * w400 + l31 * w410 + b40;
        float l41 = l30 * w401 + l31 * w411 + b41;
        float l50 = l30 + l40;
        float l51 = l31 + l41;
        float l60 = (float) tanh(l50);
        float l61 = (float) tanh(l51);
        double ez80 = exp(l60 / temperature);
        double ez81 = exp(l61 / temperature);
        double ez8 = ez80 + ez81;

        float l80 = (float) (ez80 / ez8);
        float l81 = (float) (ez81 / ez8);


        float g70 = grad7.getFloat(0, 0);
        float g71 = grad7.getFloat(0, 1);

        float g80 = grad8.getFloat(0, 0);
        float g81 = grad8.getFloat(0, 1);

        float gi70 = g70 * w7;
        float gi71 = g71 * w7;

        float gi80 = (g80 * l80 * (1 - l80) - g81 * l81 * l80) / temperature;
        float gi81 = (-g80 * l80 * l81 + g81 * l81 * (1 - l81)) / temperature;

        float g60 = gi70 + gi80;
        float g61 = gi71 + gi81;

        float g50 = g60 * (1 - l60 * l60);
        float g51 = g61 * (1 - l61 * l61);

        float g40 = g50;
        float g41 = g51;

        float gi40 = g40 * w400 + g41 * w401;
        float gi41 = g40 * w410 + g41 * w411;

        float g30 = gi40 + g50;
        float g31 = gi41 + g51;

        float g20 = l20 > 0 ? g30 : 0;
        float g21 = l21 > 0 ? g31 : 0;

        float g10 = g20 * w200 + g21 * w201;
        float g11 = g20 * w210 + g21 * w211;
        float g12 = g20 * w220 + g21 * w221;

        float post_eb20 = eb20 * lambda + g20;
        float post_eb21 = eb21 * lambda + g21;
        float post_b20 = b20 + alpha * fdelta * post_eb20;
        float post_b21 = b21 + alpha * fdelta * post_eb21;

        float post_ew200 = ew200 * lambda + g20 * in00;
        float post_ew201 = ew201 * lambda + g21 * in00;
        float post_ew210 = ew210 * lambda + g20 * in10;
        float post_ew211 = ew211 * lambda + g21 * in10;
        float post_ew220 = ew220 * lambda + g20 * in11;
        float post_ew221 = ew221 * lambda + g21 * in11;

        float post_w200 = w200 + alpha * fdelta * post_ew200;
        float post_w201 = w201 + alpha * fdelta * post_ew201;
        float post_w210 = w210 + alpha * fdelta * post_ew210;
        float post_w211 = w211 + alpha * fdelta * post_ew211;
        float post_w220 = w220 + alpha * fdelta * post_ew220;
        float post_w221 = w221 + alpha * fdelta * post_ew221;

        float post_eb40 = eb40 * lambda + g40;
        float post_eb41 = eb41 * lambda + g41;

        float post_b40 = b40 + alpha * fdelta * post_eb40;
        float post_b41 = b41 + alpha * fdelta * post_eb41;

        float post_ew400 = ew400 * lambda + g40 * l30;
        float post_ew401 = ew401 * lambda + g41 * l30;
        float post_ew410 = ew410 * lambda + g40 * l31;
        float post_ew411 = ew411 * lambda + g41 * l31;

        float post_w400 = w400 + alpha * fdelta * post_ew400;
        float post_w401 = w401 + alpha * fdelta * post_ew401;
        float post_w410 = w410 + alpha * fdelta * post_ew410;
        float post_w411 = w411 + alpha * fdelta * post_ew411;

        TDNetwork net = createNet(alpha, lambda,
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature);
        Map<String, INDArray> in = Map.of(
                "input0", input0,
                "input1", input1);
        Map<String, INDArray> out = net.forward(in);
        Map<String, INDArray> grads = Map.of("layer7", grad7,
                "layer8", grad8);

        INDArray outInput0Org = out.get("input0").dup();
        INDArray outInput1Org = out.get("input1").dup();
        INDArray outLayer1Org = out.get("layer1").dup();
        INDArray outLayer2Org = out.get("layer2").dup();
        INDArray outLayer3Org = out.get("layer3").dup();
        INDArray outLayer4Org = out.get("layer4").dup();
        INDArray outLayer5Org = out.get("layer5").dup();
        INDArray outLayer6Org = out.get("layer6").dup();
        INDArray deltaOrg = delta.dup();
        INDArray grad7Org = grad7.dup();
        INDArray grad8Org = grad8.dup();

        Map<String, INDArray> ctx = net.train(out, grads, delta.mul(alpha), lambda, null);

        assertEquals(out.get("input0"), outInput0Org);
        assertEquals(out.get("input1"), outInput1Org);
        assertEquals(out.get("layer1"), outLayer1Org);
        assertEquals(out.get("layer2"), outLayer2Org);
        assertEquals(out.get("layer3"), outLayer3Org);
        assertEquals(out.get("layer4"), outLayer4Org);
        assertEquals(out.get("layer5"), outLayer5Org);
        assertEquals(out.get("layer6"), outLayer6Org);
        assertEquals(delta, deltaOrg);
        assertEquals(grad7, grad7Org);
        assertEquals(grad8, grad8Org);

        assertThat(ctx.get("layer6"), matrixCloseTo(new float[][]{
                {g60, g61}
        }, EPSILON));
        assertThat(ctx.get("layer5"), matrixCloseTo(new float[][]{
                {g50, g51}
        }, EPSILON));
        assertThat(ctx.get("layer4"), matrixCloseTo(new float[][]{
                {g40, g41}
        }, EPSILON));
        assertThat(ctx.get("layer3"), matrixCloseTo(new float[][]{
                {g30, g31}
        }, EPSILON));
        assertThat(ctx.get("layer2"), matrixCloseTo(new float[][]{
                {g20, g21}
        }, EPSILON));
        assertThat(ctx.get("layer1"), matrixCloseTo(new float[][]{
                {g10, g11, g12}
        }, EPSILON));
        assertThat(ctx.get("input1"), matrixCloseTo(new float[][]{
                {g11, g12}
        }, EPSILON));
        assertThat(ctx.get("input0"), matrixCloseTo(new float[][]{
                {g10}
        }, EPSILON));

        Map<String, TDLayer> layers = net.getLayers();
        assertThat(((TDDense) layers.get("layer2")).getEb(), matrixCloseTo(new float[][]{
                {post_eb20, post_eb21}
        }, EPSILON));
        assertThat(((TDDense) layers.get("layer2")).getB(), matrixCloseTo(new float[][]{
                {post_b20, post_b21}
        }, EPSILON));
        assertThat(((TDDense) layers.get("layer2")).getEw(), matrixCloseTo(new float[][]{
                {post_ew200, post_ew201},
                {post_ew210, post_ew211},
                {post_ew220, post_ew221}
        }, EPSILON));
        assertThat(((TDDense) layers.get("layer2")).getW(), matrixCloseTo(new float[][]{
                {post_w200, post_w201},
                {post_w210, post_w211},
                {post_w220, post_w221}
        }, EPSILON));

        assertThat(((TDDense) layers.get("layer4")).getEb(), matrixCloseTo(new float[][]{
                {post_eb40, post_eb41}
        }, EPSILON));
        assertThat(((TDDense) layers.get("layer4")).getB(), matrixCloseTo(new float[][]{
                {post_b40, post_b41}
        }, EPSILON));
        assertThat(((TDDense) layers.get("layer4")).getEw(), matrixCloseTo(new float[][]{
                {post_ew400, post_ew401},
                {post_ew410, post_ew411},
        }, EPSILON));
        assertThat(((TDDense) layers.get("layer4")).getW(), matrixCloseTo(new float[][]{
                {post_w400, post_w401},
                {post_w410, post_w411},
        }, EPSILON));
    }
}
