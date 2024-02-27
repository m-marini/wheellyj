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
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;


class TDLayerTest {
    @Test
    void createConcat() throws IOException {
        String yaml = """
                ---
                name: name
                type: concat
                inputs: [input]
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDConcat.class));
    }

    @Test
    void createDense() throws IOException {
        String yaml = """
                ---
                name: name
                type: dense
                inputs: [input]
                maxAbsWeights: 10
                dropOut: 10
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDDense.class));
    }

    @Test
    void createDropOut() throws IOException {
        String yaml = """
                ---
                name: name
                type: dropout
                inputs: [input]
                dropOut: 0.5
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDDropOut.class));
        assertEquals(0.5F, ((TDDropOut) layer).dropOut());
    }

    @Test
    void createLinear() throws IOException {
        String yaml = """
                ---
                name: name
                type: linear
                inputs: [input]
                b: 0.4
                w: 1.5
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDLinear.class));
        assertEquals(0.4f, ((TDLinear) layer).bias());
        assertEquals(1.5f, ((TDLinear) layer).weight());
    }

    @Test
    void createRelu() throws IOException {
        String yaml = """
                ---
                name: name
                type: relu
                inputs: [input]
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDRelu.class));
    }

    @Test
    void createSoftmax() throws IOException {
        String yaml = """
                ---
                name: name
                type: softmax
                inputs: [input]
                temperature: 0.4
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDSoftmax.class));
        assertEquals(0.4f, ((TDSoftmax) layer).temperature());
    }

    @Test
    void createSum() throws IOException {
        String yaml = """
                ---
                name: name
                type: sum
                inputs: [input]
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDSum.class));
    }

    @Test
    void createTanh() throws IOException {
        String yaml = """
                ---
                name: name
                type: tanh
                inputs: [input]
                """;
        JsonNode root = Utils.fromText(yaml);
        Locator locator = Locator.root();
        TDLayer layer = TDLayer.fromJson(root, locator);
        assertThat(layer, Matchers.isA(TDTanh.class));
    }
}