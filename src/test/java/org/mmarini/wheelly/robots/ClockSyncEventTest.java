/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.robots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClockSyncEventTest {

    @Test
    void remoteTimeTest() {
        /*
         Given a clock event
         |--------|--------|------|
         0        20       30     50
         10000    10020    10030  10050
         t0       t0 + 20  t0+30  t0 + 50

         */
        final long t0 = System.currentTimeMillis();
        long dt = 50;
        long rdt = 10;
        long rt0 = 10000;
        long latency = (dt - rdt + 1) / 2;
        ClockSyncEvent clockEvent = new ClockSyncEvent(t0, rt0 + latency, rt0 + latency + rdt, t0 + dt);
        ClockConverter converter = clockEvent.converter();

        // When computing local localTime from rt0
        long localTime0 = converter.fromRemote(rt0);

        // Then
        assertEquals(0, localTime0 - t0);

        // When computing local localTime from rt0 + 10000
        long interval = 10000;
        long localTime = converter.fromRemote(rt0 + interval);

        assertEquals(interval, localTime - t0);
    }

}