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

/**
 * Contains the correlation between camera event and proxy message
 *
 * @param camerEvent the camera event
 * @param proxy      the proxy message
 */
public record CorrelatedCameraEvent(CameraEvent camerEvent, WheellyProxyMessage proxy) {
    /**
     * Creates the event
     *
     * @param camerEvent the camera event
     * @param proxy      the proxy message
     */
    public CorrelatedCameraEvent(CameraEvent camerEvent, WheellyProxyMessage proxy) {
        this.camerEvent = requireNonNull(camerEvent);
        this.proxy = requireNonNull(proxy);
    }

    /**
     * Returns the camera azimuth (direction relative to environment)
     */
    public Complex cameraAzimuth() {
        return proxy.robotYaw().add(proxy.sensorDirection());
    }

    /**
     * Returns the camera location
     */
    public Point2D cameraLocation() {
        return proxy.sensorLocation();
    }

    /**
     * Returns the marker azimuth (direction relative to environment)
     */
    public Complex markerAzimuth() {
        return cameraAzimuth().add(camerEvent().direction());
    }

    /**
     * Returns the marker distance or 0 if unable to locate the distance (m)
     */
    public double markerDistance() {
        return proxy.echoDistance();
    }

    /**
     * Returns the proxy simulation time (ms)
     */
    public long proxyTime() {
        return proxy.simulationTime();
    }

    /**
     * Returns the qr code recognized
     */
    public String qrCode() {
        return camerEvent.qrCode();
    }

    /**
     * Returns the camera simulation time (ms)
     */
    public long simulationTime() {
        return camerEvent.simulationTime();
    }
}
