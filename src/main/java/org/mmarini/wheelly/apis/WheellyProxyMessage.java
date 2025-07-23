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

import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.Math.round;
import static java.lang.String.format;

/**
 * The Wheelly status contain the sensor value of Wheelly
 *
 * @param localTime          the local message localTime (ms)
 * @param simulationTime     the simulation markerTime (ms)
 * @param remoteTime         the remote ping localTime (ms)
 * @param sensorDirectionDeg the sensor direction at ping (DEG)
 * @param sensorDirection    the sensor direction at ping
 * @param echoDelay          the echo delay (um)
 * @param xPulses            the x robot location pulses at echo ping
 * @param yPulses            the y robot location pulses at echo ping
 * @param echoYawDeg         the robot direction at ping (DEG)
 * @param echoYaw            the robot direction at ping
 */
public record WheellyProxyMessage(long localTime, long simulationTime, long remoteTime,
                                  int sensorDirectionDeg, Complex sensorDirection, long echoDelay,
                                  double xPulses, double yPulses, int echoYawDeg,
                                  Complex echoYaw) implements WheellyMessage {
    public static final int NUM_PARAMS = 7;
    public static final float DISTANCE_SCALE = 1F / 5882;

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     px
     *     [sampleTime]
     *     [sensorDirectionDeg]
     *     [distanceTime (us)]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     * </pre>
     *
     * @param line           the status string
     * @param clockConverter the clock converter
     */

    public static WheellyProxyMessage create(Timed<String> line, ClockConverter clockConverter) {
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

        long simTime = clockConverter.fromRemote(remoteTime);
        return new WheellyProxyMessage(time, simTime, remoteTime, echoDirection, echoDelay, x,
                y, echoYaw);
    }

    /**
     * Creates the message
     *
     * @param localTime          the local message localTime (ms)
     * @param simulationTime     the simulation markerTime (ms)
     * @param remoteTime         the remote ping localTime (ms)
     * @param sensorDirectionDeg the sensor direction at ping (DEG)
     * @param echoDelay          the echo delay (um)
     * @param xPulses            the x robot location pulses at echo ping
     * @param yPulses            the y robot location pulses at echo ping
     * @param echoYawDeg         the robot direction at ping (DEG)
     */
    public WheellyProxyMessage(long localTime, long simulationTime, long remoteTime, int sensorDirectionDeg, long echoDelay, double xPulses, double yPulses, int echoYawDeg) {
        this(localTime, simulationTime, remoteTime, sensorDirectionDeg, Complex.fromDeg(sensorDirectionDeg), echoDelay, xPulses, yPulses, echoYawDeg, Complex.fromDeg(echoYawDeg));
    }

    /**
     * Returns the absolute echo direction (DEG)
     */
    public Complex echoDirection() {
        return sensorDirection.add(echoYaw);
    }

    /**
     * Returns the echo distance (m)
     */
    public double echoDistance() {
        return echoDelay * DISTANCE_SCALE;
    }

    /**
     * Returns the proxy message with echo delay set
     *
     * @param echoDelay echo delay (us)
     */
    public WheellyProxyMessage setEchoDelay(long echoDelay) {
        return echoDelay != this.echoDelay
                ? new WheellyProxyMessage(localTime, simulationTime, remoteTime, sensorDirectionDeg, sensorDirection, echoDelay, xPulses, yPulses, echoYawDeg, echoYaw)
                : this;
    }

    /**
     * Returns the proxy message with echo delay set to echo distance
     *
     * @param echoDistance the distance (m)
     */
    public WheellyProxyMessage setEchoDistance(double echoDistance) {
        return setEchoDelay(round(echoDistance / DISTANCE_SCALE));
    }

    /**
     * Returns the proxy message with sensor direction set
     *
     * @param sensorDirection the sensor direction (DEG)
     */
    public WheellyProxyMessage setSensorDirection(int sensorDirection) {
        return sensorDirection != this.sensorDirectionDeg
                ? new WheellyProxyMessage(localTime, simulationTime, remoteTime, sensorDirection, echoDelay, xPulses, yPulses, echoYawDeg)
                : this;
    }

    /**
     * Returns the proxy message with simulation markerTime
     *
     * @param simulationTime the simulation markerTime
     */
    public WheellyProxyMessage setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime
                ? new WheellyProxyMessage(localTime, simulationTime, remoteTime, sensorDirectionDeg, sensorDirection, echoDelay, xPulses, yPulses, echoYawDeg, echoYaw)
                : this;
    }
}