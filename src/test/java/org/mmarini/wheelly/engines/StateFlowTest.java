/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.mmarini.yaml.Locator;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.yaml.Utils.fromText;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class StateFlowTest {

    private static final String YAML = """
            ---
            entry: a
            states:
              a:
                $id: https://mmarini.org/wheelly/state-halt-schema-0.1
                class: org.mmarini.wheelly.engines.HaltState
                transitions:
                    ".*":
                      to: b
                      onTransition:
                        - a
                        - b
                        - get
                        - put
              b:
                $id: https://mmarini.org/wheelly/state-halt-schema-0.1
                class: org.mmarini.wheelly.engines.HaltState
            onInit:
              - a
              - 1
              - put
            """;

    @Test
    void create() throws IOException {
        JsonNode root = fromText(YAML);

        StateFlow sf = StateFlow.create(root, Locator.root());

        assertThat(sf, field("states", hasSize(2)));
        assertNotNull(sf.getState("a"));
        assertNotNull(sf.getState("b"));
        ProcessorCommand onInit = sf.onInit();
        assertNotNull(onInit);
        assertEquals("@program", onInit.id());
        assertThat(sf.transitions(), hasSize(1));
    }
}