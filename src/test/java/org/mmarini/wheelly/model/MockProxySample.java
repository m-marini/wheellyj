/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

public class MockProxySample implements ProxySample {
    private final Point2D robotLocation;
    private final int robotDeg;
    private final int sensorRelativeDeg;
    private final double sampleDistance;

    public MockProxySample(Point2D robotLocation, int robotDeg, int sensorRelativeDeg, double sampleDistance) {
        this.robotLocation = robotLocation;
        this.robotDeg = robotDeg;
        this.sensorRelativeDeg = sensorRelativeDeg;
        this.sampleDistance = sampleDistance;
    }

    @Override
    public int getRobotDeg() {
        return robotDeg;
    }

    @Override
    public Point2D getRobotLocation() {
        return robotLocation;
    }

    @Override
    public double getSampleDistance() {
        return sampleDistance;
    }

    @Override
    public int getSensorRelativeDeg() {
        return sensorRelativeDeg;
    }
}
