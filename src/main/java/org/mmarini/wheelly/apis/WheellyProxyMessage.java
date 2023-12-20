/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.schedulers.Timed;

import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyProxyMessage extends WheellyMessage {
    public static final int NUM_PARAMS = 7;

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     px
     *     [sampleTime]
     *     [sensorDirection]
     *     [distanceTime (us)]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     * </pre>
     *
     * @param line the status string
     */

    public static WheellyProxyMessage create(Timed<String> line) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NUM_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\" (#params=%d)", line.value(), params.length));
        }
        long remoteTime = parseLong(params[1]);
        int echoDirection = parseInt(params[2]);
        int echoDelay = parseInt(params[3]);
        double x = parseDouble(params[4]);
        double y = parseDouble(params[5]);
        int echoYaw = parseInt(params[6]);

        return new WheellyProxyMessage(time, remoteTime, echoDirection, echoDelay, x,
                y, echoYaw);
    }

    private final int sensorDirection;
    private final long echoDelay;
    private final double xPulses;
    private final double yPulses;
    private final int echoYaw;

    /**
     * Creates wheelly status
     *
     * @param time            the local message time
     * @param remoteTime      the remote ping time
     * @param sensorDirection the sensor direction at ping (DEG)
     * @param echoDelay       the echo delay (um)
     * @param xPulses         the x robot location pulses at echo ping
     * @param yPulses         the y robot location pulses at echo ping
     * @param echoYaw         the robot direction at ping (DEG)
     */
    public WheellyProxyMessage(long time, long remoteTime, int sensorDirection, long echoDelay, double xPulses, double yPulses,
                               int echoYaw) {
        super(time, remoteTime);
        this.xPulses = xPulses;
        this.yPulses = yPulses;
        this.sensorDirection = sensorDirection;
        this.echoDelay = echoDelay;
        this.echoYaw = echoYaw;
    }

    /**
     * Returns the echo delay (us)
     */
    public long getEchoDelay() {
        return echoDelay;
    }

    /**
     * Returns the proxy message with echo delay set
     *
     * @param echoDelay echo delay (us)
     */
    public WheellyProxyMessage setEchoDelay(long echoDelay) {
        return echoDelay != this.echoDelay
                ? new WheellyProxyMessage(time, remoteTime, sensorDirection, echoDelay, xPulses, yPulses, echoYaw)
                : this;
    }

    /**
     * Returns the absolute echo direction (DEG)
     */
    public int getEchoDirection() {
        return normalizeDegAngle(sensorDirection + echoYaw);
    }

    /**
     * Returns the robot direction at ping (DEG)
     */
    public int getEchoYaw() {
        return echoYaw;
    }

    /**
     * Returns the sensor direction at ping (DEG)
     */
    public int getSensorDirection() {
        return sensorDirection;
    }

    /**
     * Returns the proxy message with sensor direction set
     *
     * @param sensorDirection the sensor direction (DEG)
     */
    public WheellyProxyMessage setSensorDirection(int sensorDirection) {
        return sensorDirection != this.sensorDirection
                ? new WheellyProxyMessage(time, remoteTime, sensorDirection, echoDelay, xPulses, yPulses, echoYaw)
                : this;
    }

    /**
     * Returns the robot location abscissa at ping
     */
    public double getXPulses() {
        return xPulses;
    }

    /**
     * Returns the robot location ordinate at ping
     */
    public double getYPulses() {
        return yPulses;
    }

    /**
     * Returns the status with remote time instant set
     *
     * @param remoteTime the remote ping instant
     */
    public WheellyProxyMessage setRemoteTime(long remoteTime) {
        return remoteTime != this.remoteTime
                ? new WheellyProxyMessage(time, remoteTime, sensorDirection, echoDelay, xPulses, yPulses, echoYaw)
                : this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyProxyMessage.class.getSimpleName() + "[", "]")
                .add("time=" + time)
                .add("remoteTime=" + remoteTime)
                .add("echoDirection=" + sensorDirection)
                .add("echoDelay=" + echoDelay)
                .add("xPulses=" + xPulses)
                .add("yPulses=" + yPulses)
                .add("echoYaw=" + echoYaw)
                .toString();
    }

}
