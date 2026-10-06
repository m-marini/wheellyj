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

import java.awt.geom.Point2D;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public record MotionStatus(MotionStatusId status, int targetDir, Point2D target) {
    static final Point2D ORIGIN = new Point2D.Double();
    static final MotionStatus HALT = new MotionStatus(MotionStatusId.HALT, 0, ORIGIN);

    public static MotionStatus backward(Point2D target) {
        return new MotionStatus(MotionStatusId.BACKWARD, 0, target);
    }

    public static MotionStatus forward(Point2D target) {
        return new MotionStatus(MotionStatusId.FORWARD, 0, target);
    }

    public static MotionStatus halt() {
        return HALT;
    }

    public static MotionStatus rotate(int direction) {
        return new MotionStatus(MotionStatusId.ROTATE, direction, ORIGIN);
    }

    public MotionStatus {
        requireNonNull(status);
        requireNonNull(target);
    }

    @Override
    public String toString() {
        return switch (status) {
            case HALT -> "ha";
            case ROTATE -> format("rt %d", this.targetDir);
            case FORWARD -> format("fw %.2f,%.2f", target.getX(), target.getY());
            case BACKWARD -> format("bw %.2f,%.2f", target.getX(), target.getY());
        };
    }

    /**
     * Defines the operational movement profiles and behaviour states
     * of the robot chassis.
     */
    public enum MotionStatusId {
        /**
         * The robot chassis is fully halted and stationary.
         */
        HALT,

        /**
         * The robot chassis is actively moving in a forward direction.
         */
        FORWARD,

        /**
         * The robot chassis is actively moving in a backward direction.
         */
        BACKWARD,

        /**
         * The robot chassis is actively rotating around its center point.
         */
        ROTATE
    }
}
