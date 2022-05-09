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
import java.util.Optional;
import java.util.StringJoiner;

import static java.lang.Math.*;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;

/**
 *
 */
public class ProxySample {
    public static final int FRONT_LEFT_MASK = 8;
    public static final int FRONT_RIGHT_MASK = 4;
    public static final int REAR_LEFT_MASK = 2;
    public static final int REAR_RIGHT_MASK = 1;
    public static final int REAR_MASK = REAR_LEFT_MASK | REAR_RIGHT_MASK;
    public static final int FRONT_MASK = FRONT_LEFT_MASK | FRONT_RIGHT_MASK;
    public static final int LEFT_MASK = FRONT_LEFT_MASK | REAR_LEFT_MASK;
    public static final int RIGHT_MASK = FRONT_RIGHT_MASK | REAR_RIGHT_MASK;
    private static final int[] MASK_LIST = {FRONT_MASK, FRONT_RIGHT_MASK, LEFT_MASK, REAR_LEFT_MASK, REAR_MASK, REAR_RIGHT_MASK, RIGHT_MASK, FRONT_RIGHT_MASK};
    private static final double X_FRONT = 0.1;
    private static final double X_REAR = -0.16;
    private static final double Y_LEFT = -0.09;
    private static final double Y_RIGHT = 0.09;
    private static Point2D[] RELATIVE_CONTACT_LOCATIONS = {
            new Point2D.Double(X_FRONT, 0),     // N
            new Point2D.Double(X_FRONT, Y_RIGHT), // NE
            new Point2D.Double(0, Y_RIGHT),     // E
            new Point2D.Double(X_REAR, Y_RIGHT),  // SE
            new Point2D.Double(X_REAR, 0),      // S
            new Point2D.Double(X_REAR, Y_LEFT),   // SW
            new Point2D.Double(0, Y_LEFT),      // W
            new Point2D.Double(X_FRONT, Y_LEFT),  // NW
    };

    /**
     * @param relativeDegDirection relative direction DEG
     * @param distance             distance (m)
     * @param robotAsset           asset
     * @param contactSensors       the contact sensors
     * @param canMoveForward       true if robot can move forward
     * @param canMoveBackward      true if robot can move backward
     */
    public static ProxySample create(int relativeDegDirection, double distance,
                                     RobotAsset robotAsset,
                                     int contactSensors, boolean canMoveBackward, boolean canMoveForward) {
        return new ProxySample(robotAsset, relativeDegDirection, distance, contactSensors, canMoveForward, canMoveBackward);
    }

    public final boolean canMoveBackward;
    public final boolean canMoveForward;
    public final int contactSensors;
    public final double distance;
    public final RobotAsset robotAsset;
    public final int sensorRelativeDeg;

    /**
     * @param robotAsset        asset
     * @param sensorRelativeDeg relative direction DEG
     * @param distance          distance (m)
     * @param contactSensors    the contact sensors
     * @param canMoveForward    true if robot can move forward
     * @param canMoveBackward   true if robot can move backward
     */
    protected ProxySample(RobotAsset robotAsset, int sensorRelativeDeg, double distance, int contactSensors, boolean canMoveForward, boolean canMoveBackward) {
        this.sensorRelativeDeg = sensorRelativeDeg;
        this.distance = distance;
        this.robotAsset = robotAsset;
        this.contactSensors = contactSensors;
        this.canMoveBackward = canMoveBackward;
        this.canMoveForward = canMoveForward;
    }

    public List<Point2D> getContactObstacles() {
        List<Point2D> result = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            int idx = dir.ordinal();
            if (isContact(MASK_LIST[idx])) {
                result.add(getRelativeContact(dir));
            }
        }
        return result;
    }

    public double getDistance() {
        return distance;
    }

    /**
     * Returns the absolute location of the reflector if any
     */
    public Optional<Point2D> getLocation() {
        if (distance > 0) {
            Point2D location = robotAsset.getLocation();
            double angle = robotAsset.getDirectionRad() + toRadians(sensorRelativeDeg);
            double x = location.getX() + distance * cos(angle);
            double y = location.getY() + distance * sin(angle);
            return Optional.of(new Point2D.Double(x, y));
        } else {
            return Optional.empty();
        }
    }

    public Point2D getRelativeContact(Direction direction) {
        double sa = sin(robotAsset.getDirectionRad());
        double ca = cos(robotAsset.getDirectionRad());
        double x = robotAsset.location.getX();
        double y = robotAsset.location.getY();
        Point2D relPt = RELATIVE_CONTACT_LOCATIONS[direction.ordinal()];
        return new Point2D.Double(x + relPt.getX() * ca - relPt.getY() * sa,
                y + relPt.getX() * sa + relPt.getY() * ca);
    }

    public RobotAsset getRobotAsset() {
        return robotAsset;
    }

    public double getSampleDeg() {
        return normalizeDegAngle(robotAsset.directionDeg + sensorRelativeDeg);
    }

    /**
     * Returns the sample direction in RAD
     */
    public double getSampleRad() {
        return toRadians(getSampleDeg());
    }

    /**
     *
     */
    public int getSensorRelativeDeg() {
        return sensorRelativeDeg;
    }

    public boolean isContact(int mask) {
        return (contactSensors & mask) != 0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ProxySample.class.getSimpleName() + "[", "]")
                .add("sensorDeg=" + sensorRelativeDeg)
                .add("distance=" + distance)
                .toString();
    }

    public enum Direction {N, NE, E, SE, S, SW, W, NW}
}
