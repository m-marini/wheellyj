/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.mm2m;

/**
 * Contains the correlation between camera event and lidar message
 *
 * @param camerEvent the camera event
 * @param lidar      the lidar message
 */
public record CorrelatedCameraEvent(CameraEvent camerEvent, WheellyLidarMessage lidar) {
    public static final CorrelatedCameraEvent DEFAULT_MESSAGE = new CorrelatedCameraEvent(CameraEvent.DEFAULT_EVENT, WheellyLidarMessage.DEFAULT_MESSAGE);

    /**
     * Creates the event
     *
     * @param camerEvent the camera event
     * @param lidar      the proxy message
     */
    public CorrelatedCameraEvent(CameraEvent camerEvent, WheellyLidarMessage lidar) {
        this.camerEvent = requireNonNull(camerEvent);
        this.lidar = requireNonNull(lidar);
    }

    /**
     * Returns the camera simulation time (ms)
     */
    public long cameraTime() {
        return camerEvent.simulationTime();
    }

    /**
     * Returns the head direction relative the robot at the lidar message
     */
    public Complex headDirection() {
        return lidar.headDirection();
    }

    /**
     * Returns the lidar message simulation time (ms)
     */
    public long lidarTime() {
        return lidar.simulationTime();
    }

    /**
     * Returns the lidar azimuth (direction relative to environment)
     */
    public Complex lidarYaw() {
        return lidar.robotYaw().add(lidar.headDirection());
    }

    /**
     * Returns the marker distance or 0 if unable to locate the distance (m)
     */
    public double markerDistance() {
        return mm2m(lidar.frontDistance());
    }

    /**
     * Returns the marker yaw (direction relative to environment)
     */
    public Complex markerYaw() {
        return lidarYaw().add(camerEvent().direction());
    }

    /**
     * Returns the qr code recognised
     */
    public String qrCode() {
        return camerEvent.qrCode();
    }

    /**
     * Returns the robot direction relative the environment
     */
    public Complex robotDirection() {
        return lidar.robotYaw();
    }

    /**
     * Returns the robot location at the lidar message
     */
    public Point2D robotLocation() {
        return lidar.robotLocation();
    }
}
