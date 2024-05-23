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

package org.mmarini.rl.nets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.Math.exp;
import static java.lang.Math.tanh;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;


class TDNetworkTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    private static final String YAML = """
            ---
            $schema: https://mmarini.org/wheelly/network-schema-0.2
            alpha: 1e-3
            lambda: 0.5
            layers:
              - name: layer1
                type: dense
                maxAbsWeights: 1e3
                dropOut: 1
                inputs: [inputs]
              - name: layer2
                type: tanh
                inputs: [layer1]
              - name: layer3
                type: softmax
                temperature: 1.1
                inputs: [layer2]
            sizes:
              inputs: 2
              layer1: 2
              layer2: 2
              layer3: 2
            """;
    private static final float DROP_OUT = 1;

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

    static TDNetwork createNet(INDArray eb2,
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
        List<TDLayer> layers = List.of(
                new TDConcat("layer1", "input0", "input1"),
                new TDDense("layer2", "layer1", Float.MAX_VALUE, DROP_OUT),
                new TDRelu("layer3", "layer2"),
                new TDDense("layer4", "layer3", Float.MAX_VALUE, DROP_OUT),
                new TDSum("layer5", "layer3", "layer4"),
                new TDTanh("layer6", "layer5"),
                new TDLinear("layer7", "layer6", b7, w7),
                new TDSoftmax("layer8", "layer6", temperature)
        );
        Map<String, Long> sizes = Map.of(
                "input0", 1L,
                "input1", 2L,
                "layer1", 3L,
                "layer2", 2L,
                "layer3", 2L,
                "layer4", 2L,
                "layer5", 2L,
                "layer6", 2L,
                "layer7", 2L,
                "layer8", 2L
        );
        Map<String, INDArray> parameters = Map.of("layer2.bias", b2,
                "layer2.weights", w2,
                "layer4.bias", b4,
                "layer4.weights", w4);
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDNetwork net = TDNetwork.create(layers, sizes, random, parameters);
        net.state()
                .putBiasTrace("layer2", eb2)
                .putWeightsTrace("layer2", ew2)
                .putBiasTrace("layer4", eb4)
                .putWeightsTrace("layer4", ew4);
        return net;
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

        TDNetwork net = createNet(
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature);

        assertThat(net.backwardSequence(), contains(
                "layer8",
                "layer7",
                "layer6",
                "layer5",
                "layer4",
                "layer3",
                "layer2",
                "layer1"
        ));
        assertThat(net.sourceLayers(), containsInAnyOrder("input0", "input1"));
        assertThat(net.sinkLayers(), containsInAnyOrder("layer7", "layer8"));
    }

    @Test
    void createBySpec() throws IOException {
        JsonNode spec = Utils.fromText(YAML);
        Locator locator = Locator.root();
        INDArray w = Nd4j.randn(2, 2);
        INDArray b = Nd4j.randn(1, 2);
        Map<String, INDArray> parameters = Map.of(
                "layer1.weights", w,
                "layer1.bias", b
        );
        Random random = Nd4j.getRandom();
        random.setSeed(1234);
        TDNetwork net = TDNetwork.fromJson(spec, locator, parameters, random);
        assertThat(net.layers(), hasEntry(
                equalTo("layer1"),
                isA(TDDense.class)
        ));
        assertThat(net.layers(), hasEntry(
                equalTo("layer2"),
                isA(TDTanh.class)
        ));
        assertThat(net.layers(), hasEntry(
                equalTo("layer3"),
                isA(TDSoftmax.class)
        ));
        assertEquals(1.1f, ((TDSoftmax) net.layers().get("layer3")).temperature());

        assertThat(net.layers().get("layer1").inputs, arrayContainingInAnyOrder("inputs"));
        assertThat(net.layers().get("layer2").inputs, arrayContainingInAnyOrder("layer1"));
        assertThat(net.layers().get("layer3").inputs, arrayContainingInAnyOrder("layer2"));

        // Check for parameters
        TDNetworkState state = net.state();
        assertThat(state.getWeights("layer1"), equalTo(w));
        assertThat(state.getBias("layer1"), equalTo(b));

        // Checks for variables
        assertThat(state.getWeightsTrace("layer1"), equalTo(Nd4j.zeros(2, 2)));
        assertThat(state.getBiasTrace("layer1"), equalTo(Nd4j.zeros(1, 2)));

        assertThat(net.forwardSequence(), contains("layer1", "layer2", "layer3"));
        assertThat(net.backwardSequence(), contains("layer3", "layer2", "layer1"));
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

        TDNetwork net = createNet(
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature);
        Map<String, INDArray> in = Map.of(
                "input0", input0,
                "input1", input1);
        INDArray input0Org = input0.dup();
        INDArray input1Org = input1.dup();

        TDNetworkState state = net.forward(in).state();

        assertEquals(input0, input0Org);
        assertEquals(input1, input1Org);
        assertThat(state.getValues("layer1"), matrixCloseTo(new float[][]{
                {in00, in10, in11}
        }, EPSILON));
        assertThat(state.getValues("layer2"),
                matrixCloseTo(new float[][]{
                        {l20, l21}
                }, EPSILON));
        assertThat(state.getValues("layer3"),
                matrixCloseTo(new float[][]{
                        {l30, l31}
                }, EPSILON));
        assertThat(state.getValues("layer4"),
                matrixCloseTo(new float[][]{
                        {l40, l41}
                }, EPSILON));
        assertThat(state.getValues("layer5"),
                matrixCloseTo(new float[][]{
                        {l50, l51}
                }, EPSILON));
        assertThat(state.getValues("layer6"),
                matrixCloseTo(new float[][]{
                        {l60, l61}
                }, EPSILON));
        assertThat(state.getValues("layer7"),
                matrixCloseTo(new float[][]{
                        {l70, l71}
                }, EPSILON));
        assertThat(state.getValues("layer8"),
                matrixCloseTo(new float[][]{
                        {l80, l81}
                }, EPSILON));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void parameters(float alpha,
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

        Map<String, INDArray> props = createNet(eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature).parameters();

        assertThat(props, hasEntry("layer2.bias", b2));
        assertThat(props, hasEntry("layer2.weights", w2));
        assertThat(props, hasEntry("layer4.bias", b4));
        assertThat(props, hasEntry("layer4.weights", w4));
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

        ObjectNode spec = createNet(
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature).spec();

        assertEquals(TDNetwork.NETWORK_SCHEMA_YML, spec.path("$schema").asText());

        JsonNode layers = spec.path("layers");
        assertTrue(layers.isArray());
        assertThat(layers.size(), equalTo(8));

        JsonNode layer1 = layers.path(0);
        assertEquals("layer1", layer1.path("name").asText());
        assertEquals("concat", layer1.path("type").asText());

        JsonNode layer2 = layers.path(1);
        assertEquals("layer2", layer2.path("name").asText());
        assertEquals("dense", layer2.path("type").asText());

        JsonNode layer3 = layers.path(2);
        assertEquals("layer3", layer3.path("name").asText());
        assertEquals("relu", layer3.path("type").asText());

        JsonNode layer4 = layers.path(3);
        assertEquals("layer4", layer4.path("name").asText());
        assertEquals("dense", layer4.path("type").asText());

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

        assertEquals(2, layer1.path("inputs").size());
        assertEquals("input0", layer1.path("inputs").path(0).asText());
        assertEquals("input1", layer1.path("inputs").path(1).asText());

        assertEquals(1, layer2.path("inputs").size());
        assertEquals("layer1", layer2.path("inputs").path(0).asText());

        assertEquals(1, layer3.path("inputs").size());
        assertEquals("layer2", layer3.path("inputs").path(0).asText());

        assertEquals(1, layer4.path("inputs").size());
        assertEquals("layer3", layer4.path("inputs").path(0).asText());

        assertEquals(2, layer5.path("inputs").size());
        assertEquals("layer3", layer5.path("inputs").path(0).asText());
        assertEquals("layer4", layer5.path("inputs").path(1).asText());

        assertEquals(1, layer6.path("inputs").size());
        assertEquals("layer5", layer6.path("inputs").path(0).asText());

        assertEquals(1, layer7.path("inputs").size());
        assertEquals("layer6", layer7.path("inputs").path(0).asText());

        assertEquals(1, layer8.path("inputs").size());
        assertEquals("layer6", layer8.path("inputs").path(0).asText());

        JsonNode size = spec.path("sizes").path("input0");
        assertTrue(size.isValueNode());
        assertEquals(1L, size.asLong());

        size = spec.path("sizes").path("input1");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer1");
        assertTrue(size.isValueNode());
        assertEquals(3L, size.asLong());

        size = spec.path("sizes").path("layer2");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer3");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer4");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer5");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer6");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer7");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());

        size = spec.path("sizes").path("layer8");
        assertTrue(size.isValueNode());
        assertEquals(2L, size.asLong());
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

        TDNetwork net = createNet(
                eb2, ew2, b2, w2,
                eb4, ew4, b4, w4,
                b7, w7,
                temperature);
        Map<String, INDArray> in = Map.of(
                "input0", input0,
                "input1", input1);
        TDNetworkState out = net.forward(in).state();
        Map<String, INDArray> grads = Map.of("layer7", grad7,
                "layer8", grad8);

        INDArray outInput0Org = out.getValues("input0").dup();
        INDArray outInput1Org = out.getValues("input1").dup();
        INDArray outLayer1Org = out.getValues("layer1").dup();
        INDArray outLayer2Org = out.getValues("layer2").dup();
        INDArray outLayer3Org = out.getValues("layer3").dup();
        INDArray outLayer4Org = out.getValues("layer4").dup();
        INDArray outLayer5Org = out.getValues("layer5").dup();
        INDArray outLayer6Org = out.getValues("layer6").dup();
        INDArray deltaOrg = delta.dup();
        INDArray grad7Org = grad7.dup();
        INDArray grad8Org = grad8.dup();

        out = net.forward(in, true)
                .train(grads, delta.mul(alpha), lambda, null)
                .state();

        assertEquals(out.getValues("input0"), outInput0Org);
        assertEquals(out.getValues("input1"), outInput1Org);
        assertEquals(out.getValues("layer1"), outLayer1Org);
        assertEquals(out.getValues("layer2"), outLayer2Org);
        assertEquals(out.getValues("layer3"), outLayer3Org);
        assertEquals(out.getValues("layer4"), outLayer4Org);
        assertEquals(out.getValues("layer5"), outLayer5Org);
        assertEquals(out.getValues("layer6"), outLayer6Org);
        assertEquals(delta, deltaOrg);
        assertEquals(grad7, grad7Org);
        assertEquals(grad8, grad8Org);

        assertThat(out.getGradients("layer6"), matrixCloseTo(new float[][]{
                {g60, g61}
        }, EPSILON));

        assertThat(out.getGradients("layer5"), matrixCloseTo(new float[][]{
                {g50, g51}
        }, EPSILON));
        assertThat(out.getGradients("layer4"), matrixCloseTo(new float[][]{
                {g40, g41}
        }, EPSILON));
        assertThat(out.getGradients("layer3"), matrixCloseTo(new float[][]{
                {g30, g31}
        }, EPSILON));
        assertThat(out.getGradients("layer2"), matrixCloseTo(new float[][]{
                {g20, g21}
        }, EPSILON));
        assertThat(out.getGradients("layer1"), matrixCloseTo(new float[][]{
                {g10, g11, g12}
        }, EPSILON));
        assertThat(out.getGradients("input1"), matrixCloseTo(new float[][]{
                {g11, g12}
        }, EPSILON));
        assertThat(out.getGradients("input0"), matrixCloseTo(new float[][]{
                {g10}
        }, EPSILON));

        assertThat(out.getBiasTrace("layer2"), matrixCloseTo(new float[][]{
                {post_eb20, post_eb21}
        }, EPSILON));
        assertThat(out.getBias("layer2"), matrixCloseTo(new float[][]{
                {post_b20, post_b21}
        }, EPSILON));
        assertThat(out.getWeightsTrace("layer2"), matrixCloseTo(new float[][]{
                {post_ew200, post_ew201},
                {post_ew210, post_ew211},
                {post_ew220, post_ew221}
        }, EPSILON));
        assertThat(out.getWeights("layer2"), matrixCloseTo(new float[][]{
                {post_w200, post_w201},
                {post_w210, post_w211},
                {post_w220, post_w221}
        }, EPSILON));

        assertThat(out.getBiasTrace("layer4"), matrixCloseTo(new float[][]{
                {post_eb40, post_eb41}
        }, EPSILON));
        assertThat(out.getBias("layer4"), matrixCloseTo(new float[][]{
                {post_b40, post_b41}
        }, EPSILON));
        assertThat(out.getWeightsTrace("layer4"), matrixCloseTo(new float[][]{
                {post_ew400, post_ew401},
                {post_ew410, post_ew411},
        }, EPSILON));
        assertThat(out.getWeights("layer4"), matrixCloseTo(new float[][]{
                {post_w400, post_w401},
                {post_w410, post_w411},
        }, EPSILON));
    }
}
