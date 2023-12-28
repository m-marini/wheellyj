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

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.hamcrest.MockitoHamcrest;

import java.awt.geom.Point2D;
import java.util.Random;
import java.util.function.Consumer;

import static java.lang.Math.toRadians;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.apis.SimRobot.MAX_PPS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

class SimRobotTest {

    public static final int SEED = 1234;
    public static final long INTERVAL = 500;
    public static final float GRID_SIZE = 0.2f;
    private static final double PULSES_EPSILON = 1;

    @Test
    void configureTest() {
        // Given a sim robot connected
        SimRobot robot = createRobot();
        robot.connect();

        // and a clock sync event consumer
        Consumer<ClockSyncEvent> onClock = mock();
        robot.setOnClock(onClock);

        // and a motion consumer
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // and a proxy consumer
        Consumer<WheellyProxyMessage> onProxy = mock();
        robot.setOnProxy(onProxy);

        // and a contacts consumer
        Consumer<WheellyContactsMessage> onContacts = mock();
        robot.setOnContacts(onContacts);

        // When configure
        robot.configure();

        // Then remote clock should be 0
        assertEquals(0L, robot.getRemoteTime());

        // and the consumer should be invoked
        verify(onClock, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("receiveTimestamp", equalTo(0L)),
                        hasProperty("transmitTimestamp", equalTo(0L))
                )));
        verify(onMotion, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(0L))
                )
        ));
        verify(onProxy, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(0L))
                )
        ));
        verify(onContacts, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(0L))
                )
        ));
    }

    @Test
    void connectTest() {
        SimRobot robot = createRobot();
        robot.connect();
        assertEquals(new Point2D.Float(), robot.getLocation());
        assertEquals(0, robot.getDirection());
        assertEquals(0, robot.getSensorDirection());
        assertEquals(0f, robot.getEchoDistance());
        assertEquals(0L, robot.getRemoteTime());
    }

    /**
     * Given a simulated robot with an obstacles map grid of 0.2 m without obstacles
     */
    private SimRobot createRobot() {
        Random random = new Random(SEED);
        return new SimRobot(new MapBuilder(new GridTopology(GRID_SIZE)).build(),
                random, 0, 0, toRadians(15), MAX_PPS,
                INTERVAL, INTERVAL);
    }

    @Test
    void createTest() {
        SimRobot robot = createRobot();
        assertEquals(new Point2D.Float(), robot.getLocation());
        assertEquals(0, robot.getDirection());
        assertEquals(0, robot.getSensorDirection());
        assertEquals(0f, robot.getEchoDistance());
        assertEquals(0L, robot.getRemoteTime());
    }

    @Test
    void moveFrom0To0ByMAX() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 0 DEG at max speed
        robot.move(0, MAX_PPS);
        // And ticks 5 time for 100 ms
        for (int i = 0; i < 5; i++) {
            robot.tick(100);
        }

        // Then robot should emit motion at (0, 18) ~= (0, MAX_PPS * 0.5)
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(500L)),
                        hasProperty("XPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("YPulses", closeTo(18, PULSES_EPSILON)),
                        hasProperty("direction", equalTo(0))
                )
        ));
    }

    @Test
    void moveFrom0To0By_MAX() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 0 DEG at max speed
        robot.move(0, -MAX_PPS);
        // And ticks 5 time for 100 ms
        for (int i = 0; i < 5; i++) {
            robot.tick(100);
        }

        // Then robot should emit motion at (0, -18) ~= (0, -MAX_PPS * 0.5)
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(500L)),
                        hasProperty("XPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("YPulses", closeTo(-18, PULSES_EPSILON)),
                        hasProperty("direction", equalTo(0))
                )
        ));
        // And remote time should be 500L
        assertEquals(500L, robot.getRemoteTime());
    }

    @Test
    void rotateFrom0To5() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 5 DEG at 0 speed
        robot.move(5, 0);
        // And ticks 5 time for 100 ms
        for (int i = 0; i < 5; i++) {
            robot.tick(100);
        }

        // Then robot should emit motion at (0, 0) toward 5 DEG
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(500L)),
                        hasProperty("XPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("YPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("direction", greaterThanOrEqualTo(4)),
                        hasProperty("direction", lessThanOrEqualTo(6))
                )
        ));
        // And remote time should be 500L
        assertEquals(500L, robot.getRemoteTime());
    }

    @Test
    void rotateFrom0To90() {
        // Given a robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // When move to 90 DEG at 0 speed
        robot.move(90, 0);
        // And ticks 5 time for 100 ms
        for (int i = 0; i < 5; i++) {
            robot.tick(100);
        }

        // Then robot should emit motion at (0, 0) toward approx 90 DEG
        verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(500L)),
                        hasProperty("XPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("YPulses", closeTo(0, PULSES_EPSILON)),
                        hasProperty("direction", greaterThanOrEqualTo(89)),
                        hasProperty("direction", lessThanOrEqualTo(91))
                )
        ));
        // And remote time should be 500L
        assertEquals(500L, robot.getRemoteTime());
    }

    @Test
    void scanTest() {
        // Given a sim robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();

        // and a proxy consumer
        Consumer<WheellyProxyMessage> onProxy = mock();
        robot.setOnProxy(onProxy);

        // When scan 90 DEG
        robot.scan(90);
        // And tick for 500ms
        robot.tick(500);

        // the consumer should be invoked
        verify(onProxy, times(1)).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(500L)),
                        hasProperty("sensorDirection", equalTo(90))
                )));
        // And remote time should be 500L
        assertEquals(500L, robot.getRemoteTime());
    }

    @Test
    void tickForStatus() {
        // Given a sim robot connected and configured
        SimRobot robot = createRobot();
        robot.connect();
        robot.configure();

        // and a motion consumer
        Consumer<WheellyMotionMessage> onMotion = mock();
        robot.setOnMotion(onMotion);

        // and a proxy consumer
        Consumer<WheellyProxyMessage> onProxy = mock();
        robot.setOnProxy(onProxy);

        // and a contacts consumer
        Consumer<WheellyContactsMessage> onContacts = mock();
        robot.setOnContacts(onContacts);

        // When tick 3 time for 500ms
        for (int i = 0; i < 3; i++) {
            robot.tick(INTERVAL);
        }

        // Then contacts consumer should be never invoked
        verify(onContacts, never()).accept(any());

        // And remote time should be 500*3
        assertEquals(1500L, robot.getRemoteTime());

        // and motion consumer should be invoked by 500 ms intervals
        InOrder onMotionOrder = inOrder(onMotion);
        onMotionOrder.verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(INTERVAL))
                )
        ));
        onMotionOrder.verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(2 * INTERVAL))
                )
        ));
        onMotionOrder.verify(onMotion).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(3 * INTERVAL))
                )
        ));
        onMotionOrder.verifyNoMoreInteractions();

        // and proxy consumer should be invoked by 500 ms intervals
        InOrder onProxyOrder = inOrder(onProxy);
        onProxyOrder.verify(onProxy).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(INTERVAL))
                )
        ));
        onProxyOrder.verify(onProxy).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(2 * INTERVAL))
                )
        ));
        onProxyOrder.verify(onProxy).accept(MockitoHamcrest.argThat(
                allOf(
                        hasProperty("remoteTime", equalTo(3 * INTERVAL))
                )
        ));
        onProxyOrder.verifyNoMoreInteractions();
    }
}