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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.robots.RobotSpec.UNKNOWN_MARKER;

/**
 * The camera event
 *
 * @param time      the message time (ms)
 * @param markerId  the captured QRCode
 * @param points    the QRCode vertices
 * @param direction the direction of label
 */
public record CameraEvent(
        long time,
        int markerId,
        int width, int height,
        Point2D[] points,
        Angle direction) {
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(\\d+),(\\d+),(\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)$");
    public static final CameraEvent DEFAULT_EVENT = unknown(0);

    public static CameraEvent create(long time, int markerId, int width, int height, double widthRatio, double... coords) {
        requireNonNull(coords);
        if (!(coords.length == 0 || coords.length == 8)) {
            throw new IllegalArgumentException("Require 0 or 8 coordinates: " + coords.length);
        }
        Point2D[] points = null;
        Angle direction = Angle.DEG0;
        if (coords.length == 8) {
            points = new Point2D[4];
            double xTot = 0;
            for (int i = 0; i < 4; i++) {
                double x = coords[i * 2];
                double y = coords[i * 2 + 1];
                points[i] = new Point2D.Double(x, y);
                xTot += x;
            }
            direction = Angle.fromPoint(((xTot - width * 2) / 4) * widthRatio, width);
        }
        return new CameraEvent(time, markerId, width, height, points, direction);
    }

    /**
     * Returns the camera event from line
     *
     * @param time       the message time (ms)
     * @param arg        the data line
     * @param widthRatio the width ratio
     */
    public static CameraEvent parse(long time, String arg, double widthRatio) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong camera message \"%s\"", arg));
        }
        int markerId = Integer.parseInt(m.group(1));
        int width = Integer.parseInt(m.group(2));
        int height = Integer.parseInt(m.group(3));

        double[] points = IntStream.range(0, 8)
                .mapToDouble(i -> Double.parseDouble(m.group(i + 4)))
                .toArray();

        return create(time, markerId, width, height, widthRatio, points);
    }

    /**
     * Returns the unknown qrCode event
     */
    public static CameraEvent unknown(long timestamp) {
        return new CameraEvent(timestamp, UNKNOWN_MARKER, 0, 0, null, Angle.DEG0);
    }
}
