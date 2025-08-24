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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class MqttRobotTest {
    public static final String BROKER_URL = "tcp://localhost:1883";
    public static final String WRONG_BROKER_URL = "tcp://localhost:1884";
    public static final String USER = "wheellyj";
    public static final String PASSWORD = "wheellyj";
    public static final RobotSpec ROBOT_SPEC = new RobotSpec(3, Complex.fromDeg(15), 0.18, Complex.fromDeg(120));
    public static final int CONFIGURE_TIMEOUT = 1000;
    public static final String CAMERA_EVENT_TEXT = "0,A,200,200,0,0,0,0,0,0,0,0";
    public static final int RETRY_INTERVAL = 500;
    public static final String CLIENT_ID = "testRobot";
    public static final int TIMEOUT = 2000;
    public static final String ROBOT_ID = "wheelly";
    public static final String CAMERA_ID = "wheellycam";
    public static final String QR_ID = "wheellyqr";
    public static final int CLOSE_DELAY = 100;
    private static final Logger logger = LoggerFactory.getLogger(MqttRobotTest.class);
    public static final int MESSAGE_DELAY = 100;
    public static final long CAMERA_INTERVAL = 500L;
    public static final int CAMERA_TIMEOUT = 5000;

    private MqttRobot robot;
    private TestSubscriber<RobotStatusApi> statusSub;
    private TestSubscriber<Throwable> errorSub;
    private TestSubscriber<CameraEvent> cameraSub;
    private TestSubscriber<WheellyProxyMessage> proxySub;
    private TestSubscriber<WheellyContactsMessage> contactsSub;
    private TestSubscriber<WheellyMotionMessage> motionSub;
    private MockMqttClient mockClient;

    void createRobot(String url) throws MqttException {
        robot = assertDoesNotThrow(() -> MqttRobot.create(url, CLIENT_ID, USER, PASSWORD,
                ROBOT_ID, CAMERA_ID, QR_ID,
                CONFIGURE_TIMEOUT, RETRY_INTERVAL, CAMERA_INTERVAL, CAMERA_TIMEOUT, ROBOT_SPEC,
                new String[]{"cs,100,100", "ci,200,200"}, new String[]{"cf,255,5"}));
        assertNotNull(robot);
        statusSub = new TestSubscriber<>();
        errorSub = new TestSubscriber<>();
        cameraSub = new TestSubscriber<>();
        this.proxySub = new TestSubscriber<>();
        this.motionSub = new TestSubscriber<>();
        this.contactsSub = new TestSubscriber<>();
        robot.readRobotStatus().subscribe(statusSub);
        robot.readErrors().subscribe(errorSub);
        robot.readCamera()
                .subscribe(cameraSub);
        robot.readProxy()
                .subscribe(proxySub);
        robot.readContacts()
                .subscribe(contactsSub);
        robot.readMotion()
                .subscribe(motionSub);
        mockClient = assertDoesNotThrow(MockMqttClient::new);
        mockClient.start();
        mockClient.readCommands().subscribe(
                t ->
                        mockClient.publish(t._1 + "/res", t._2)
        );
    }

    @BeforeEach
    void setUp() throws MqttException {
        createRobot(BROKER_URL);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (mockClient != null) {
            mockClient.close();
        }
    }

    @Test
    void testCameraCapture() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        // And wait for 2 CAMERA_INTERVAL
        Completable.timer(2 * CAMERA_INTERVAL, TimeUnit.MILLISECONDS).blockingAwait();
        // And Closing robot
        robot.close();
        robot.readRobotStatus()
                .ignoreElements()
                .blockingAwait();

        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        // Then
        TestSubscriber<Tuple2<String, MqttMessage>> mockSub = mockClient.subscriber();
        mockSub.assertComplete();
        mockSub.assertNoErrors();
        assertThat(mockSub.values().stream()
                .filter(t -> "cmd/wheellycam/ca".equals(t._1))
                .count(), greaterThanOrEqualTo(2L));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
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

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(1);

        List<CameraEvent> msgs = cameraSub.values();
        assertThat(msgs.getFirst().simulationTime(), greaterThan(0L));
        assertEquals("A", msgs.getFirst().qrCode());
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

        cameraSub.assertComplete();
        cameraSub.assertNoErrors();
        cameraSub.assertNoValues();

        motionSub.assertComplete();
        motionSub.assertNoErrors();
        motionSub.assertNoValues();

        proxySub.assertComplete();
        proxySub.assertNoErrors();
        proxySub.assertNoValues();

        contactsSub.assertComplete();
        contactsSub.assertNoErrors();
        contactsSub.assertNoValues();
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
        assertTrue(subscriber.values().stream()
                .anyMatch(t -> "cmd/wheelly/ci".equals(t._1)));
        assertTrue(subscriber.values().stream()
                .anyMatch(t -> "cmd/wheelly/cs".equals(t._1)));
        assertTrue(subscriber.values().stream()
                .anyMatch(t -> "cmd/wheellycam/cf".equals(t._1)));

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertNoValues();

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        errorSub.assertNoValues();
    }

    @Test
    void testContactsMessage() throws IOException {
        // Given ...

        TestSubscriber<WheellyContactsMessage> contactSub = new TestSubscriber<>();
        robot.readContacts().subscribe(contactSub);
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

        contactSub.assertNoErrors();
        contactSub.assertComplete();
        contactSub.assertValueCount(1);

        List<WheellyContactsMessage> msgs = contactSub.values();
        assertThat(msgs.getFirst().simulationTime(), greaterThanOrEqualTo(10L));
        assertTrue(msgs.getFirst().frontSensors());
        assertFalse(msgs.getFirst().rearSensors());
        assertTrue(msgs.getFirst().canMoveForward());
        assertFalse(msgs.getFirst().canMoveBackward());

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
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

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);

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

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
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
        long t0 = robot.simulationTime();
        mockClient.sendRobot("mt", "0,0,0,-45,0,0,0,0,0,0,0,0,0,0");
        Completable.timer(MESSAGE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        robot.close();
        robot.readRobotStatus().blockingSubscribe();

        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertNoErrors();

        motionSub.assertNoErrors();
        motionSub.assertComplete();
        motionSub.assertValueCount(1);

        List<WheellyMotionMessage> msgs = motionSub.values();
        assertThat(msgs.getFirst().simulationTime(), greaterThanOrEqualTo(t0));
        assertThat(msgs.getFirst().simulationTime(), lessThanOrEqualTo(t0 + 100L));
        assertEquals(-45, msgs.getFirst().directionDeg());

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertNoErrors();
    }

    @Test
    void testMove() throws IOException {
        // Given ...

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);
        robot.move(90, 60);
        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();

        mockClient.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        // Then
        TestSubscriber<Tuple2<String, MqttMessage>> mockSub = mockClient.subscriber();
        mockSub.assertComplete();
        mockSub.assertNoErrors();
        assertThat(mockSub.values().stream()
                .filter(t ->
                        t._1.endsWith("/mv")
                                && Arrays.equals(t._2.getPayload(), "90,60".getBytes()))
                .count(), greaterThanOrEqualTo(1L));


        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

    @Test
    void testProxyMessage() throws IOException {
        // Given ...
        TestSubscriber<WheellyProxyMessage> proxySub = new TestSubscriber<>();
        robot.readProxy().subscribe(proxySub);

        // When connect
        robot.connect();
        // And waiting for robotConfigured
        robot.readRobotStatus()
                .filter(RobotStatusApi::configured)
                .firstElement()
                .ignoreElement()
                .blockingAwait(TIMEOUT, TimeUnit.MILLISECONDS);

        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        mockClient.sendRobot("px", "0,90,0,0.0,0.0,0");
        Completable.timer(MESSAGE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        robot.close();
        robot.readRobotStatus().ignoreElements().blockingAwait();
        mockClient.close();

        errorSub.assertNoErrors();
        errorSub.assertComplete();
        errorSub.assertValueCount(0);

        proxySub.assertNoErrors();
        proxySub.assertComplete();
        proxySub.assertValueCount(1);

        List<WheellyProxyMessage> msgs = proxySub.values();
        assertThat(msgs.getFirst().simulationTime(), greaterThanOrEqualTo(10L));
        assertEquals(90, msgs.getFirst().sensorDirectionDeg());

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
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

        cameraSub.assertNoErrors();
        cameraSub.assertComplete();
        cameraSub.assertValueCount(0);
    }

    @Test
    void testSupplyMessage() throws IOException {
        // Given ...
        TestSubscriber<WheellySupplyMessage> supplySub = new TestSubscriber<>();
        robot.readSupply().subscribe(supplySub);

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

        supplySub.assertNoErrors();
        supplySub.assertComplete();
        supplySub.assertValueCount(1);

        List<WheellySupplyMessage> msgs = supplySub.values();
        assertThat(msgs.getFirst().simulationTime(), greaterThanOrEqualTo(10L));
        assertEquals(1, msgs.getFirst().supplySensor());

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

        assertFalse(msgs.getLast().connected());
        assertFalse(msgs.getLast().connecting());
    }

}