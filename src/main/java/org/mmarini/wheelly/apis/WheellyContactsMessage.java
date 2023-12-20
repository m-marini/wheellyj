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

import static java.lang.Long.parseLong;
import static java.lang.String.format;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyContactsMessage extends WheellyMessage {
    public static final int NO_PARAMS = 6;

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
     * @param line the status string
     */

    public static WheellyContactsMessage create(Timed<String> line) {
        long time = line.time(TimeUnit.MILLISECONDS);
        String[] params = line.value().split(" ");
        if (params.length != NO_PARAMS) {
            throw new IllegalArgumentException(format("Wrong contacts message \"%s\" (#params=%d)", line.value(), params.length));
        }
        long remoteTime = parseLong(params[1]);
        boolean frontSensors = Integer.parseInt(params[2]) != 0;
        boolean rearSensors = Integer.parseInt(params[3]) != 0;

        boolean canMoveForward = Integer.parseInt(params[4]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[5]) != 0;

        return new WheellyContactsMessage(time,
                remoteTime, frontSensors, rearSensors,
                canMoveForward, canMoveBackward
        );
    }

    private final boolean frontSensors;
    private final boolean rearSensors;
    private final boolean canMoveBackward;
    private final boolean canMoveForward;

    /**
     * Creates wheelly status
     *
     * @param time            the status time
     * @param remoteTime      the remote contacts time
     * @param frontSensors    the front sensors signals
     * @param rearSensors     the rear sensors signals
     * @param canMoveForward  true if it can move forward
     * @param canMoveBackward true if it can move backward
     */
    public WheellyContactsMessage(long time,
                                  long remoteTime, boolean frontSensors, boolean rearSensors,
                                  boolean canMoveForward, boolean canMoveBackward) {
        super(time, remoteTime);
        this.frontSensors = frontSensors;
        this.rearSensors = rearSensors;
        this.canMoveBackward = canMoveBackward;
        this.canMoveForward = canMoveForward;
    }

    /**
     * Return true if robot can move backward
     */
    public boolean canMoveBackward() {
        return canMoveBackward;
    }

    /**
     * Return true if robot can move forward
     */
    public boolean canMoveForward() {
        return canMoveForward;
    }

    /**
     * Returns true if front sensor is clear
     */
    public boolean isFrontSensors() {
        return frontSensors;
    }

    /**
     * Returns true if rear sensor is clear
     */
    public boolean isRearSensors() {
        return rearSensors;
    }

    /**
     * Returns the message with can move backward set
     *
     * @param canMoveBackward true if robot can move backward
     */
    public WheellyContactsMessage setCanMoveBackward(boolean canMoveBackward) {
        return canMoveBackward != this.canMoveBackward
                ? new WheellyContactsMessage(time, remoteTime, frontSensors, rearSensors, canMoveForward, canMoveBackward)
                : this;
    }

    /**
     * Returns the message with can move forward set
     *
     * @param canMoveForward true if robot can move forward
     */
    public WheellyContactsMessage setCanMoveForward(boolean canMoveForward) {
        return canMoveForward != this.canMoveForward
                ? new WheellyContactsMessage(time, remoteTime, frontSensors, rearSensors, canMoveForward, canMoveBackward)
                : this;
    }

    /**
     * Returns the status with remote time instant set
     *
     * @param remoteTime the remote instant
     */
    public WheellyContactsMessage setRemoteTime(long remoteTime) {
        return remoteTime != this.remoteTime
                ? new WheellyContactsMessage(time, remoteTime, frontSensors, rearSensors, canMoveForward, canMoveBackward)
                : this;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyContactsMessage.class.getSimpleName() + "[", "]")
                .add("time=" + time)
                .add("remoteTime=" + remoteTime)
                .add("frontSensors=" + frontSensors)
                .add("rearSensors=" + rearSensors)
                .add("canMoveBackward=" + canMoveBackward)
                .add("canMoveForward=" + canMoveForward)
                .toString();
    }
}
