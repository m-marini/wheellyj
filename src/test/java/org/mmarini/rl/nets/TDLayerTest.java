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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.text;


class TDLayerTest {
    private static final long SEED = 1234;

    @Test
    void createConcat() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: concat"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDConcat.class));
    }

    @Test
    void createDense() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: dense",
                "inputSize: 2",
                "outputSize: 3"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDDense.class));
    }

    @Test
    void createLinear() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: linear",
                "b: 0.4",
                "w: 1.5"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDLinear.class));
        assertEquals(0.4f, ((TDLinear) layer).getB());
        assertEquals(1.5f, ((TDLinear) layer).getW());
    }

    @Test
    void createRelu() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: relu"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDRelu.class));
    }

    @Test
    void createSoftmax() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: softmax",
                "temperature: 0.4"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDSoftmax.class));
        assertEquals(0.4f, ((TDSoftmax) layer).getTemperature());
    }

    @Test
    void createSum() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: sum"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDSum.class));
    }

    @Test
    void createTanh() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: tanh"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "", Map.of(), random);
        assertThat(layer, Matchers.isA(TDTanh.class));
    }

    @Test
    void loadDense() throws IOException {
        String yaml = text(
                "---",
                "name: name",
                "type: dense",
                "inputSize: 2",
                "outputSize: 3"
        );
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        INDArray b = Nd4j.randn(1, 3);
        INDArray w = Nd4j.randn(2, 3);
        Map<String, INDArray> ata = Map.of(
                "net.name.b", b,
                "net.name.w", w
        );
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        TDLayer layer = TDLayer.create(root, locator, "net", ata, random);
        assertThat(layer, Matchers.isA(TDDense.class));
        assertEquals(b, ((TDDense) layer).getB());
        assertEquals(w, ((TDDense) layer).getW());
    }

}