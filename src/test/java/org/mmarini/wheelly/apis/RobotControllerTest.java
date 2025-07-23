/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RobotControllerTest {

    public static final long COMMAND_INTERVAL = 1000L;
    public static final long REACTION_INTERVAL = 300L;
    public static final long MESSAGE_INTERVAL = 500;

    static RobotController createController(RobotApi robot) {
        return new RobotController(REACTION_INTERVAL, COMMAND_INTERVAL, x -> 12d)
                .connectRobot(robot);
    }

    private MockRobot mockRobot;
    private RobotController controller;

    @BeforeEach
    void setUp() {
        this.mockRobot = spy(new MockRobot());
        this.controller = createController(mockRobot);
    }

    @AfterEach
    void tearDown() {
        this.controller.shutdown();
   }

    @Test
    void testConnect() throws Throwable {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotControllerStatusApi> lineSub = new TestSubscriber<>();
        controller.readControllerStatus()
                .subscribe(lineSub);

        // When start the controller
        controller.start();
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::ready)
                .firstElement()
                .blockingGet();
        controller.shutdown();
        controller.readShutdown()
                .blockingAwait();

        // Then the connect method should be invoked
        InOrder inOrder = inOrder(mockRobot);
        inOrder.verify(mockRobot).connect();
        inOrder.verify(mockRobot).close();

        // And
        lineSub.assertComplete();
        lineSub.assertNoErrors();
        List<RobotControllerStatusApi> lines = lineSub.values();
        assertThat(lines, hasSize(4));
        assertFalse(lines.getFirst().started());
        assertTrue(lines.get(1).started());
        assertTrue(lines.get(2).ready());
        assertFalse(lines.get(3).started());
    }

    @Test
    void testInference() {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);

        AtomicInteger counter = new AtomicInteger();
        Consumer<RobotStatus> inferenceMock = s ->
                counter.incrementAndGet();
        controller.setOnInference(inferenceMock);

        // When start the controller
        controller.start();
        // And generating 10 async messages delayed by 10 ms
        //mockRobot.sendStatus(10, MESSAGE_INTERVAL);
        Flowable.interval(1, TimeUnit.MILLISECONDS)
                .take(10)
                .doOnNext(ignored ->
                        mockRobot.sendStatus(MESSAGE_INTERVAL))
                .toList()
                .blockingGet();
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then ...
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(greaterThanOrEqualTo(2)));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(5000L, states.getLast().simulationTime());

        assertThat(counter.get(), greaterThanOrEqualTo(7));
    }

    @Test
    void testMove() {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);

        // When start the controller
        controller.start();
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::ready)
                .firstElement()
                .blockingGet();
        controller.execute(RobotCommands.move(Complex.DEG90, 10));
        mockRobot.sendStatus(10, MESSAGE_INTERVAL);
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then the move method of robot should be invoked
        verify(mockRobot, atLeast((int) (COMMAND_INTERVAL / MESSAGE_INTERVAL))).move(90, 10);

        // And
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(11));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(5000L, states.getLast().simulationTime());
    }

    @Test
    void testRead() {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);

        // When start the controller
        controller.start();
        mockRobot.sendStatus(10, MESSAGE_INTERVAL);
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then ...
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(11));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(5000L, states.getLast().simulationTime());
    }

    @Test
    void testScan() {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);

        // When start the controller
        controller.start();
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::ready)
                .firstElement()
                .blockingGet();
        controller.execute(RobotCommands.scan(Complex.DEG90));
        mockRobot.sendStatus(10, MESSAGE_INTERVAL);
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then the move method of robot should be invoked
        verify(mockRobot, atLeast((int) (COMMAND_INTERVAL / MESSAGE_INTERVAL))).scan(90);

        // And
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(11));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(5000L, states.getLast().simulationTime());
    }

    @Test
    void testShutdown() {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotControllerStatusApi> lineSub = new TestSubscriber<>();
        controller.readControllerStatus()
                .subscribe(lineSub);

        // When start the controller
        controller.start();
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::ready)
                .firstElement()
                .blockingGet();
        controller.shutdown();
        boolean result = controller.readShutdown()
                .blockingAwait(1000, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }
}
