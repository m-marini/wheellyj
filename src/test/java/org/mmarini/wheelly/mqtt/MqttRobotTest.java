/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.mqtt;

import io.reactivex.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;

class MqttRobotTest {
    public static final String BROKER_URL = "tcp://localhost:1883";
    public static final String WRONG_BROKER_URL = "tcp://localhost:1884";
    public static final String USER = "wheellyj";
    public static final String PASSWORD = "wheellyj";
    public static final int CONFIGURE_TIMEOUT = 1000;
    public static final String CAMERA_EVENT_TEXT = "0,A,200,200,0,0,0,0,0,0,0,0";
    public static final int RETRY_INTERVAL = 500;
    public static final String CLIENT_ID = "testRobot";
    public static final int TIMEOUT = 2000;
    public static final String ROBOT_ID = "wheelly";
    public static final String QR_ID = "wheellyqr";
    public static final int CLOSE_DELAY = 100;
    public static final int MESSAGE_DELAY = 100;
    public static final String CONFIG_STRING = "{\"test\":1}";

    private MqttRobot robot;
    private TestSubscriber<RobotStatusApi> statusSub;
    private TestSubscriber<Throwable> errorSub;
    private MockMqttClient mockClient;
    private List<WheellyContactsMessage> contacts;
    private List<WheellyMotionMessage> motions;
    private List<CameraEvent> cameras;

    void createRobot(String url) throws MqttException {
        robot = assertDoesNotThrow(() -> MqttRobot.create(url, CLIENT_ID, USER, PASSWORD,
                ROBOT_ID, QR_ID,
                CONFIGURE_TIMEOUT, RETRY_INTERVAL, DEFAULT_ROBOT_SPEC,
                new String[]{CONFIG_STRING}));
        assertNotNull(robot);
        robot.readRobotStatus().subscribe(statusSub);
        robot.readErrors().subscribe(errorSub);
        robot.onCamera(cameras::add);
        robot.onContacts(contacts::add);
        robot.onMotion(motions::add);
        mockClient = assertDoesNotThrow(MockMqttClient::new);
        mockClient.start();
        mockClient.readCommands().subscribe(
                t ->
                        mockClient.publish(t._1 + "/res", t._2)
        );
    }

