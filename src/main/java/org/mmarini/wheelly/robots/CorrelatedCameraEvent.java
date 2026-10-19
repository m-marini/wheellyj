/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.robots;

import org.mmarini.wheelly.utils.Angle;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.utils.Utils.cm2m;

/**
 * Contains the correlation between camera event and lidar message
 *
 * @param cameraEvent the camera event
 * @param lidar      the lidar message
 */
public record CorrelatedCameraEvent(CameraEvent cameraEvent, WheellyLidarMessage lidar) {
    public static final CorrelatedCameraEvent DEFAULT_MESSAGE = new CorrelatedCameraEvent(CameraEvent.DEFAULT_EVENT, WheellyLidarMessage.DEFAULT_MESSAGE);

    /**
     * Creates the event
     *
     * @param cameraEvent the camera event
     * @param lidar      the proxy message
     */
    public CorrelatedCameraEvent(CameraEvent cameraEvent, WheellyLidarMessage lidar) {
        this.cameraEvent = requireNonNull(cameraEvent);
        this.lidar = requireNonNull(lidar);
    }

    /**
     * Returns the camera simulation time (ms)
     */
    public long cameraTime() {
        return cameraEvent.time();
    }

    /**
     * Returns the head direction relative the robot at the lidar message
     */
    public Angle headDirection() {
        return lidar.headDirection();
    }

    /**
     * Returns the lidar message simulation time (ms)
     */
    public long lidarTime() {
        return lidar.time();
    }

    /**
     * Returns the lidar azimuth (direction relative to environment)
     */
    public Angle lidarYaw() {
        return lidar.robotYaw().add(lidar.headDirection());
    }

    /**
     * Returns the marker distance or 0 if unable to locate the distance (m)
     */
    public double markerDistance() {
        return cm2m(lidar.frontDistance());
    }

    /**
     * Returns the qr code recognised
     */
    public int markerId() {
        return cameraEvent.markerId();
    }

    /**
     * Returns the marker yaw (direction relative to environment)
     */
    public Angle markerYaw() {
        return lidarYaw().add(cameraEvent().direction());
    }

    /**
     * Returns the robot direction relative the environment
     */
    public Angle robotDirection() {
        return lidar.robotYaw();
    }

    /**
     * Returns the robot location at the lidar message
     */
    public Point2D robotLocation() {
        return lidar.robotLocation();
    }
}
