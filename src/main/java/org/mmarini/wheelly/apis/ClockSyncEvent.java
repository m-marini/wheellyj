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

import static java.lang.String.format;

/**
 * The clock sync event
 *
 * @param originateTimestamp   the originate timestamp in local clock ticks (ms)
 * @param receiveTimestamp     the receive timestamp in remote clock ticks (ms)
 * @param transmitTimestamp    the transmit timestamp in remote clock ticks (ms)
 * @param destinationTimestamp the destination timestamp in local clock ticks (ms)
 */
public record ClockSyncEvent(long originateTimestamp,
                             long receiveTimestamp, long transmitTimestamp,
                             long destinationTimestamp) {
    /**
     * Returns the clock sync event from a clock sync string
     *
     * @param data                 the sync string
     * @param destinationTimestamp the destination timestamp in local clock ticks (ms)
     */
    static ClockSyncEvent from(String data, long destinationTimestamp) {
        String[] fields = data.split(" ");
        if (fields.length != 4) {
            throw new IllegalArgumentException(format("Wrong clock message \"%s\"", data));
        }
        long originateTimestamp = Long.parseLong(fields[1]);
        long receiveTimestamp = Long.parseLong(fields[2]);
        long transmitTimestamp = Long.parseLong(fields[3]);
        return new ClockSyncEvent(originateTimestamp, receiveTimestamp, transmitTimestamp, destinationTimestamp);
    }

    /**
     * Returns the clock converter of the event
     */
    public ClockConverter converter() {
        long offset = remoteOffset();
        return new ClockConverter() {
            @Override
            public long fromRemote(long time) {
                return time + offset;
            }

            @Override
            public long fromSimulation(long time) {
                return time - offset;
            }
        };
    }

    public long latency() {
        return (destinationTimestamp - originateTimestamp - transmitTimestamp + receiveTimestamp + 1) / 2;
    }

    public long remoteOffset() {
        return originateTimestamp + latency() - receiveTimestamp;
    }
}
