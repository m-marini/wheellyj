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

/**
 * Store the command parameters for the required robot state
 *
 * @param status            the status
 * @param moveTarget        the target location
 * @param rotationDirection the rotation direction (DEG)
 * @param headStatus
 * @param scanDirection     the scan direction (DEG)
 * @param headTarget
 */
public record RobotCommands(MotionStatus.MotionStatusId status, Point2D moveTarget, int rotationDirection,
                            HeadStatus.HeadStatusId headStatus, int scanDirection,
                            Point2D headTarget) implements EnvAction {

    static RobotCommands HALT = new RobotCommands(MotionStatus.MotionStatusId.HALT, new Point2D.Double(),
            0, HeadStatus.HeadStatusId.FIX_DIRECTION, 0, new Point2D.Double());

    public static RobotCommands halt() {
        return HALT;
    }

    public RobotCommands {
        requireNonNull(status);
        requireNonNull(moveTarget);
        requireNonNull(headStatus);
        requireNonNull(headTarget);
    }

    /**
     * Returns true if halt command
     */
    public boolean isHalt() {
        return MotionStatus.MotionStatusId.HALT.equals(status);
    }

    /**
     * Returns true if rotate command
     */
    public boolean isRotate() {
        return MotionStatus.MotionStatusId.ROTATE.equals(status);
    }
}
