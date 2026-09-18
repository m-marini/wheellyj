/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.envs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseMacroActionTest {
    public static final int COMMITMENT_TIME = 10;

    MockMacroActionContext ctx;
    private WorldModelBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.ctx = new MockMacroActionContext();
    }


    @Test
    void testHalt() {
        // Given a halt action
        MacroAction action = MacroAction.haltAction();

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then command should halt the robot
        assertTrue(cmd.isHalt());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @ParameterizedTest
    @CsvSource({
            "0.1"
    })
    void testMicroAction(double distance) {
        // Given a micro action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        Point2D targetPosition = robotStatus.direction().at(robotStatus.location(), distance);
        MicroAction action = new MicroAction(COMMITMENT_TIME, targetPosition);

        // When executing the action for the first time
        RobotCommands cmd = action.executeAction(ctx, builder.build());
        assertEquals(distance >= 0
                        ? RobotStatusId.FORWARD
                        : RobotStatusId.BACKWARD,
                cmd.status());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }
}