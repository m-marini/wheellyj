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

package org.mmarini.wheelly.fsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.wheelly.apis.*;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Utils.MM;

class BaseMacroActionTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final double SAFETY_DISTANCE = 0.5;

    MockMacroActionContext ctx;
    private WorldModelBuilder builder;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.ctx = new MockMacroActionContext();
    }
    /*

    @Test
    void testMoveSafetyBackward() {
        // Given a move to safety action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        double safetyDistance = SAFETY_DISTANCE;
        Point2D safetyTarget1 = robotStatus.direction().at(robotStatus.location(), -safetyDistance);
        MoveSafetyAction action = new MoveSafetyAction(COMMITMENT_TIME, safetyDistance);

        // When executing the action for the first time with front contact
        RobotCommands cmd = action.execute(ctx, builder
                .canMoveForward(false)
                .build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(safetyTarget1, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot at commitment time without changes
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME).build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(safetyTarget1, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action with robot moved back but still in contact
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME)
                .backward(SAFETY_DISTANCE)
                .build());

        // Then robot should move backward
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        Point2D safetyTarget2 = robotStatus.direction().at(robotStatus.location(), -safetyDistance);
        assertThat(cmd.target(), pointCloseTo(safetyTarget2, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action without contact
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME)
                .canMoveForward(true)
                .build());

        // Then robot should move backward
        assertEquals(RobotStatusId.BACKWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(safetyTarget2, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action without contact to safety target
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME)
                .canMoveForward(true)
                .backward(safetyDistance)
                .build());

        // Then robot should move backward
        assertEquals(RobotStatusId.HALT, cmd.status());
       // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

    }

    @Test
    void testMicroActionForward() {
        // Given a micro action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        double distance = robotStatus.robotSpec().targetRange() * 2;
        Point2D targetPosition = robotStatus.direction().at(robotStatus.location(), distance);
        MicroAction action = new MicroAction(COMMITMENT_TIME, targetPosition);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot at commitment time
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME).build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action with robot after commitment time and robot in target range
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME)
                .forward(distance)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @Test
    void testMicroActionForwardCompleted() {
        // Given a micro action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        double distance = robotStatus.robotSpec().targetRange() * 2;
        Point2D targetPosition = robotStatus.direction().at(robotStatus.location(), distance);
        MicroAction action = new MicroAction(COMMITMENT_TIME, targetPosition);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot before commitment time and robot in target range
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME / 2)
                .forward(distance)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @Test
    void testMicroActionFrontContact() {
        // Given a micro action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        double distance = robotStatus.robotSpec().targetRange() * 2;
        Point2D targetPosition = robotStatus.direction().at(robotStatus.location(), distance);
        MicroAction action = new MicroAction(COMMITMENT_TIME, targetPosition);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot before commitment time and contact
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME / 2)
                .canMoveForward(false)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @Test
    void testMicroActionRearContact() {
        // Given a micro action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        double distance = robotStatus.robotSpec().targetRange() * 2;
        Point2D targetPosition = robotStatus.direction().at(robotStatus.location(), distance);
        MicroAction action = new MicroAction(COMMITMENT_TIME, targetPosition);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.FORWARD, cmd.status());
        assertThat(cmd.target(), pointCloseTo(targetPosition, MM));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot before commitment time and contact
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME / 2)
                .canMoveBackward(false)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @ParameterizedTest
    @ValueSource(ints = {90, -180, -90})
    void testRotation(int targetDir) {
        // Given a rotate action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        int directionRange = robotStatus.robotSpec().directionRange().toIntDeg();
        RotateAction action = new RotateAction(COMMITMENT_TIME, targetDir);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir, directionRange));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot at commitment time
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME).build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir, directionRange));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());

        // When executing the action with robot after commitment time and robot in target range
        ctx.clearRequests();
        cmd = action.execute(ctx, builder.addTime(COMMITMENT_TIME)
                .robotDir(targetDir)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertTrue(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @ParameterizedTest
    @ValueSource(ints = {90, -180, -90})
    void testRotationFrontContact(int targetDir) {
        // Given a rotate action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        int directionRange = robotStatus.robotSpec().directionRange().toIntDeg();
        RotateAction action = new RotateAction(COMMITMENT_TIME, targetDir);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir, directionRange));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot before commitment time with contact
        ctx.clearRequests();
        cmd = action.execute(ctx, builder
                .addTime(COMMITMENT_TIME / 2)
                .canMoveForward(false)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

    @ParameterizedTest
    @ValueSource(ints = {90, -180, -90})
    void testRotationRearContact(int targetDir) {
        // Given a rotate action
        WorldModel world0 = builder.build();
        RobotStatus robotStatus = world0.robotStatus();
        int directionRange = robotStatus.robotSpec().directionRange().toIntDeg();
        RotateAction action = new RotateAction(COMMITMENT_TIME, targetDir);

        // When executing the action for the first time
        RobotCommands cmd = action.execute(ctx, builder.build());

        // Then the command should be forward to target position
        assertEquals(RobotStatusId.ROTATE, cmd.status());
        assertThat(Complex.fromDeg(cmd.rotationDirection()), angleCloseTo(targetDir, directionRange));
        // And action should not have been completed
        assertFalse(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(0, ctx.requestNextActionNum());

        // When executing the action with robot before commitment time with contact
        ctx.clearRequests();
        cmd = action.execute(ctx, builder
                .addTime(COMMITMENT_TIME / 2)
                .canMoveBackward(false)
                .build());

        // Then the command should be forward to target position
        assertTrue(cmd.isHalt());
        // And action should not have been completed
        assertTrue(action.completed());
        // And action should not have been expired
        assertFalse(action.expired());
        // And no next action should have been required
        assertEquals(1, ctx.requestNextActionNum());
    }

     */
}