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

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ProxySample {
    private static final int NO_PARAMS = 7;

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
        int relDirection = Integer.parseInt(params[2]);
        float distance = Float.parseFloat(params[3]);
        float x = Float.parseFloat(params[4]);
        float y = Float.parseFloat(params[5]);
        int direction = Integer.parseInt(params[6]);
        return new Timed<>(new ProxySample(relDirection, distance, RobotAsset.create(x, y, direction)),
                instant, TimeUnit.MILLISECONDS);
    }

    public final RobotAsset asset;
    public final float distance;
    public final int relativeDirection;

    /**
     * @param relativeDirection relative direction DEG
     * @param distance          distance (m)
     * @param asset             asset
     */
    protected ProxySample(int relativeDirection, float distance, RobotAsset asset) {
        this.relativeDirection = relativeDirection;
        this.distance = distance;
        this.asset = asset;
    }
}
