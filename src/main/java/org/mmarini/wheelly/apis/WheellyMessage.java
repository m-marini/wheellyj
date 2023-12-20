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

import java.util.Optional;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public abstract class WheellyMessage {
    public static <T extends WheellyMessage> Optional<T> fromLine(Timed<String> line) {
        if (line.value().startsWith("ct ")) {
            return Optional.of((T) WheellyContactsMessage.create(line));
        } else if (line.value().startsWith("mt ")) {
            return Optional.of((T) WheellyMotionMessage.create(line));
        } else if (line.value().startsWith("px ")) {
            return Optional.of((T) WheellyProxyMessage.create(line));
        } else if (line.value().startsWith("sv ")) {
            return Optional.of((T) WheellySupplyMessage.create(line));
        } else {
            return Optional.empty();
        }
    }

    protected final long time;
    protected final long remoteTime;

    /**
     * Creates wheelly status
     *
     * @param time       the status time
     * @param remoteTime
     */
    public WheellyMessage(long time, long remoteTime) {
        this.time = time;
        this.remoteTime = remoteTime;
    }

    /**
     * Returns the remote message time (remote clock)
     */
    public long getRemoteTime() {
        return remoteTime;
    }

    /**
     * Returns the local received time of message (local clock)
     */
    public long getTime() {
        return time;
    }
}
