/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

/**
 *
 */
public interface ContactSensors extends RobotAsset {
    int FRONT_LEFT_MASK = 8;
    int FRONT_RIGHT_MASK = 4;
    int REAR_LEFT_MASK = 2;
    int REAR_RIGHT_MASK = 1;
    int REAR_MASK = REAR_LEFT_MASK | REAR_RIGHT_MASK;
    int FRONT_MASK = FRONT_LEFT_MASK | FRONT_RIGHT_MASK;
    int LEFT_MASK = FRONT_LEFT_MASK | REAR_LEFT_MASK;
    int RIGHT_MASK = FRONT_RIGHT_MASK | REAR_RIGHT_MASK;
    int[] MASK_LIST = {FRONT_MASK, FRONT_RIGHT_MASK, LEFT_MASK, REAR_LEFT_MASK, REAR_MASK, REAR_RIGHT_MASK, RIGHT_MASK, FRONT_RIGHT_MASK};
    double X_FRONT = 0.1;
    double X_REAR = -0.16;
    double Y_LEFT = -0.09;
    double Y_RIGHT = 0.09;
    Point2D[] RELATIVE_CONTACT_LOCATIONS = {
            new Point2D.Double(X_FRONT, 0),     // N
            new Point2D.Double(X_FRONT, Y_RIGHT), // NE
            new Point2D.Double(0, Y_RIGHT),     // E
            new Point2D.Double(X_REAR, Y_RIGHT),  // SE
            new Point2D.Double(X_REAR, 0),      // S
            new Point2D.Double(X_REAR, Y_LEFT),   // SW
            new Point2D.Double(0, Y_LEFT),      // W
            new Point2D.Double(X_FRONT, Y_LEFT),  // NW
    };

    boolean getCannotMoveBackward();

    boolean getCannotMoveForward();

    default List<Point2D> getContactObstacles() {
        List<Point2D> result = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            int idx = dir.ordinal();
            if (isContact(MASK_LIST[idx])) {
                result.add(getRelativeContact(dir));
            }
        }
        return result;
    }

    int getContactSensors();

    default Point2D getRelativeContact(Direction direction) {
        double robotRad = getRobotRad();
        double sa = sin(robotRad);
        double ca = cos(robotRad);
        Point2D robotLocation = getRobotLocation();
        double x = robotLocation.getX();
        double y = robotLocation.getY();
        Point2D relPt = RELATIVE_CONTACT_LOCATIONS[direction.ordinal()];
        return new Point2D.Double(x + relPt.getX() * ca - relPt.getY() * sa,
                y + relPt.getX() * sa + relPt.getY() * ca);
    }

    default boolean isBlocked() {
        return getCannotMoveBackward() && getCannotMoveForward();
    }

    default boolean isContact(int mask) {
        return (getContactSensors() & mask) != 0;
    }

    enum Direction {NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST}
}
