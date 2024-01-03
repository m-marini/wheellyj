package org.mmarini.wheelly.apis;

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