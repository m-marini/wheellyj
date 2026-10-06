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

public record HeadStatus(HeadStatusId status, int direction, Point2D target) {
    static final Point2D ORIGIN = new Point2D.Double();
    static final HeadStatus LOOK_STRAIGHT = new HeadStatus(HeadStatusId.FIX_DIRECTION, 0, ORIGIN);

    public static HeadStatus lookStraight() {
        return LOOK_STRAIGHT;
    }

    public static HeadStatus scan(int direction) {
        return new HeadStatus(HeadStatusId.FIX_DIRECTION, direction, ORIGIN);
    }

    public static HeadStatus trackFrontFace(Point2D target) {
        return new HeadStatus(HeadStatusId.FRONT_TRACK, 0, target);
    }

    public static HeadStatus trackRearFace(Point2D target) {
        return new HeadStatus(HeadStatusId.REAR_TRACK, 0, target);
    }

    public HeadStatus {
        requireNonNull(status);
        requireNonNull(target);
    }

    @Override
    public String toString() {
        return switch (status) {
            case FIX_DIRECTION -> format("sc %d", this.direction);
            case FRONT_TRACK -> format("ft %.2f,%.2f", target.getX(), target.getY());
            case REAR_TRACK -> format("rt %.2f,%.2f", target.getX(), target.getY());
        };
    }

    /**
     * Defines the operational tracking profiles and behaviour states
     * of the robot sensor head.
     */
    public enum HeadStatusId {
        /**
         * The sensor head is locked, maintaining a fixed absolute direction.
         */
        FIX_DIRECTION,

        /**
         * The sensor head is actively tracking a targeted position from the front.
         */
        FRONT_TRACK,

        /**
         * The sensor head is actively tracking a targeted position from the rear.
         */
        REAR_TRACK,
    }
}
