/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class MqttRobotTest {
    public static final String BROKER_URL = "tcp://localhost:1883";
    public static final String WRONG_BROKER_URL = "tcp://localhost:1884";
    public static final String USER = "testUser";
    public static final String PASSWORD = "testPassword";
    public static final String SENSOR_TOPIC = "/test/sensors";
    public static final String COMMAND_TOPIC = "/test/commands";
    public static final RobotSpec ROBOT_SPEC = new RobotSpec(3, Complex.fromDeg(15), 0.18, Complex.fromDeg(120));
    public static final int CONFIGURE_TIMEOUT = 1000;
    public static final String CAMERA_EVENT_TEXT = "qr 0 A 200 200 0 0 0 0 0 0 0 0";
    public static final int RETRY_INTERVAL = 500;
    public static final String CLIENT_ID = "testRobot";
    public static final int TIMEOUT = 2000;

    private MqttRobot robot;
    private TestSubscriber<RobotStatusApi> statusSub;
    private TestSubscriber<String> readSub;
    private TestSubscriber<String> writeSub;
    private TestSubscriber<Throwable> errorSub;
    private TestSubscriber<WheellyMessage> messageSub;
    private TestSubscriber<CameraEvent> cameraSub;
    private MockMqttClient mockClient;

    void createRobot(String url) {
        robot = assertDoesNotThrow(() -> MqttRobot.create(url, CLIENT_ID, USER, PASSWORD, SENSOR_TOPIC, COMMAND_TOPIC,
                CONFIGURE_TIMEOUT, RETRY_INTERVAL, ROBOT_SPEC, "cs 100 100", "cs 200 200"));
        assertNotNull(robot);
        statusSub = new TestSubscriber<>();
        errorSub = new TestSubscriber<>();
        messageSub = new TestSubscriber<>();
        cameraSub = new TestSubscriber<>();
        readSub = new TestSubscriber<>();
        writeSub = new TestSubscriber<>();
        robot.readRobotStatus().subscribe(statusSub);
        robot.readErrors().subscribe(errorSub);
        robot.readMessages().subscribe(messageSub);
        robot.readCamera().subscribe(cameraSub);
        robot.readReadLine().subscribe(readSub);
        robot.readWriteLine().subscribe(writeSub);
        mockClient = new MockMqttClient();
        mockClient.readMessages()
                .subscribe(msg -> {
                    if (msg.startsWith("ck ")) {
                        mockClient.send(msg + " 0 0");
                    } else if (msg.startsWith("cs ")) {
                        mockClient.send("// " + msg);
                    }
                });
        mockClient.start();
        mockClient.readConnected().blockingAwait();
    }

    @BeforeEach
    void setUp() {
        assertDoesNotThrow(() -> createRobot(BROKER_URL));
    }

    @AfterEach
    void tearDown() {
        if (mockClient != null) {
            assertDoesNotThrow(() -> mockClient.close());
        }
    }

    @Test
    void testCameraMessage() {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for configured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        mockClient.send(CAMERA_EVENT_TEXT);
        // And wait for message
        robot.readMessages()
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.close();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        readSub.assertNoErrors();
        readSub.assertComplete();
        readSub.assertValueSequence(List.of(
                "ck 0 0 0",
                "// cs 100 100",
                "// cs 200 200",
                CAMERA_EVENT_TEXT
        ));

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        writeSub.assertValueCount(4);

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(1);

        List<CameraEvent> msgs = cameraSub.values();
        assertThat(msgs.getFirst().simulationTime(), greaterThan(0L));
    }

    @Test
    void testConfiguration() {
        // Given...

        // When ....
        robot.connect();

        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        robot.close();

        // Then ...
        statusSub.assertNoErrors();
        statusSub.assertComplete();

        List<RobotStatusApi> msgs = statusSub.values();

        assertThat(msgs.stream()
                .filter(RobotStatusApi::configured)
                .count(), greaterThan(0L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        List<String> wr = writeSub.values();
        assertThat(wr, hasSize(greaterThanOrEqualTo(3)));
        writeSub.assertValueAt(0, "ck 0");
        writeSub.assertValueAt(1, "cs 100 100");
        writeSub.assertValueAt(2, "cs 200 200");

        readSub.assertNoErrors();
        readSub.assertComplete();
        readSub.assertValueCount(3);
        readSub.assertValueSequence(List.of("ck 0 0 0", "// cs 100 100", "// cs 200 200"));
    }

    @Test
    void testConnect() {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for connected
        robot.readRobotStatus()
                .filter(RobotStatusApi::connected)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.close();

        // Then ...
        statusSub.assertNoErrors();
        statusSub.assertComplete();

        List<RobotStatusApi> msgs = statusSub.values();
        assertThat(msgs, hasSize(greaterThanOrEqualTo(4)));

        assertFalse(msgs.getFirst().connected());
        assertFalse(msgs.getFirst().connecting());
        assertFalse(msgs.getFirst().configuring());
        assertFalse(msgs.getFirst().configured());

        assertFalse(msgs.get(1).connected());
        assertTrue(msgs.get(1).connecting());
        assertFalse(msgs.get(1).configuring());
        assertFalse(msgs.get(1).configured());

        assertTrue(msgs.get(2).connected());
        assertFalse(msgs.get(2).connecting());
        assertFalse(msgs.get(2).configuring());
        assertFalse(msgs.get(2).configured());

        assertFalse(msgs.getLast().connected());
        assertFalse(msgs.getLast().connecting());
        assertFalse(msgs.getLast().configuring());
        assertFalse(msgs.getLast().configured());

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        readSub.assertNoErrors();
        readSub.assertComplete();
        readSub.assertValueCount(0);

        writeSub.assertNoErrors();
        writeSub.assertComplete();

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

    @Test
    void testHalt() {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for configured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.halt();
        // And wait for message
        robot.readWriteLine()
                .filter(m -> m.startsWith("ha "))
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        // Then
        TestSubscriber<String> mockSub = mockClient.subscriber();
        mockSub.assertNoErrors();
        assertThat(mockSub.values().stream()
                .filter("ha"::equals)
                .count(), greaterThanOrEqualTo(2L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        readSub.assertNoErrors();
        readSub.assertComplete();
        readSub.assertValueSequence(List.of(
                "ck 0 0 0",
                "// cs 100 100",
                "// cs 200 200"
        ));

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        assertThat(writeSub.values().stream()
                .filter("ha"::equals)
                .count(), greaterThanOrEqualTo(2L));

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);

    }

    @Test
    void testMisconfiguration() {
        // Given...
        assertDoesNotThrow(() -> {
            mockClient.readConnected().blockingAwait();
            mockClient.close();
        });


        mockClient = new MockMqttClient().start();
        mockClient.readMessages()
                .subscribe(msg -> {
                    if (msg.startsWith("ck ")) {
                        mockClient.send(msg + " 0 0");
                    } else if (msg.startsWith("cs ")) {
                        mockClient.send("!! Bad " + msg);
                    }
                });
        mockClient.readConnected().blockingAwait();

        // When ....
        robot.connect();

        robot.readRobotStatus()
                .filter(RobotStatusApi::configuring)
                .limit(2)
                .ignoreElements()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        robot.close();

        // Then ...
        statusSub.assertNoErrors();
        statusSub.assertComplete();

        List<RobotStatusApi> msgs = statusSub.values();
        assertThat(msgs.stream()
                .filter(RobotStatusApi::configured)
                .count(), equalTo(0L));
        assertThat(msgs.stream()
                .filter(RobotStatusApi::configuring)
                .count(), greaterThanOrEqualTo(2L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);

        writeSub.assertNoErrors();
        writeSub.assertComplete();

        readSub.assertNoErrors();
        readSub.assertComplete();
    }

    @Test
    void testMove() {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for configured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.move(90, 60);
        // And wait for message
        robot.readWriteLine()
                .filter(m -> m.startsWith("mv "))
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.close();

        // Then
        TestSubscriber<String> mockSub = mockClient.subscriber();
        mockSub.assertNoErrors();
        assertThat(mockSub.values(), hasItem("mv 90 60"));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        readSub.assertNoErrors();
        readSub.assertComplete();

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        assertThat(writeSub.values(), hasItem("mv 90 60"));

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

    @Test
    void testProxyMessage() {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for configured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        mockClient.send("px 0 0 0 0.0 0.0 0");
        // And wait for message
        robot.readReadLine()
                .filter(m -> m.startsWith("px "))
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.close();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        readSub.assertNoErrors();
        readSub.assertComplete();
        readSub.assertValueSequence(List.of(
                "ck 0 0 0",
                "// cs 100 100",
                "// cs 200 200",
                "px 0 0 0 0.0 0.0 0"
        ));

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        writeSub.assertValueCount(4);

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(1);

        List<WheellyMessage> msgs = messageSub.values();
        assertThat(msgs.getFirst(), isA(WheellyProxyMessage.class));
        assertThat(msgs.getFirst().simulationTime(), greaterThan(0L));

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

    @Test
    void testScan() {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for configured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.scan(-45);
        // And wait for message
        robot.readWriteLine()
                .filter(m -> m.startsWith("sc "))
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.close();

        // Then
        TestSubscriber<String> mockSub = mockClient.subscriber();
        mockSub.assertNoErrors();
        assertThat(mockSub.values(), hasItem("sc -45"));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        readSub.assertNoErrors();
        readSub.assertComplete();

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        assertThat(writeSub.values(), hasItem("sc -45"));

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

    @Test
    void testWrongBrokerConnect() {
        // Given ...
        assertDoesNotThrow(() -> createRobot(WRONG_BROKER_URL));
        // When connect
        robot.connect();
        // and wait for error
        robot.readErrors()
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        // and close
        robot.close();

        // Then error should be sent
        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(1);
        List<Throwable> errors = this.errorSub.values();
        assertThat(errors.getFirst(), isA(MqttException.class));

        // And status should be closed
        statusSub.assertNoErrors();
        statusSub.assertComplete();

        List<RobotStatusApi> msgs = statusSub.values();
        assertThat(msgs, hasSize(greaterThanOrEqualTo(3)));

        assertFalse(msgs.getFirst().connected());
        assertFalse(msgs.getFirst().connecting());
        assertFalse(msgs.getFirst().configuring());
        assertFalse(msgs.getFirst().configured());

        assertFalse(msgs.get(1).connected());
        assertTrue(msgs.get(1).connecting());
        assertFalse(msgs.get(1).configuring());
        assertFalse(msgs.get(1).configured());

        assertFalse(msgs.get(2).connected());
        assertFalse(msgs.get(2).connecting());
        assertFalse(msgs.get(2).configuring());
        assertFalse(msgs.get(2).configured());

        readSub.assertNoErrors();
        readSub.assertComplete();
        readSub.assertValueCount(0);

        writeSub.assertNoErrors();
        writeSub.assertComplete();
        writeSub.assertValueCount(0);

        messageSub.assertNoErrors();
        messageSub.assertComplete();
        messageSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

}