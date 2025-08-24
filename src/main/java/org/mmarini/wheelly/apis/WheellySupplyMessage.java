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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.String.format;

/**
 * Contains the supply voltage value
 *
 * @param simulationTime the simulation markerTime (ms)
 * @param supplySensor   the supply voltage (U)
 */
public record WheellySupplyMessage(long simulationTime, int supplySensor) implements WheellyMessage {
    public static final int NO_PARAMS = 3;
    // [sampleTime] [voltage (U)]
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,(\\d+)$");

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
        String[] params = line.value().split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException(format("Wrong supply message \"%s\" (#params=%d)", line.value(), params.length));
        }
        long remoteTime = parseLong(params[1]);
        int supplySensor = parseInt(params[2]);
        long simTime = clockConverter.fromRemote(remoteTime);
        return new WheellySupplyMessage(simTime, supplySensor);
    }

    public static WheellySupplyMessage create(Timed<String> line, long timeOffset) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException(format("Wrong supply message \"%s\" (#params=%d)", line.value(), params.length));
        }
        int supplySensor = parseInt(params[2]);
        long simTime = time - timeOffset;
        return new WheellySupplyMessage(simTime, supplySensor);
    }

    /**
     * Returns the supply message from argument string
     * The string status is formatted as:
     * <pre>
     *     [sampleTime]
     *     [voltage (U)]
     * </pre>
     *
     * @param simTime the simulation time (ms)
     * @param arg     the argument string
     */
    public static WheellySupplyMessage parse(long simTime, String arg) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong supply message \"%s\"", arg));
        }
        int supplySensor = parseInt(m.group(1));
        return new WheellySupplyMessage(simTime, supplySensor);
    }
}
