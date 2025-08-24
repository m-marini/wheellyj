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

import io.reactivex.rxjava3.schedulers.Timed;

import java.awt.geom.Point2D;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * The camera event
 *
 * @param simulationTime the simulation time (ms)
 * @param qrCode         the captured QRCode
 * @param points         the QRCode vertices
 * @param direction      the direction of label
 */
public record CameraEvent(
        long simulationTime,
        String qrCode,
        int width, int height,
        Point2D[] points,
        Complex direction) {
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(\\S+),(\\d+),(\\d+),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*),(-?\\d+\\.?\\d*)$");
    private static final int NUM_PARAMS = 13;

    /**
     * Returns the camera event from line
     *
     * @param line  the data line
     * @param widthRatio the width ratio
     */
    public static CameraEvent create(Timed<String> line, double widthRatio, long timeOffset) {
        if (!line.value().startsWith("qr ")) {
            return null;
        }
        String[] params = line.value().split(" ");
        long time = line.time(TimeUnit.MILLISECONDS);
        if (params.length != NUM_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\" (#params=%d)", line, params.length));
        }
        String qrcode = params[2];
        int width = Integer.parseInt(params[3]);
        int height = Integer.parseInt(params[4]);
        Point2D[] points = new Point2D[4];
        double xTot = 0;
        for (int i = 0; i < 4; i++) {
            double x = Double.parseDouble(params[i * 2 + 5]);
            double y = Double.parseDouble(params[i * 2 + 6]);
            points[i] = new Point2D.Double(x, y);
            xTot += x;
        }
        Complex direction = Complex.fromPoint(((xTot - width * 2) / 4) * widthRatio, width);
        return new CameraEvent(time - timeOffset, qrcode, width, height, points, direction);
    }


    /**
     * Returns the camera event from line
     *
     * @param simTime    the simulation time
     * @param arg        the data line
     * @param widthRatio the width ratio
     */
    public static CameraEvent parse(long simTime, String arg, double widthRatio) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong camera message \"%s\"", arg));
        }
        String qrcode = m.group(1);
        int width = Integer.parseInt(m.group(2));
        int height = Integer.parseInt(m.group(3));
        Point2D[] points = new Point2D[4];
        double xTot = 0;
        for (int i = 0; i < 4; i++) {
            double x = Double.parseDouble(m.group(i * 2 + 4));
            double y = Double.parseDouble(m.group(i * 2 + 5));
            points[i] = new Point2D.Double(x, y);
            xTot += x;
        }
        Complex direction = Complex.fromPoint(((xTot - width * 2) / 4) * widthRatio, width);
        return new CameraEvent(simTime, qrcode, width, height, points, direction);
    }

    /**
     * Returns the unknown qrCode event
     */
    public static CameraEvent unknown(long timestamp) {
        return new CameraEvent(timestamp, RobotSpec.UNKNOWN_QR_CODE, 0, 0, null, Complex.DEG0);
    }
}
