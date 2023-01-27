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

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class RobotControllerTest {

    public static final long CONNECTION_RETRY_INTERVAL = 1000L;
    public static final long INTERVAL = 100L;
    public static final long COMMAND_INTERVAL = 1000L;
    public static final long REACTION_INTERVAL = 300L;
    public static final long WATCHDOG_INTERVAL = 1000L;

    static RobotController createController(RobotApi robot) {
        return new RobotController(robot, INTERVAL, REACTION_INTERVAL, COMMAND_INTERVAL, CONNECTION_RETRY_INTERVAL, WATCHDOG_INTERVAL);
    }

    @Test
    void close() throws IOException {
        RobotApi robot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(robot).configure();
        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.setOnError(consumer);

        rc.stepUp(); // connect
        rc.stepUp(); // configure
        rc.stepUp(); // close

        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait retry
        long elapsed = System.currentTimeMillis() - ts;
        assertThat(elapsed, greaterThanOrEqualTo(CONNECTION_RETRY_INTERVAL));

        rc.stepUp(); // connect

        verify(robot, times(1)).close();
        verify(robot, times(2)).connect();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void configure() throws IOException {
        RobotApi robot = mock(RobotApi.class);
        RobotController rc = createController(robot);

        rc.stepUp();

        verify(robot).connect();

        rc.stepUp();

        verify(robot).configure();
        rc.shutdown();
    }

    @Test
    void configureError() throws IOException {
        RobotApi robot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(robot).configure();
        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.setOnError(consumer);

        rc.stepUp(); // connect
        rc.stepUp(); // configure
        rc.stepUp(); // close

        verify(robot, times(1)).close();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void connect() throws IOException {
        RobotApi robot = mock();
        RobotController rc = createController(robot);

        rc.stepUp();

        verify(robot).connect();
        rc.shutdown();
    }

    @Test
    void connectError() throws IOException {
        RobotApi robot = mock();
        IOException error = new IOException("Error");
        doThrow(error).when(robot).connect();
        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.setOnError(consumer);

        rc.stepUp(); // connect 1

        verify(robot, times(1)).connect();
        verify(consumer, times(1)).accept(error);

        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait retry
        long elapsed = System.currentTimeMillis() - ts;
        assertThat(elapsed, greaterThanOrEqualTo(CONNECTION_RETRY_INTERVAL));

        rc.stepUp(); // connect 2
        verify(robot, times(2)).connect();
        verify(consumer, times(2)).accept(error);
        rc.shutdown();
    }

    @Test
    void inference() throws InterruptedException, IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        RobotController rc = createController(robot);
        Consumer<RobotStatus> consumer = mock();
        rc.setOnInference(consumer);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure
        Thread.sleep(INTERVAL * 8);
        verify(robot, atLeast(7)).tick(INTERVAL);
        verify(consumer, atLeast(2)).accept(any());
        rc.shutdown();
    }

    @Test
    void move() throws IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure
        rc.stepUp(); // Handle scan

        rc.moveRobot(90, 10);
        rc.stepUp(); // Handle move

        verify(robot).move(90, 10);
        rc.shutdown();
    }

    @Test
    void moveError() throws IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        IOException error = new IOException("Error");
        doThrow(error).when(robot).move(anyInt(), anyInt());

        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.setOnError(consumer);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure
        rc.stepUp(); // Handle scan
        rc.moveRobot(90, 10);
        rc.stepUp(); // Handle move

        rc.stepUp(); // Close

        verify(robot).move(90, 10);
        verify(robot).close();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void read() throws InterruptedException, IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        RobotController rc = createController(robot);
        Consumer<RobotStatus> consumer = mock();
        rc.setOnStatusReady(consumer);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure
        Thread.sleep(INTERVAL * 2);
        verify(robot, times(2)).tick(INTERVAL);
        verify(consumer, times(3)).accept(any());
        rc.shutdown();
    }

    @Test
    void scan() {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        rc.moveSensor(90);
        rc.stepUp(); // Handle scan

        verify(robot).scan(90);
        rc.shutdown();
    }

    @Test
    void scanError() throws IOException {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        IOException error = new IOException("Error");
        doThrow(error).when(robot).scan(anyInt());

        RobotController rc = createController(robot);
        Consumer<Throwable> consumer = mock();
        rc.setOnError(consumer);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure

        rc.moveSensor(90);
        rc.stepUp(); // Handle scan
        rc.stepUp(); // Close

        verify(robot).scan(90);
        verify(robot).close();
        verify(consumer, times(1)).accept(error);
        rc.shutdown();
    }

    @Test
    void shutdown() throws InterruptedException {
        Mock1Robot robot = spy(new Mock1Robot());
        RobotController rc = createController(robot);
        Consumer<RobotStatus> consumer = mock();
        rc.setOnInference(consumer);
        rc.start();
        Thread.sleep(1000);
        rc.shutdown();
        boolean result = rc.readShutdown().blockingAwait(1000, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

    @Test
    void waitInterval() {
        Mock1Robot robot = spy(new Mock1Robot());
        robot.setTime(System.currentTimeMillis());
        RobotController rc = createController(robot);
        rc.stepUp(); // Connect
        rc.stepUp(); // Configure
        rc.moveSensor(90);
        rc.stepUp(); // Handle scan
        rc.stepUp(); // Handle move

        long ts = System.currentTimeMillis();
        rc.stepUp(); // wait interval
        long elapsed = System.currentTimeMillis() - ts;
        assertThat(elapsed, greaterThanOrEqualTo(INTERVAL));

        rc.moveSensor(-90);
        rc.stepUp(); // Handle scan
        InOrder inOrder = inOrder(robot);
        inOrder.verify(robot).scan(90);
        inOrder.verify(robot).scan(-90);
        rc.shutdown();
    }

    static class Mock1Robot extends MockRobot {
        @Override
        public void configure() {
            if (onStatusReady != null) {
                onStatusReady.accept(getStatus());
            }
        }

        @Override
        public void tick(long dt) {
            super.tick(dt);
            if (onStatusReady != null) {
                onStatusReady.accept(getStatus());
            }
            try {
                Thread.sleep(dt);
            } catch (InterruptedException e) {
            }
        }
    }
}