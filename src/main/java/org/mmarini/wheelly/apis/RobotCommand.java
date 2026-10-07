/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import org.mmarini.wheelly.envs.EnvAction;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;

public record RobotCommand(MotionStatus motionStatus, HeadStatus headStatus) implements EnvAction {
    private static final RobotCommand HALT = new RobotCommand(MotionStatus.halt(), HeadStatus.lookStraight());

    public static RobotCommand backward(Point2D motionTarget, Complex headAngle) {
        return new RobotCommand(MotionStatus.backward(motionTarget), HeadStatus.scan(headAngle));
    }

    public static RobotCommand backward(Point2D motionTarget) {
        return new RobotCommand(MotionStatus.backward(motionTarget), HeadStatus.lookStraight());
    }

    public static RobotCommand forward(Point2D motionTarget, Complex headAngle) {
        return new RobotCommand(MotionStatus.forward(motionTarget), HeadStatus.scan(headAngle));
    }

    public static RobotCommand forward(Point2D motionTarget) {
        return new RobotCommand(MotionStatus.forward(motionTarget), HeadStatus.lookStraight());
    }

    public static RobotCommand halt() {
        return HALT;
    }

    public static RobotCommand halt(Complex headAngle) {
        return new RobotCommand(MotionStatus.halt(), HeadStatus.scan(headAngle));
    }

    public static RobotCommand rotate(Complex robotMarkerDir, Complex headAngle) {
        return new RobotCommand(MotionStatus.rotate(robotMarkerDir), HeadStatus.scan(headAngle));
    }

    public static RobotCommand rotate(Complex robotMarkerDir) {
        return new RobotCommand(MotionStatus.rotate(robotMarkerDir), HeadStatus.lookStraight());
    }

    public RobotCommand {
        requireNonNull(motionStatus);
        requireNonNull(headStatus);
    }
}
