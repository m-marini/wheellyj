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

import static java.lang.Long.parseLong;
import static java.lang.String.format;

/**
 * Contains the contact message
 *
 * @param simulationTime  the simulation markerTime (ms)
 * @param frontSensors    the front sensors signals
 * @param rearSensors     the rear sensors signals
 * @param canMoveForward  true if it can move forward
 * @param canMoveBackward true if it can move backward
 */
public record WheellyContactsMessage(long simulationTime,
                                     boolean frontSensors, boolean rearSensors,
                                     boolean canMoveForward, boolean canMoveBackward)
        implements WheellyMessage {
    public static final int NO_PARAMS = 6;
    // [sampleTime] [frontSignals] [rearSignals] [canMoveForward] [canMoveBackward]
    public static final Pattern ARG_PATTERN = Pattern.compile("^\\d+,([01]),([01]),([01]),([01])$");

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     ct
     *     [sampleTime]
     *     [frontSignals]
     *     [rearSignals]
     *     [canMoveForward]
     *     [canMoveBackward]
     * </pre>
     *
     * @param line           the status string
     * @param clockConverter the clock converter
     */
    public static WheellyContactsMessage create(Timed<String> line, ClockConverter clockConverter) {
        String[] params = line.value().split(" ");
        if (params.length != NO_PARAMS - 1) {
            throw new IllegalArgumentException(format("Wrong contacts message \"%s\" (#params=%d)", line.value(), params.length));
        }
        long remoteTime = parseLong(params[0]);
        boolean frontSensors = Integer.parseInt(params[1]) != 0;
        boolean rearSensors = Integer.parseInt(params[2]) != 0;

        boolean canMoveForward = Integer.parseInt(params[3]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[4]) != 0;
        long simTime = clockConverter.fromRemote(remoteTime);
        return new WheellyContactsMessage(
                simTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward
        );
    }

    public static WheellyContactsMessage create(Timed<String> line, long timeOffset) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException(format("Wrong contacts message \"%s\" (#params=%d)", line.value(), params.length));
        }
        boolean frontSensors = Integer.parseInt(params[2]) != 0;
        boolean rearSensors = Integer.parseInt(params[3]) != 0;

        boolean canMoveForward = Integer.parseInt(params[4]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[5]) != 0;
        long simTime = time - timeOffset;
        return new WheellyContactsMessage(
                simTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward
        );
    }

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     [sampleTime]
     *     [frontSignals]
     *     [rearSignals]
     *     [canMoveForward]
     *     [canMoveBackward]
     * </pre>
     *
     * @param simTime the simulation time (ms)
     * @param arg     the arguments string
     */
    public static WheellyContactsMessage parse(long simTime, String arg) {
        Matcher m = ARG_PATTERN.matcher(arg);
        if (!m.matches()) {
            throw new IllegalArgumentException(format("Wrong contacts message \"%s\"", arg));
        }
        boolean frontSensors = Integer.parseInt(m.group(1)) != 0;
        boolean rearSensors = Integer.parseInt(m.group(2)) != 0;

        boolean canMoveForward = Integer.parseInt(m.group(3)) != 0;
        boolean canMoveBackward = Integer.parseInt(m.group(4)) != 0;
        return new WheellyContactsMessage(
                simTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward
        );
    }

    /**
     * Returns the message with can move the backward set
     *
     * @param canMoveBackward true if the robot can move backward
     */
    public WheellyContactsMessage setCanMoveBackward(boolean canMoveBackward) {
        return canMoveBackward != this.canMoveBackward
                ? new WheellyContactsMessage(simulationTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward)
                : this;
    }

    /**
     * Returns the message with the set can-move forward flag
     *
     * @param canMoveForward true if robot can move forward
     */
    public WheellyContactsMessage setCanMoveForward(boolean canMoveForward) {
        return canMoveForward != this.canMoveForward
                ? new WheellyContactsMessage(simulationTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward)
                : this;
    }

    /**
     * Returns the contact message with simulation markerTime
     *
     * @param simulationTime the simulation markerTime (ms)
     */
    public WheellyContactsMessage setSimulationTime(long simulationTime) {
        return simulationTime != this.simulationTime
                ? new WheellyContactsMessage(simulationTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward)
                : this;
    }
}
