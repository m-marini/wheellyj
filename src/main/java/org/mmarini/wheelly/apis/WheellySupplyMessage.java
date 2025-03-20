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

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;

/**
 * The Wheelly status contain the sensor value of Wheelly
 *
 * @param localTime      the status localTime (ms)
 * @param simulationTime the simulation markerTime (ms)
 * @param remoteTime     the remote localTime (ms)
 * @param supplySensor   the supply voltage (U)
 */
public record WheellySupplyMessage(long localTime, long simulationTime, long remoteTime, int supplySensor
) implements WheellyMessage {
    public static final int NO_PARAMS = 3;

    /**
     * Returns the Wheelly supply event from string
     * The string status is formatted as:
     * <pre>
     *     sv
     *     [sampleTime]
     *     [voltage (U)]
     * </pre>
     *
     * @param line           the status string
     * @param clockConverter the clock converter
     */
    public static WheellySupplyMessage create(Timed<String> line, ClockConverter clockConverter) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException(format("Wrong supply message \"%s\" (#params=%d)", line.value(), params.length));
        }
        long remoteTime = parseLong(params[1]);
        int supplySensor = parseInt(params[2]);
        long simTime = clockConverter.fromRemote(remoteTime);
        return new WheellySupplyMessage(time, simTime, remoteTime, supplySensor);
    }

    /**
     * Returns the status with remote localTime instant set
     *
     * @param remoteTime the remote instant
     */
    public WheellySupplyMessage setRemoteTime(long remoteTime) {
        return remoteTime != this.remoteTime
                ? new WheellySupplyMessage(localTime, simulationTime, remoteTime, supplySensor)
                : this;
    }
}
