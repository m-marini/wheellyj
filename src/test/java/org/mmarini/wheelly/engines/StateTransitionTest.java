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
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;
import static rocks.cleancode.hamcrest.record.HasFieldMatcher.field;

class StateTransitionTest {

    private static final String YAML = text("---",
            "trigger: armed",
            "from: a",
            "to: b",
            "onTransition:",
            "  - a.b",
            "  - 1",
            "  - put");

    private static final String LIST_YAML = text("---",
            "- trigger: armed",
            "  from: a",
            "  to: b",
            "- trigger: armed",
            "  from: b",
            "  to: c");

    @Test
    void create() throws IOException {
        JsonNode root = fromText(YAML);
        StateTransition st = StateTransition.create(root, Locator.root());
        assertNotNull(st);
        assertEquals("a", st.from());
        assertEquals("b", st.to());
        assertTrue(st.isTriggered("armed"));
        assertThat(st, field("onTransition",
                field("id", equalTo("@program"))));
    }

    @Test
    void createList() throws IOException {
        JsonNode root = fromText(LIST_YAML);
        List<StateTransition> st = StateTransition.createList(root, Locator.root());
        assertThat(st, hasSize(2));

        assertThat(st.get(0), field("from", equalTo("a")));
        assertThat(st.get(0), field("to", equalTo("b")));
        assertTrue(st.get(0).isTriggered("armed"));
        assertThat(st.get(0), field("onTransition", nullValue()));

        assertThat(st.get(1), field("from", equalTo("b")));
        assertThat(st.get(1), field("to", equalTo("c")));
        assertTrue(st.get(1).isTriggered("armed"));
        assertThat(st.get(1), field("onTransition", nullValue()));
    }
}