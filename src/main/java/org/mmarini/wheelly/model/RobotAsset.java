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
    private static final int NO_PARAMS = 15;

    /**
     * @param yaw
     * @param pitch
     * @param roll
     * @param acc
     * @param linAcc
     * @param worldAcc
     * @param xSpeed
     */
    public static RobotAsset create(float yaw, float pitch, float roll, float[] acc, float[] linAcc, float[] worldAcc, float xSpeed) {
        return new RobotAsset(yaw, pitch, roll, acc, linAcc, worldAcc, xSpeed);
    }

    /**
     * The string status is formatted as:
     * <pre>
     *     as [assetTime] [accX] [accY] [accZ] [linAccX] [linAccY] [linAccZ] [worldAccX] [worldAccY] [worldAccZ] [yaw] [pitch] [roll]
     * </pre>
     *
     * @param msg   the asset message
     * @param clock the remote clock
     */
    public static Timed<RobotAsset> from(String msg, RemoteClock clock) {
        String[] params = msg.split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException("Missing status parameters");
        }
        long instant = clock.fromRemote(Long.parseLong(params[1]));
        float[] acc = new float[3];
        acc[0] = Float.parseFloat(params[2]);
        acc[1] = Float.parseFloat(params[3]);
        acc[2] = Float.parseFloat(params[4]);
        float[] linAcc = new float[3];
        linAcc[0] = Float.parseFloat(params[5]);
        linAcc[1] = Float.parseFloat(params[6]);
        linAcc[2] = Float.parseFloat(params[7]);
        float[] worldAcc = new float[3];
        worldAcc[0] = Float.parseFloat(params[8]);
        worldAcc[1] = Float.parseFloat(params[9]);
        worldAcc[2] = Float.parseFloat(params[10]);
        float yaw = Float.parseFloat(params[11]);
        float pitch = Float.parseFloat(params[12]);
        float roll = Float.parseFloat(params[13]);
        float xSpeed = Float.parseFloat(params[14]);
        return new Timed<>(new RobotAsset(yaw, pitch, roll, acc, linAcc, worldAcc, xSpeed), instant, TimeUnit.MILLISECONDS);
    }

    public final float[] acc;
    public final float[] linAcc;
    public final float pitch;
    public final float roll;
    public final float[] worldAcc;
    public final float xSpeed;
    public final float yaw;

    /**
     * @param yaw
     * @param pitch
     * @param roll
     * @param acc
     * @param linAcc
     * @param worldAcc
     * @param xSpeed
     */
    protected RobotAsset(float yaw, float pitch, float roll, float[] acc, float[] linAcc, float[] worldAcc, float xSpeed) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
        this.xSpeed = xSpeed;
        this.acc = requireNonNull(acc);
        this.linAcc = requireNonNull(linAcc);
        this.worldAcc = requireNonNull(worldAcc);
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
    public float[] getLinAcc() {
        return linAcc;
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
    public float[] getWorldAcc() {
        return worldAcc;
    }

    /**
     *
     */
    public float getYaw() {
        return yaw;
    }

    /**
     *
     */
    public float getxSpeed() {
        return xSpeed;
    }
}
