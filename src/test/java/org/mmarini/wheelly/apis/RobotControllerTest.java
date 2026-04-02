/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mmarini.RandomArgumentsGenerator;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_TARGET_RANGE;
import static org.mmarini.yaml.Utils.fromResource;

class RobotControllerTest {

    public static final long COMMAND_INTERVAL = 1000L;
    public static final long REACTION_INTERVAL = 300L;
    public static final long TEST_DURATION = 20000L;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataMove() {
        return RandomArgumentsGenerator.create(1234)
                .uniform(-65, 65)
                .uniform(0, 359)
                .uniform(1.0, 2.0, 9)
                .build(NUM_RANDOM_TEST_CASES);
    }

    private RobotController controller;

    @BeforeEach
    void setUp() throws IOException {
        JsonNode root = fromResource("/simRobot0Obstacles.yml");
        SimRobot robot = SimRobot.create(root, new File("simRobot0Obstacles.yml"));
        this.controller = new RobotController(REACTION_INTERVAL, COMMAND_INTERVAL, x -> 12d)
                .connectRobot(robot);
    }

    @AfterEach
    void tearDown() {
        this.controller.shutdown();
    }

    @ParameterizedTest
    @MethodSource("dataMove")
    void testBackward(int headDeg, int targetDeg, double targetDistance) {

        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);
        Point2D target = Complex.fromDeg(targetDeg).at(new Point2D.Double(), targetDistance);

        // When start the controller
        controller.start();
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::ready)
                .firstElement()
                .blockingGet();
        controller.execute(RobotCommands.backward(headDeg, target));
        // Wait for 5 simulated seconds
        controller.readRobotStatus()
                .filter(s -> s.simulationTime() >= TEST_DURATION)
                .firstElement()
                .ignoreElement()
                .blockingAwait();
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then the move method of robot should be invoked
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(greaterThanOrEqualTo(2)));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(TEST_DURATION, states.getLast().simulationTime());
        assertEquals(headDeg, states.getLast().headDirection().toIntDeg());
        assertTrue(states.getLast().halt());
        assertThat(states.getLast().location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));
        assertThat(states.getLast().direction().opposite(), angleCloseTo(targetDeg, 10));
    }

    @Test
    void testConnect() {
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

        // And
        lineSub.assertComplete();
        lineSub.assertNoErrors();
        List<RobotControllerStatusApi> lines = lineSub.values();
        assertThat(lines, hasSize(greaterThanOrEqualTo(2)));
        assertFalse(lines.getFirst().started());
        assertFalse(lines.getLast().started());
        assertFalse(lines.getLast().ready());
    }

    @ParameterizedTest
    @MethodSource("dataMove")
    void testForward(int headDeg, int targetDeg, double targetDistance) {

        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);
        Point2D target = Complex.fromDeg(targetDeg).at(new Point2D.Double(), targetDistance);

        // When start the controller
        controller.start();
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::ready)
                .firstElement()
                .blockingGet();
        controller.execute(RobotCommands.forward(headDeg, target));
        // Wait for 5 simulated seconds
        controller.readRobotStatus()
                .filter(s -> s.simulationTime() >= TEST_DURATION)
                .firstElement()
                .ignoreElement()
                .blockingAwait();
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then the move method of robot should be invoked
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(greaterThanOrEqualTo(2)));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(TEST_DURATION, states.getLast().simulationTime());
        assertEquals(headDeg, states.getLast().headDirection().toIntDeg());
        assertTrue(states.getLast().halt());
        assertThat(states.getLast().location(), pointCloseTo(target, DEFAULT_TARGET_RANGE));
        assertThat(states.getLast().direction(), angleCloseTo(targetDeg, 10));
    }

    @Test
    void testInference() {
        // Given a mock robot
        // and a controller
        TestSubscriber<RobotStatus> statusSub = new TestSubscriber<>();
        controller.readRobotStatus()
                .subscribe(statusSub);

        AtomicInteger counter = new AtomicInteger();
        Consumer<RobotStatus> inferenceMock = _ ->
                counter.incrementAndGet();
        controller.setOnInference(inferenceMock);

        // When start the controller
        controller.start();
        // And waiting for 2 inference status
        controller.readControllerStatus()
                .filter(RobotControllerStatusApi::inferencing)
                .limit(2)
                .blockingSubscribe();
        // And then shutting down
        controller.shutdown();
        // ANd waiting for shut down
        controller.readShutdown().blockingAwait();

        // Then ...
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(greaterThanOrEqualTo(2)));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(COMMAND_INTERVAL, states.getLast().simulationTime());

        assertThat(counter.get(), greaterThanOrEqualTo(2));
    }

    @ParameterizedTest
    @MethodSource("dataMove")
    void testRotate(int headDeg, int targetDeg, double targetDistance) {

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
        controller.execute(RobotCommands.rotate(headDeg, targetDeg));
        // Wait for 5 simulated seconds
        controller.readRobotStatus()
                .filter(s -> s.simulationTime() >= TEST_DURATION)
                .firstElement()
                .ignoreElement()
                .blockingAwait();
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // Then the move method of robot should be invoked
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(greaterThanOrEqualTo(2)));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(TEST_DURATION, states.getLast().simulationTime());
        assertEquals(headDeg, states.getLast().headDirection().toIntDeg());
        assertTrue(states.getLast().halt());
        assertThat(states.getLast().direction(), angleCloseTo(targetDeg, 10));
    }

    @ParameterizedTest
    @ValueSource(ints = {-65, -60, -45, -30, -15, -0, 15, 30, 45, 60, 65})
    void testScan(int headDeg) {
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
        controller.execute(RobotCommands.halt(headDeg));
        // Wait for 5 simulated seconds
        controller.readRobotStatus()
                .filter(s -> s.simulationTime() >= 5000)
                .firstElement()
                .ignoreElement()
                .blockingAwait();
        controller.shutdown();
        controller.readShutdown().blockingAwait();

        // And
        statusSub.assertComplete();
        statusSub.assertNoErrors();
        List<RobotStatus> states = statusSub.values();

        assertThat(states, hasSize(greaterThanOrEqualTo(2)));
        assertEquals(0L, states.getFirst().simulationTime());
        assertEquals(5000L, states.getLast().simulationTime());
        assertEquals(headDeg, states.getLast().headDirection().toIntDeg());
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
