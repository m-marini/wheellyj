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

import static java.lang.String.format;

/**
 * The camera event
 *
 * @param timestamp the timestamp of captured data
 * @param qrCode    the captured QRCode
 * @param points    the QRCode vertices
 */
public record CameraEvent(
        long timestamp,
        String qrCode,
        int width, int height,
        Point2D[] points
) {
    private static final int NUM_PARAMS = 13;
    public static final String UNKNOWN_QR_CODE = "?";

    /**
     * Returns the camera event from line
     *
     * @param line the data line
     */
    static CameraEvent create(String line) {
        String[] params = line.split(" ");
        if (params.length != NUM_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\" (#params=%d)", line, params.length));
        }
        long timestamp = Long.parseLong(params[1]);
        String qrcode = params[2];
        int width = Integer.parseInt(params[3]);
        int height = Integer.parseInt(params[4]);
        Point2D[] points = new Point2D[4];
        for (int i = 0; i < 4; i++) {
            double x = Double.parseDouble(params[i * 2 + 5]);
            double y = Double.parseDouble(params[i * 2 + 6]);
            points[i] = new Point2D.Double(x, y);
        }
        return new CameraEvent(timestamp, qrcode, width, height, points);
    }

    /**
     * Returns the unknown qrCode event
     */
    public static CameraEvent unknown(long timestamp) {
        return new CameraEvent(timestamp, UNKNOWN_QR_CODE, 0, 0, null);
    }
}
