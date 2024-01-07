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
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.yaml.Locator;
import org.mockito.InOrder;

import java.io.IOException;

import static org.mmarini.wheelly.TestFunctions.text;
import static org.mmarini.yaml.Utils.fromText;
import static org.mockito.Mockito.*;

class ProcessorCommandTest {


    static final String YAML = text("---",
            "- a.1",
            "- 1",
            "- put",
            "- a.2",
            "- a.1",
            "- get",
            "- 2",
            "- add",
            "- put");

    @Test
    void add() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("add");
        ProcessorContext ctx = mock();
        when(ctx.popDouble()).thenReturn(2D, 1D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx, times(2)).popDouble();
        inOrder.verify(ctx).push(3D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void constDouble() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("1.1");
        ProcessorContext ctx = mock();

        cmd.execute(ctx);

        verify(ctx, only()).push(1.1);
    }

    @Test
    void constInt() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("1");
        ProcessorContext ctx = mock();

        cmd.execute(ctx);

        verify(ctx, only()).push(1D);
    }

    @Test
    void constString() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("a.b");
        ProcessorContext ctx = mock();

        cmd.execute(ctx);

        verify(ctx, only()).push("a.b");
    }

    @Test
    void create() throws IOException {
        JsonNode root = fromText(YAML);
        ProcessorCommand cmd = ProcessorCommand.create(root, Locator.root());
        ProcessorContext ctx = mock();
        when(ctx.pop()).thenReturn(1D, 3D);
        when(ctx.popDouble()).thenReturn(1D, 2D);
        when(ctx.popString()).thenReturn("a.1", "a.1", "a.2");
        when(ctx.get("a.1")).thenReturn(1D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx).push("a.1");
        inOrder.verify(ctx).push(1D);
        inOrder.verify(ctx).pop();
        inOrder.verify(ctx).popString();
        inOrder.verify(ctx).put("a.1", 1D);
        inOrder.verify(ctx).push("a.2");
        inOrder.verify(ctx).push("a.1");
        inOrder.verify(ctx).popString();
        inOrder.verify(ctx).get("a.1");
        inOrder.verify(ctx).push(1D);
        inOrder.verify(ctx).push(2D);
        inOrder.verify(ctx, times(2)).popDouble();
        inOrder.verify(ctx).push(3D);
        inOrder.verify(ctx).pop();
        inOrder.verify(ctx).popString();
        inOrder.verify(ctx).put("a.2", 3D);
        inOrder.verify(ctx).stackSize();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void div() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("div");
        ProcessorContext ctx = mock();
        when(ctx.popDouble()).thenReturn(2D, 3D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx, times(2)).popDouble();
        inOrder.verify(ctx).push(1.5D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void get() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("get");
        ProcessorContext ctx = mock();
        when(ctx.get("a.b")).thenReturn(1D);
        when(ctx.popString()).thenReturn("a.b");

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx).popString();
        inOrder.verify(ctx).push(1D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void mul() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("mul");
        ProcessorContext ctx = mock();
        when(ctx.popDouble()).thenReturn(3.5, 2D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx, times(2)).popDouble();
        inOrder.verify(ctx).push(7D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void neg() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("neg");
        ProcessorContext ctx = mock();
        when(ctx.popDouble()).thenReturn(-1D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx).popDouble();
        inOrder.verify(ctx).push(1D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void put() {
        ProcessorCommand cmd = ProcessorCommand.parse("a.b", "1", "put");
        ProcessorContext ctx = mock();
        when(ctx.pop()).thenReturn(1D);
        when(ctx.popString()).thenReturn("a.b");

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx).push("a.b");
        inOrder.verify(ctx).push(1D);
        inOrder.verify(ctx).pop();
        inOrder.verify(ctx).popString();
        inOrder.verify(ctx).put("a.b", 1D);
        inOrder.verify(ctx).stackSize();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void sub() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("sub");
        ProcessorContext ctx = mock();
        when(ctx.popDouble()).thenReturn(2D, 1D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx, times(2)).popDouble();
        inOrder.verify(ctx).push(-1D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void swap() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("swap");
        ProcessorContext ctx = mock();
        when(ctx.pop()).thenReturn(2D, 1D);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx);
        inOrder.verify(ctx, times(2)).pop();
        inOrder.verify(ctx).push(2D);
        inOrder.verify(ctx).push(1D);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void time() {
        ProcessorCommand cmd = ProcessorCommand.parseCommand("localTime");
        ProcessorContext ctx = mock();

        RobotStatus status = mock();
        when(status.simulationTime()).thenReturn(100L);

        when(ctx.robotStatus()).thenReturn(status);

        cmd.execute(ctx);

        InOrder inOrder = inOrder(ctx, status);
        inOrder.verify(status).simulationTime();
        inOrder.verify(ctx).push(100L);
        inOrder.verifyNoMoreInteractions();
    }
}