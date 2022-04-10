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

import io.reactivex.rxjava3.schedulers.Timed;

import java.awt.geom.Point2D;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Math.*;
import static org.mmarini.wheelly.model.ScannerMap.normalizeAngle;

/**
 *
 */
public class ProxySample {
    private static final int NO_PARAMS = 7;

    /**
     * @param relativeDirection relative direction DEG
     * @param distance          distance (m)
     * @param asset             asset
     */
    public static ProxySample create(int relativeDirection, double distance, RobotAsset asset) {
        return new ProxySample(relativeDirection, distance, asset);
    }

    /**
     * The string status is formatted as:
     * <pre>
     *     sa [sampleTime] [direction] [distance] [x location] [y location] [angle]
     * </pre>
     *
     * @param msg   the asset message
     * @param clock the remote clock
     */
    public static Timed<ProxySample> from(String msg, RemoteClock clock) {
        String[] params = msg.split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException("Missing status parameters");
        }
        long instant = clock.fromRemote(Long.parseLong(params[1]));
        int relDirection = parseInt(params[2]);
        double distance = parseDouble(params[3]);
        double x = parseDouble(params[4]);
        double y = parseDouble(params[5]);
        int direction = parseInt(params[6]);
        return new Timed<>(new ProxySample(relDirection, distance, RobotAsset.create(x, y, direction)),
                instant, TimeUnit.MILLISECONDS);
    }

    public final RobotAsset asset;
    public final double distance;
    public final int relativeDirection;

    /**
     * @param relativeDirection relative direction DEG
     * @param distance          distance (m)
     * @param asset             asset
     */
    protected ProxySample(int relativeDirection, double distance, RobotAsset asset) {
        this.relativeDirection = relativeDirection;
        this.distance = distance;
        this.asset = asset;
    }

    /**
     * Returns the absolute location of the reflector if any
     */
    public Optional<Point2D> getLocation() {
        if (distance > 0) {
            Point2D location = asset.getLocation();
            double angle = asset.getRadDirection() + toRadians(relativeDirection);
            double x = location.getX() + distance * cos(angle);
            double y = location.getY() + distance * sin(angle);
            return Optional.of(new Point2D.Double(x, y));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the sample direction in RAD
     */
    public double getSampleDirection() {
        return normalizeAngle(asset.getRadDirection() + toRadians(relativeDirection));
    }
}
