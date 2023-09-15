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
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.apis.MockRobot;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;
import static org.mockito.Mockito.mock;

class ProcessorCommandTest {


    public static final long TIME = 4321L;
    static final String YAML = text("---",
            "- a.1",
            "- 1",
            "- put",
            "- a.2",
            "- a.1",
            "- get",
            "- 2",
            "- add",
            "- put"
    );

    static RobotControllerApi createController() {
        return mock();
    }

    static StateFlow createFlow() {
        StateNode entry = new HaltState("entry", null, null, null);
        List<StateNode> states = List.of(entry);
        List<StateTransition> transitions = List.of();
        return new StateFlow(states, transitions, entry, null);
    }

    static RobotApi createRobot() {
        MockRobot mockRobot = new MockRobot();
        mockRobot.setTime(TIME);
        return mockRobot;
    }

    @Test
    void add() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("add");

        ProcessorContext ctx = createContext().push(1D).push(2D);

        cmd.execute(ctx);

        assertEquals(3.0, ctx.peek());
    }

    @Test
    void constDouble() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("1.1");

        ProcessorContext ctx = createContext();

        cmd.execute(ctx);

        assertThat(ctx, hasProperty("stack", hasSize(1)));
        assertEquals(Double.parseDouble("1.1"), ctx.peek());
    }

    @Test
    void constInt() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("1");

        ProcessorContext ctx = createContext();

        cmd.execute(ctx);

        assertThat(ctx, hasProperty("stack", hasSize(1)));
        assertEquals(Double.parseDouble("1"), ctx.peek());
    }

    @Test
    void constString() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("a.b");

        ProcessorContext ctx = createContext();

        cmd.execute(ctx);

        assertThat(ctx, hasProperty("stack", hasSize(1)));
        assertEquals("a.b", ctx.peek());
    }

    @Test
    void create() throws IOException {
        JsonNode root = fromText(YAML);
        ProcessorCommand cmd = ProcessorCommand.create(root, Locator.root());
        ProcessorContext ctx = createContext();

        cmd.execute(ctx);

        assertNull(ctx.peek());
        assertEquals(Double.valueOf(1), ctx.get("a.1"));
        assertEquals(Double.valueOf(3), ctx.get("a.2"));
    }

    @NotNull
    private ProcessorContext createContext() {
        RobotControllerApi controller = createController();
        ProcessorContext processorContext = new ProcessorContext(controller, createFlow());
        RobotStatus status = RobotStatus.create().setTime(TIME);
        processorContext.setRobotStatus(status);
        return processorContext;
    }


    @Test
    void div() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("div");

        ProcessorContext ctx = createContext().push(3D).push(2D);

        cmd.execute(ctx);

        assertEquals(1.5, ctx.peek());
    }

    @Test
    void get() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("get");

        ProcessorContext ctx = createContext().put("a.b", 1.0).push("a.b");

        cmd.execute(ctx);

        assertEquals(1.0, ctx.peek());
    }

    @Test
    void mul() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("mul");

        ProcessorContext ctx = createContext().push(2D).push(3.5);

        cmd.execute(ctx);

        assertEquals(7.0, ctx.peek());
    }

    @Test
    void neg() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("neg");

        ProcessorContext ctx = createContext().push(-1D);

        cmd.execute(ctx);

        assertEquals(1.0, ctx.peek());
    }

    @Test
    void put() {
        ProcessorCommand cmd = ProcessorCommand.parse("a.b", "1", "put");

        ProcessorContext ctx = createContext();

        cmd.execute(ctx);

        assertNull(ctx.peek());
        assertEquals(Double.valueOf(1), ctx.get("a.b"));
    }

    @Test
    void sub() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("sub");

        ProcessorContext ctx = createContext().push(1D).push(2D);

        cmd.execute(ctx);

        assertEquals((double) -1, ctx.peek());
    }

    @Test
    void swap() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("swap");

        ProcessorContext ctx = createContext().push(1D).push(2D);

        cmd.execute(ctx);

        assertThat(ctx, hasProperty("stack",
                contains(2.0, 1.0)
        ));
    }

    @Test
    void time() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("time");

        ProcessorContext ctx = createContext();

        cmd.execute(ctx);
        assertEquals(TIME, ctx.peek());
    }

}