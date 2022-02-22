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

import java.time.Instant;

/**
 * The remote clock map the local clock to the remote clock ticks
 */
public class RemoteClock {

    /**
     * Returns a remote clock
     *
     * @param offset the local offset of remote clock
     */
    public static RemoteClock create(long offset) {
        return new RemoteClock(offset);
    }

    public final long offset;

    /**
     * Creates a remote clock
     *
     * @param offset the local offset of remote clock
     */
    protected RemoteClock(long offset) {
        this.offset = offset;
    }

    /**
     * Returns the local instant of a remote clock ticks
     *
     * @param millis the remote clock ticks
     */
    public Instant fromRemote(long millis) {
        return Instant.ofEpochMilli(offset + millis);
    }

    /**
     * Returns the remote clock ticks of a local instant
     *
     * @param instant the local instant
     */
    public long toRemote(Instant instant) {
        return instant.toEpochMilli() - offset;
    }
}
