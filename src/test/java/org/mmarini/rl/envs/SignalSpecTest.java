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

package org.mmarini.rl.envs;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mmarini.yaml.Locator;

import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;

class SignalSpecTest {

    private static final String YAML = text("---",
            "a:",
            "  type: int",
            "  shape: [1, 2]",
            "  numValues: 2",
            "b:",
            "  type: float",
            "  shape: [2, 1]",
            "  minValue: 0",
            "  maxValue: 1"
    );

    @Test
    void load() throws IOException {
        JsonNode node = fromText(YAML);

        Map<String, SignalSpec> spec = SignalSpec.createSignalSpecMap(node, Locator.root());

        SignalSpec a = spec.get("a");
        assertThat(a, isA(IntSignalSpec.class));
        assertEquals(2, ((IntSignalSpec) a).numValues());
        assertArrayEquals(new long[]{1, 2}, a.shape());

        SignalSpec b = spec.get("b");
        assertThat(b, isA(FloatSignalSpec.class));
        assertEquals(0f, ((FloatSignalSpec) b).minValue());
        assertEquals(1f, ((FloatSignalSpec) b).maxValue());
        assertArrayEquals(new long[]{2, 1}, b.shape());
    }
}