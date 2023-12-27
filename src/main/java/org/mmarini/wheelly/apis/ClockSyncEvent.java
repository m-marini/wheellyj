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

import java.util.StringJoiner;

import static java.lang.String.format;

/**
 * The clock sync event
 */
public class ClockSyncEvent {
    /**
     * Returns the clock sync event
     *
     * @param originateTimestamp   the originate timestamp in local clock ticks (ms)
     * @param receiveTimestamp     the receive timestamp in remote clock ticks (ms)
     * @param transmitTimestamp    the transmit timestamp in remote clock ticks (ms)
     * @param destinationTimestamp the destination timestamp in local clock ticks (ms)
     */
    public static ClockSyncEvent create(long originateTimestamp, long receiveTimestamp, long transmitTimestamp, long destinationTimestamp) {
        return new ClockSyncEvent(originateTimestamp, receiveTimestamp, transmitTimestamp, destinationTimestamp);
    }

    /**
     * Returns the clock sync event for local clock (no Latency)
     */
    public static ClockSyncEvent create() {
        long now = System.currentTimeMillis();
        return new ClockSyncEvent(now, 0, 0, now);
    }

    /**
     * Returns the clock sync event from a clock sync string
     *
     * @param data                 the sync string
     * @param destinationTimestamp the destination timestamp in local clock ticks (ms)
     */
    public static ClockSyncEvent from(String data, long destinationTimestamp) {
        String[] fields = data.split(" ");
        if (fields.length != 4) {
            throw new IllegalArgumentException(format("Wrong clock message \"%s\"", data));
        }
        long originateTimestamp = Long.parseLong(fields[1]);
        long receiveTimestamp = Long.parseLong(fields[2]);
        long transmitTimestamp = Long.parseLong(fields[3]);
        return ClockSyncEvent.create(originateTimestamp, receiveTimestamp, transmitTimestamp, destinationTimestamp);
    }

    public final long destinationTimestamp;
    public final long originateTimestamp;
    public final long receiveTimestamp;
    public final long transmitTimestamp;
    private final long remoteOffset;

    /**
     * Creates the clock sync event
     * <pre>
     * |---------|-------|--------|
     * originate receive transmit destination
     * </pre>
     *
     * @param originateTimestamp   the originate timestamp in local clock ticks (ms)
     * @param receiveTimestamp     the receive timestamp in remote clock ticks (ms)
     * @param transmitTimestamp    the transmit timestamp in remote clock ticks (ms)
     * @param destinationTimestamp the destination timestamp in local clock ticks (ms)
     */
    protected ClockSyncEvent(long originateTimestamp, long receiveTimestamp, long transmitTimestamp, long destinationTimestamp) {
        this.originateTimestamp = originateTimestamp;
        this.receiveTimestamp = receiveTimestamp;
        this.transmitTimestamp = transmitTimestamp;
        this.destinationTimestamp = destinationTimestamp;
        this.remoteOffset = originateTimestamp + getLatency() - receiveTimestamp;
    }

    /**
     * Returns the remote timestamp from local timestamp
     *
     * @param localTime the local timestamp
     */
    public long fromLocal(long localTime) {
        return localTime - remoteOffset;
    }

    /**
     * Returns the local timestamp from remote timestamp
     *
     * @param remoteTime the remote timestamp
     */
    public long fromRemote(long remoteTime) {
        return remoteOffset + remoteTime;
    }

    /**
     * Returns the destination timestamp (local clock)
     */
    public long getDestinationTimestamp() {
        return destinationTimestamp;
    }

    /**
     * Returns the latency
     */
    public long getLatency() {
        long latency = (destinationTimestamp - originateTimestamp - transmitTimestamp + receiveTimestamp + 1) / 2;
        return latency;
    }

    /**
     * Returns the originate timestamp in local clock ticks (ms)
     */
    public long getOriginateTimestamp() {
        return originateTimestamp;
    }

    /**
     * Returns the received timestamp (remote clock)
     */
    public long getReceiveTimestamp() {
        return receiveTimestamp;
    }

    /**
     * Returns the remote offset
     */
    public long getRemoteOffset() {
        return remoteOffset;
    }

    /**
     * Returns the transmit timestamp (remote clock)
     */
    public long getTransmitTimestamp() {
        return transmitTimestamp;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ClockSyncEvent.class.getSimpleName() + "[", "]")
                .add("destinationTimestamp=" + destinationTimestamp)
                .add("originateTimestamp=" + originateTimestamp)
                .add("receiveTimestamp=" + receiveTimestamp)
                .add("transmitTimestamp=" + transmitTimestamp)
                .toString();
    }
}
