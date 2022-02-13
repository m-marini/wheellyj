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

import io.reactivex.Flowable;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvoker;
import org.glassfish.jersey.client.rx.rxjava2.RxFlowableInvokerProvider;
import org.mmarini.json.Utils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.time.Instant;

/**
 *
 */
public class ClockSyncEvent {
    /**
     * @param originateTimestamp
     * @param receiveTimestamp
     * @param transmitTimestamp
     * @param destinationTimestamp
     */
    public static ClockSyncEvent create(long originateTimestamp, long receiveTimestamp, long transmitTimestamp, long destinationTimestamp) {
        return new ClockSyncEvent(originateTimestamp, receiveTimestamp, transmitTimestamp, destinationTimestamp);
    }

    public static ClockSyncEvent from(String data, long destinationTimestamp) {
        String[] fields = data.split(" ");
        if (fields.length != 4) {
            throw new IllegalArgumentException("Wrong number of clock fields");
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

    /**
     * @param originateTimestamp
     * @param receiveTimestamp
     * @param transmitTimestamp
     * @param destinationTimestamp
     */
    protected ClockSyncEvent(long originateTimestamp, long receiveTimestamp, long transmitTimestamp, long destinationTimestamp) {
        this.originateTimestamp = originateTimestamp;
        this.receiveTimestamp = receiveTimestamp;
        this.transmitTimestamp = transmitTimestamp;
        this.destinationTimestamp = destinationTimestamp;
    }

    /**
     *
     */
    public long getDestinationTimestamp() {
        return destinationTimestamp;
    }

    /**
     * Returns the latency
     */
    public long getLatency() {
        return (destinationTimestamp - originateTimestamp - transmitTimestamp + receiveTimestamp + 1) / 2;
    }

    /**
     *
     */
    public long getOriginateTimestamp() {
        return originateTimestamp;
    }

    /**
     *
     */
    public long getReceiveTimestamp() {
        return receiveTimestamp;
    }

    /**
     *
     */
    public long getRemoteOffset() {
        return originateTimestamp + getLatency() - receiveTimestamp;
    }

    /**
     *
     */
    public long getTransmitTimestamp() {
        return transmitTimestamp;
    }
}