    @BeforeEach
    void setUp() throws MqttException {
        statusSub = new TestSubscriber<>();
        errorSub = new TestSubscriber<>();
        cameras = new ArrayList<>();
        this.motions = new ArrayList<>();
        this.contacts = new ArrayList<>();
        createRobot(BROKER_URL);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockClient != null) {
            mockClient.close();
        }
    }

    @Test
    void testCameraMessage() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        mockClient.sendQr("qr", CAMERA_EVENT_TEXT);
        Completable.timer(MESSAGE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();
        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        assertThat(cameras, hasSize(1));

        List<CameraEvent> msgs = cameras;
        assertThat(msgs.getFirst().time(), greaterThan(0L));
        assertEquals("A", msgs.getFirst().qrCode());
    }

    @Test
    void testConfiguration() throws IOException {
        // Given...

        // When ....
        robot.connect();

        robot.readRobotStatus()
                .any(RobotStatusApi::configured)
                .blockingGet();

        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();
        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();
        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();
        mockClient.close();

        // Then ...
        statusSub.assertNoErrors();
        statusSub.assertComplete();

        List<RobotStatusApi> msgs = statusSub.values();
        boolean configured = msgs.stream().anyMatch(RobotStatusApi::configured);
        assertTrue(configured);

        TestSubscriber<Tuple2<String, MqttMessage>> subscriber = mockClient.subscriber();
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        assertEquals(CONFIG_STRING, subscriber.values().stream()
                .filter(t -> "cmd/wheelly/cf".equals(t._1))
                .map(t -> new String(t._2.getPayload()))
                .findAny()
                .get());

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertNoValues();

        assertThat(cameras, empty());
    }

    @Test
    void testConnect() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for connected
        robot.readRobotStatus()
                .filter(RobotStatusApi::connected)
                .firstElement()
                .ignoreElement()
                .blockingAwait();
        robot.close();
        robot.readRobotStatus().blockingSubscribe();

        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        // Then ...
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

        assertFalse(msgs.getLast().connected());
        assertFalse(msgs.getLast().connecting());

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertNoValues();

        assertThat(cameras, empty());
        assertThat(motions, empty());
        assertThat(contacts, empty());
    }

    @Test
    void testContactsMessage() throws IOException {
        // Given ...
        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        mockClient.sendRobot("ct", "0,1,0,1,0");
        Completable.timer(MESSAGE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();
        mockClient.close();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        assertThat(contacts, hasSize(1));

        List<WheellyContactsMessage> msgs = contacts;
        assertThat(msgs.getFirst().time(), greaterThanOrEqualTo(10L));
        assertTrue(msgs.getFirst().frontSensors());
        assertFalse(msgs.getFirst().rearSensors());
        assertTrue(msgs.getFirst().canMoveForward());
        assertFalse(msgs.getFirst().canMoveBackward());
        assertThat(cameras, empty());
    }

    @Test
    void testHalt() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.halt();
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        // Then
        TestSubscriber<Tuple2<String, MqttMessage>> mockSub = mockClient.subscriber();
        mockSub.assertNoErrors();
        mockSub.assertComplete();
        assertThat(mockSub.values().stream()
                .filter(t -> t._1.endsWith("/ha"))
                .count(), greaterThanOrEqualTo(2L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        assertThat(cameras, empty());
    }

    @Test
    void testMisconfiguration() throws IOException, MqttException {
        // Given...
        mockClient.close();
        mockClient = new MockMqttClient().start();
        mockClient.readCommands()
                .observeOn(Schedulers.computation())
                .subscribe(msg ->
                        mockClient.publish(msg._1 + "/res", "!!Bad")
                );

        // When ....
        robot.connect();

        robot.readRobotStatus()
                .filter(RobotStatusApi::configuring)
                .limit(2)
                .ignoreElements()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        robot.close();
        robot.readRobotStatus().blockingSubscribe();
        mockClient.close();

        // Then ...
        statusSub.assertNoErrors();
        statusSub.assertComplete();

        List<RobotStatusApi> msgs = statusSub.values();
        assertThat(msgs.stream()
                .filter(RobotStatusApi::configured)
                .count(), equalTo(0L));
        assertThat(msgs.stream()
                .filter(RobotStatusApi::configuring)
                .count(), greaterThanOrEqualTo(1L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        assertThat(cameras, empty());
    }

    @Test
    void testMotionMessage() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        long t0 = robot.robotTime();
        mockClient.sendRobot("mt", "0,0,0,-45,0,0,0,0,0,0,0,0,0,0");
        Completable.timer(MESSAGE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        robot.close();
        robot.readRobotStatus().blockingSubscribe();

        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertNoErrors();

        assertThat(motions, hasSize(1));

        List<WheellyMotionMessage> msgs = motions;
        assertThat(msgs.getFirst().time(), greaterThanOrEqualTo(t0));
        assertThat(msgs.getFirst().time(), lessThanOrEqualTo(t0 + 100L));
        assertEquals(-45, msgs.getFirst().directionDeg());

        assertThat(cameras, empty());
    }

    @Test
    void testScan() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.scan(-45);
        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();
        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        // Then
        TestSubscriber<Tuple2<String, MqttMessage>> mockSub = mockClient.subscriber();
        mockSub.assertNoErrors();
        mockSub.assertComplete();

        assertThat(mockSub.values().stream()
                .filter(t ->
                        t._1.endsWith("/sc")
                                && Arrays.equals(t._2.getPayload(), "-45".getBytes()))
                .count(), greaterThanOrEqualTo(1L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        assertThat(cameras, empty());
    }

    @Test
    void testSupplyMessage() throws IOException {
        // Given ...
        List<WheellySupplyMessage> supplies = new ArrayList<>();
        robot.onSupply(supplies::add);

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        mockClient.sendRobot("sv", "0,1");
        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();

        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();
        mockClient.close();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        assertThat(supplies, hasSize(1));

        assertThat(supplies.getFirst().time(), greaterThanOrEqualTo(10L));
        assertEquals(1, supplies.getFirst().supplySensor());

        assertThat(cameras, empty());
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

        assertFalse(msgs.getLast().connected());
        assertFalse(msgs.getLast().connecting());
    }

}