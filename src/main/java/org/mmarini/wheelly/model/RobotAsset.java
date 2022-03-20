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
    private static final int NO_PARAMS = 17;

    /**
     * @param status   the imu status
     * @param failure  the imue failure
     * @param yaw      the yaw
     * @param pitch    the pitch
     * @param roll     the roll
     * @param acc      the acceleration
     * @param linAcc   the linear acceleration
     * @param worldAcc the world acceleration
     * @param xSpeed   the x speed
     */
    public static RobotAsset create(int status, int failure, float yaw, float pitch, float roll, float[] acc, float[] linAcc, float[] worldAcc, float xSpeed) {
        return new RobotAsset(status, failure, yaw, pitch, roll, acc, linAcc, worldAcc, xSpeed);
    }

    /**
     * The string status is formatted as:
     * <pre>
     *     as [status] [failure] [assetTime] [accX] [accY] [accZ] [linAccX] [linAccY] [linAccZ] [worldAccX] [worldAccY] [worldAccZ] [yaw] [pitch] [roll]
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
        int status = Integer.parseInt(params[1]);
        int failure = Integer.parseInt(params[2]);
        long instant = clock.fromRemote(Long.parseLong(params[3]));
        float[] acc = new float[3];
        acc[0] = Float.parseFloat(params[4]);
        acc[1] = Float.parseFloat(params[5]);
        acc[2] = Float.parseFloat(params[6]);
        float[] linAcc = new float[3];
        linAcc[0] = Float.parseFloat(params[7]);
        linAcc[1] = Float.parseFloat(params[8]);
        linAcc[2] = Float.parseFloat(params[9]);
        float[] worldAcc = new float[3];
        worldAcc[0] = Float.parseFloat(params[10]);
        worldAcc[1] = Float.parseFloat(params[11]);
        worldAcc[2] = Float.parseFloat(params[12]);
        float yaw = Float.parseFloat(params[13]);
        float pitch = Float.parseFloat(params[14]);
        float roll = Float.parseFloat(params[15]);
        float xSpeed = Float.parseFloat(params[16]);
        return new Timed<>(new RobotAsset(status, failure, yaw, pitch, roll, acc, linAcc, worldAcc, xSpeed), instant, TimeUnit.MILLISECONDS);
    }
    public final float[] acc;
    public final int failure;
    public final float[] linAcc;
    public final float pitch;
    public final float roll;
    public final int status;
    public final float[] worldAcc;
    public final float xSpeed;
    public final float yaw;

    /**
     * @param status   the imu status
     * @param failure  the imue failure
     * @param yaw      the yaw
     * @param pitch    the pitch
     * @param roll     the roll
     * @param acc      the acceleration
     * @param linAcc   the linear acceleration
     * @param worldAcc the world acceleration
     * @param xSpeed   the x speed
     */
    protected RobotAsset(int status, int failure, float yaw, float pitch, float roll, float[] acc, float[] linAcc, float[] worldAcc, float xSpeed) {
        this.status = status;
        this.failure = failure;
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
