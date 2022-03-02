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

import static java.util.Objects.requireNonNull;

/**
 *
 */
public class RobotAsset {
    private static final int NO_PARAMS = 8;

    /**
     * @param yaw
     * @param pitch
     * @param roll
     * @param acc
     */
    public static RobotAsset create(float yaw, float pitch, float roll, float[] acc) {
        return new RobotAsset(yaw, pitch, roll, acc);
    }

    /**
     * @param msg
     * @param clock
     */
    public static Timed<RobotAsset> from(String msg, RemoteClock clock) {
        String[] params = msg.split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException("Missing status parameters");
        }
        long instant = clock.fromRemote(Long.parseLong(params[1]));
        float yaw = Float.parseFloat(params[2]);
        float pitch = Float.parseFloat(params[3]);
        float roll = Float.parseFloat(params[4]);
        float[] acc = new float[3];
        acc[0] = Float.parseFloat(params[5]);
        acc[1] = Float.parseFloat(params[6]);
        acc[2] = Float.parseFloat(params[7]);
        return new Timed<>(new RobotAsset(yaw, pitch, roll, acc), instant, TimeUnit.MILLISECONDS);
    }

    public final float[] acc;
    public final float pitch;
    public final float roll;
    public final float yaw;

    /**
     * @param yaw
     * @param pitch
     * @param roll
     * @param acc
     */
    protected RobotAsset(float yaw, float pitch, float roll, float[] acc) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.acc = requireNonNull(acc);
    }

    /**
     *
     */
    public float[] getAcc() {
        return acc;
    }

    /**
     *
     */
    public float getPitch() {
        return pitch;
    }

    /**
     *
     */
    public float getRoll() {
        return roll;
    }

    /**
     *
     */
    public float getYaw() {
        return yaw;
    }
}
