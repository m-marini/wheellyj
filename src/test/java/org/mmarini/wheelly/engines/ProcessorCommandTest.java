/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.apis.WorldModelBuilder;
import org.mmarini.yaml.Locator;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.yaml.Utils.fromText;

class ProcessorCommandTest {


    static final String YAML = """
            ---
            - a.1
            - 1
            - put
            - a.2
            - a.1
            - get
            - 2
            - add
            - put
            """;

    private MockProcessorContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new MockProcessorContext(new WorldModelBuilder().build());
        ctx.push(1).push(2);
        assertThat(ctx.pop(), equalTo(2));
        assertThat(ctx.pop(), equalTo(1));
        assertEquals(0, ctx.stackSize());
    }

    @Test
    void testAdd() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("add");
        ctx.push(1D)
                .push(2D);

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(3D));
    }

    @Test
    void testConstDouble() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("1.1");

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(1.1));
    }

    @Test
    void testConstInt() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("1");

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(1.0));
    }

    @Test
    void testConstString() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("a.b");

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo("a.b"));
    }

    @Test
    void testCreate() throws IOException {
        JsonNode root = fromText(YAML);
        ProcessorCommand cmd = ProcessorCommand.create(root, Locator.root());

        cmd.execute(ctx);

        assertEquals(0, ctx.stackSize());
        assertThat(ctx.get("a.1"), equalTo(1D));
        assertThat(ctx.get("a.2"), equalTo(3D));
    }

    @Test
    void testDiv() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("div");
        ctx.push(3D).push(2D);

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(1.5));
    }

    @Test
    void testGet() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("get");
        ctx.put("a.b", 1D)
                .push("a.b");

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(1D));
    }

    @Test
    void testMul() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("mul");
        ctx.push(3.5D).push(2D);

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(7D));
    }

    @Test
    void testNeg() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("neg");
        ctx.push(-1D);

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(1D));
    }

    @Test
    void testPut() {
        ProcessorCommand cmd = ProcessorCommand.parse("a.b", "1", "put");

        cmd.execute(ctx);

        assertEquals(0, ctx.stackSize());
        assertThat(ctx.get("a.b"), equalTo(1D));
    }

    @Test
    void testSub() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("sub");
        ctx.push(1D)
                .push(2D);

        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.peek(), equalTo(-1D));
    }

    @Test
    void testSwap() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("swap");
        ctx.push(1D).push(2D);

        cmd.execute(ctx);

        assertEquals(2, ctx.stackSize());
        assertThat(ctx.pop(), equalTo(1D));
        assertThat(ctx.pop(), equalTo(2D));
    }

    @Test
    void testTime() {
        // Given ...
        ProcessorCommand cmd = ProcessorCommand.parseCommand("localTime");

        // When ...
        cmd.execute(ctx);

        assertEquals(1, ctx.stackSize());
        assertThat(ctx.pop(), equalTo(1L));
    }
}