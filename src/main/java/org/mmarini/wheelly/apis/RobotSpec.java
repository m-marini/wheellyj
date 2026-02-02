/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Locator;

import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

import static java.lang.Math.PI;
import static java.util.Objects.requireNonNull;


/**
 * Robot specification (geometries of robot)
 *
 * @param maxRadarDistance   the maximum radar distance (m)
 * @param lidarFOV           the lidar field of view
 * @param contactRadius      the contact radius (m)
 * @param cameraFOV          the camera field of view
 * @param headLocation       the relative sensor pivot location
 * @param frontLidarDistance the distance of front lidar from head (m)
 * @param rearLidarDistance  the distance of rear lidar from head (m)
 * @param cameraDistance     the distance of camera from head (m)
 * @param headFOV            the head field of view (the head rotation angle limit)
 */
public record RobotSpec(double maxRadarDistance, Complex lidarFOV, double contactRadius,
                        Complex cameraFOV, Point2D headLocation, double frontLidarDistance,
                        double rearLidarDistance, double cameraDistance,
                        Complex headFOV) {

    public static final double MAX_RADAR_DISTANCE = 2.0;
    public static final int DEFAULT_LIDAR_FOV_DEG = 25;
    public static final double DEFAULT_CONTACT_RADIUS = 180e-3;
    public static final int DEFAULT_CAMERA_FOV_DEG = 60;
    public static final double DEFAULT_HEAD_X = 0;
    public static final double DEFAULT_HEAD_Y = 30e-3;
    public static final double DEFAULT_FRONT_LIDAR_DISTANCE = 15e-3;
    public static final double DEFAULT_REAR_LIDAR_DISTANCE = 15e-3;
    public static final double DEFAULT_CAMERA_DISTANCE = 15e-3;
    public static final int DEFAULT_HEAD_FOV_DEG = 130;

    /**
     * Number of pulses per wheel root
     */
    public static final int PULSES_PER_ROOT = 40;
    /**
     * Wheel diameter (m)
     */
    public static final double WHEEL_DIAMETER = 67e-3;
    /**
     * Distance per pulse (m)
     */
    public static final double DISTANCE_PER_PULSE = WHEEL_DIAMETER * PI / PULSES_PER_ROOT;
    /**
     * Unknown qr code
     */
    public static final String UNKNOWN_QR_CODE = "?";
    /**
     * Roboto radius (m)
     */
    public static final float ROBOT_RADIUS = 180e-3f;
    /**
     * Max whells power (pps)
     */
    public static final int MAX_PPS = 60;
    /**
     * Robot track, distance between wheels (m)
     */
    public static final double ROBOT_TRACK = 136e-3;
    public static final int MAX_DIRECTION_ACTION = 180;

    /**
     * Robot mass (Kg)
     */
    public static final double ROBOT_MASS = 0.785;

    /**
     * Default robot specification
     */
    public static final RobotSpec DEFAULT_ROBOT_SPEC = create(MAX_RADAR_DISTANCE, DEFAULT_LIDAR_FOV_DEG,
            DEFAULT_CONTACT_RADIUS, DEFAULT_CAMERA_FOV_DEG, 0, DEFAULT_HEAD_Y,
            DEFAULT_FRONT_LIDAR_DISTANCE, DEFAULT_REAR_LIDAR_DISTANCE, DEFAULT_CAMERA_DISTANCE, DEFAULT_HEAD_FOV_DEG);

    /**
     * Applies the transformation from robot coordinate to absolute coordinate
     *
     * @param trans         the current transformation
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     */
    public static AffineTransform applyRobotView(AffineTransform trans, Point2D robotLocation, Complex robotDir) {
        trans.translate(robotLocation.getX(), robotLocation.getY());
        trans.rotate(-robotDir.toRad());
        return trans;
    }

    /**
     * Creates the robot specification
     *
     * @param maxRadarDistance   the maximum radar distance (m)
     * @param lidarFOVDeg        the lidar field of view (DEG)
     * @param contactRadius      the contact radius (m)
     * @param cameraFOVDeg       the camera field of view (DEG)
     * @param headX              the relative sensor pivot location abscissa
     * @param headY              the relative sensor pivot location ordinate
     * @param frontLidarDistance the distance of front lidar from head (m)
     * @param rearLidarDistance  the distance of rear lidar from head (m)
     * @param cameraDistance     the distance of camera from head (m)
     * @param headFOVDeg         the head field of view (the head rotation angle limit) (DEG)
     */
    public static RobotSpec create(double maxRadarDistance, int lidarFOVDeg, double contactRadius, int cameraFOVDeg,
                                   double headX, double headY, double frontLidarDistance, double rearLidarDistance,
                                   double cameraDistance, int headFOVDeg) {
        return new RobotSpec(maxRadarDistance, Complex.fromDeg(lidarFOVDeg), contactRadius, Complex.fromDeg(cameraFOVDeg),
                new Point2D.Double(headX, headY), frontLidarDistance, rearLidarDistance, cameraDistance,
                Complex.fromDeg(headFOVDeg));
    }

    /**
     * Returns the pulses for the given distance
     *
     * @param distance the distance (m)
     */
    public static double distance2Pulse(double distance) {
        return distance / DISTANCE_PER_PULSE;
    }

    /**
     * Returns the robot specification from JSON configuration
     *
     * @param root    the json document
     * @param locator the locator
     */
    public static RobotSpec fromJson(JsonNode root, Locator locator) {
        int lidarFOV = locator.path("lidarFOV").getNode(root).asInt(DEFAULT_LIDAR_FOV_DEG);
        double maxRadarDistance = locator.path("maxRadarDistance").getNode(root).asDouble();
        int cameraFOV = locator.path("cameraFOV").getNode(root).asInt();
        double contactRadius = locator.path("contactRadius").getNode(root).asDouble();
        int headFOV = locator.path("headFOV").getNode(root).asInt(DEFAULT_HEAD_FOV_DEG);
        double headX = locator.path("headX").getNode(root).asDouble(DEFAULT_HEAD_X);
        double headY = locator.path("headY").getNode(root).asDouble(DEFAULT_HEAD_Y);
        double frontLidarDistance = locator.path("frontLidarDistance").getNode(root).asDouble(DEFAULT_FRONT_LIDAR_DISTANCE);
        double rearLidarDistance = locator.path("rearLidarDistance").getNode(root).asDouble(DEFAULT_REAR_LIDAR_DISTANCE);
        double cameraDistance = locator.path("cameraDistance").getNode(root).asDouble(DEFAULT_CAMERA_DISTANCE);
        return create(maxRadarDistance, lidarFOV, contactRadius, cameraFOV,
                headX, headY, frontLidarDistance, rearLidarDistance, cameraDistance,
                headFOV);
    }

    /**
     * Returns the distance the given pulses
     *
     * @param pulses the number of pulses
     */
    public static double pulse2Distance(double pulses) {
        return pulses * DISTANCE_PER_PULSE;
    }

    /**
     * Returns the location (m) for the given pulses
     *
     * @param xPulses the x pulse coordinate
     * @param yPulses the y pulse coordinate
     */
    public static Point2D pulses2Location(double xPulses, double yPulses) {
        return new Point2D.Double(pulse2Distance(xPulses), pulse2Distance(yPulses));
    }

    /**
     * Returns the location (m) for the given pulses
     *
     * @param x the abscissa (m)
     * @param y the ordinate (m)
     */
    public static Point2D location2Pulses(double x, double y) {
        return new Point2D.Double(distance2Pulse(x), distance2Pulse(y));
    }

    /**
     * Creates the robot specification
     *
     * @param maxRadarDistance   the maximum radar distance (m)
     * @param lidarFOV           the receptive angle
     * @param contactRadius      the contact radius (m)
     * @param cameraFOV          the camera view angle
     * @param headLocation       the relative sensor pivot location
     * @param frontLidarDistance the distance of front lidar from head (m)
     * @param rearLidarDistance  the distance of rear lidar from head (m)
     * @param cameraDistance     the distance of camera from head (m)
     * @param headFOV            the head field of view (the head rotation angle limit)
     */
    public RobotSpec(double maxRadarDistance, Complex lidarFOV, double contactRadius, Complex cameraFOV, Point2D headLocation, double frontLidarDistance, double rearLidarDistance, double cameraDistance, Complex headFOV) {
        this.maxRadarDistance = maxRadarDistance;
        this.lidarFOV = requireNonNull(lidarFOV);
        this.contactRadius = contactRadius;
        this.cameraFOV = requireNonNull(cameraFOV);
        this.headLocation = requireNonNull(headLocation);
        this.frontLidarDistance = frontLidarDistance;
        this.rearLidarDistance = rearLidarDistance;
        this.cameraDistance = cameraDistance;
        this.headFOV = requireNonNull(headFOV);
    }

    /**
     * Applies the transformation from camera view coordinates to absolute coordinates
     *
     * @param trans         the current transformation
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     * @param sensorDir     the relative sensor direction
     */
    public AffineTransform applyCameraView(AffineTransform trans, Point2D robotLocation, Complex robotDir, Complex sensorDir) {
        AffineTransform trans1 = applyRobotView(trans, robotLocation, robotDir);
        trans1.translate(headLocation.getX(), headLocation.getY());
        trans1.rotate(-sensorDir.toRad());
        trans1.translate(0, cameraDistance);
        return trans1;
    }

    /**
     * Applies the transformation from front lidar view coordinates to absolute coordinates
     *
     * @param trans         the current transformation
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     * @param sensorDir     the relative sensor direction
     */
    public AffineTransform applyFrontLidarView(AffineTransform trans, Point2D robotLocation, Complex robotDir, Complex sensorDir) {
        AffineTransform trans1 = applyRobotView(trans, robotLocation, robotDir);
        trans1.translate(headLocation.getX(), headLocation.getY());
        trans1.rotate(-sensorDir.toRad());
        trans1.translate(0, frontLidarDistance);
        return trans1;
    }

    /**
     * Applies the transformation from rear lidar view coordinates to absolute coordinates
     *
     * @param trans         the current transformation
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     * @param sensorDir     the relative sensor direction
     */
    public AffineTransform applyRearLidarView(AffineTransform trans, Point2D robotLocation, Complex robotDir, Complex sensorDir) {
        AffineTransform trans1 = applyRobotView(trans, robotLocation, robotDir);
        trans1.translate(headLocation.getX(), headLocation.getY());
        trans1.rotate(PI - sensorDir.toRad());
        trans1.translate(0, rearLidarDistance);
        return trans1;
    }

    /**
     * Returns the camera location
     *
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     * @param sensorDir     the relative sensor direction
     */
    public Point2D cameraLocation(Point2D robotLocation, Complex robotDir, Complex sensorDir) {
        return applyCameraView(new AffineTransform(), robotLocation, robotDir, sensorDir)
                .transform(new Point2D.Double(), null);
    }

    /**
     * Returns the front lidar absolute location
     *
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     * @param sensorDir     the relative sensor direction
     */
    public Point2D frontLidarLocation(Point2D robotLocation, Complex robotDir, Complex sensorDir) {
        return applyFrontLidarView(new AffineTransform(), robotLocation, robotDir, sensorDir).transform(new Point2D.Double(), null);
    }

    /**
     * Returns the proxy sensor area
     *
     * @param location  the proxy sensor location
     * @param direction the proxy sensor direction
     */
    public AreaExpression proxySensorArea(Point2D location, Complex direction) {
        return AreaExpression.radialSensorArea(
                location, direction, lidarFOV,
                RobotSpec.ROBOT_RADIUS, maxRadarDistance
        );
    }

    /**
     * Returns the front lidar absolute location
     *
     * @param robotLocation the robot location
     * @param robotDir      the robot direction
     * @param sensorDir     the relative sensor direction
     */
    public Point2D rearLidarLocation(Point2D robotLocation, Complex robotDir, Complex sensorDir) {
        return applyRearLidarView(new AffineTransform(), robotLocation, robotDir, sensorDir).transform(new Point2D.Double(), null);
    }
}
