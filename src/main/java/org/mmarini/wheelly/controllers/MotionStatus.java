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

package org.mmarini.wheelly.controllers;

import org.mmarini.wheelly.robots.MotionStatusId;
import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public record MotionStatus(MotionStatusId status, Angle targetDir, Point2D target) {
    static final Point2D ORIGIN = new Point2D.Double();
    static final MotionStatus HALT = new MotionStatus(MotionStatusId.HALT, Angle.DEG0, ORIGIN);

    public static MotionStatus backward(Point2D target) {
        return new MotionStatus(MotionStatusId.BACKWARD, Angle.DEG0, target);
    }

    public static MotionStatus forward(Point2D target) {
        return new MotionStatus(MotionStatusId.FORWARD, Angle.DEG0, target);
    }

    public static MotionStatus halt() {
        return HALT;
    }

    public static MotionStatus rotate(Angle direction) {
        return new MotionStatus(MotionStatusId.ROTATE, direction, ORIGIN);
    }

    public MotionStatus {
        requireNonNull(status);
        requireNonNull(target);
        requireNonNull(targetDir);
    }

    @Override
    public String toString() {
        return switch (status) {
            case HALT -> "ha";
            case ROTATE -> format("rt %d", this.targetDir.toIntDeg());
            case FORWARD -> format("fw %.2f,%.2f", target.getX(), target.getY());
            case BACKWARD -> format("bw %.2f,%.2f", target.getX(), target.getY());
        };
    }

}
