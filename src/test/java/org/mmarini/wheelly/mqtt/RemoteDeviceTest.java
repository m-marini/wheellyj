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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoteDeviceTest {
    public static final String BROKER_URL = "tcp://localhost:1883";
    public static final String DEVICE_ID = "test";
    public static final String DATA_TYPE = "data";
    public static final String PAYLOAD = "payload";
    public static final String COMMAND = "exec";
    public static final int CLOSE_DELAY = 1;
    private static final Logger logger = LoggerFactory.getLogger(RemoteDeviceTest.class);
    RxMqttClient client;
    RemoteDevice device;

    @BeforeEach
    void setUp() throws MqttException {
        client = RxMqttClient.create(BROKER_URL, null, "wheelly", "wheelly");
        device = new RemoteDevice(DEVICE_ID, client);
    }

    @Test
    void testExecute() throws MqttException {
        // Given a client
        // And connect it
        client.connect().blockingGet();
        // And command topic result observer subscribed to command topic
        TestSubscriber<Tuple2<String, MqttMessage>> busSub = TestSubscriber.create();
        String commandTopic = "cmd/" + DEVICE_ID + "/" + COMMAND;
        client.subscribe(commandTopic, 1)
                .subscribe(busSub);
        // And a string command
        DeviceCommand<String> command = StringCommand.create(COMMAND, PAYLOAD);

        // When execute the command
        TestObserver<String> result = device.execute(command, 1000).test();
        // And observing the result
        String responseTopic = commandTopic + "/res";
        // And publishing the response
        client.publish(responseTopic,
                        new MqttMessage(PAYLOAD.getBytes()))
                .blockingAwait();
        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();
        client.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        // Than ...
        result.assertComplete();
        result.assertNoErrors();
        result.assertValue(PAYLOAD);

        busSub.assertNoErrors();
        busSub.assertComplete();
        busSub.assertValueCount(1);

        List<Tuple2<String, MqttMessage>> values = busSub.values();
        assertEquals(commandTopic, values.getFirst()._1);
        assertArrayEquals(PAYLOAD.getBytes(), values.getFirst()._2.getPayload());
    }

    @Test
    void testExecuteError() throws MqttException {
        client.connect().blockingGet();

        TestSubscriber<Tuple2<String, MqttMessage>> busSub = TestSubscriber.create();
        String commandTopic = "cmd/" + DEVICE_ID + "/" + COMMAND;
        client.subscribe(commandTopic, 1)
                .subscribe(busSub);

        DeviceCommand<String> command = new DeviceCommand<>() {
            @Override
            public String id() {
                return COMMAND;
            }

            @Override
            public String response(MqttMessage message) {
                return null;
            }

            @Override
            public MqttMessage toMessage() {
                return new MqttMessage(PAYLOAD.getBytes());
            }
        };

        TestObserver<String> result = device.execute(command, 1000).test();

        String responseTopic = commandTopic + "/err";
        client.publish(responseTopic,
                        new MqttMessage(PAYLOAD.getBytes()))
                .blockingAwait();

        client.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        result.assertError(DeviceException.class);
        result.assertError(err -> err.getMessage().equals(PAYLOAD));

        busSub.assertNoErrors();
        busSub.assertComplete();
        busSub.assertValueCount(1);

        List<Tuple2<String, MqttMessage>> values = busSub.values();
        assertEquals(commandTopic, values.getFirst()._1);
        assertArrayEquals(PAYLOAD.getBytes(), values.getFirst()._2.getPayload());
    }

    @Test
    void testExecuteNull() throws MqttException {
        client.connect().blockingGet();

        TestSubscriber<Tuple2<String, MqttMessage>> busSub = TestSubscriber.create();
        String commandTopic = "cmd/" + DEVICE_ID + "/" + COMMAND;
        client.subscribe(commandTopic, 1)
                .subscribe(busSub);

        DeviceCommand<String> command = new DeviceCommand<>() {
            @Override
            public String id() {
                return COMMAND;
            }

            @Override
            public String response(MqttMessage message) {
                return null;
            }

            @Override
            public MqttMessage toMessage() {
                return new MqttMessage(PAYLOAD.getBytes());
            }
        };

        TestObserver<String> result = device.execute(command, 1000).test();

        String responseTopic = commandTopic + "/res";
        client.publish(responseTopic, new MqttMessage(PAYLOAD.getBytes()))
                .blockingAwait();

        client.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        result.assertComplete();
        result.assertNoErrors();
        result.assertNoValues();

        busSub.assertNoErrors();
        busSub.assertComplete();
        busSub.assertValueCount(1);

        List<Tuple2<String, MqttMessage>> values = busSub.values();
        assertEquals(commandTopic, values.getFirst()._1);
        assertArrayEquals(PAYLOAD.getBytes(), values.getFirst()._2.getPayload());
    }

    @Test
    void testExecuteTimeout() throws MqttException {
        client.connect().blockingGet();

        TestSubscriber<Tuple2<String, MqttMessage>> busSub = TestSubscriber.create();
        String commandTopic = "cmd/" + DEVICE_ID + "/" + COMMAND;
        client.subscribe(commandTopic, 1)
                .subscribe(busSub);

        DeviceCommand<String> command = StringCommand.create(COMMAND, PAYLOAD);
        TestObserver<String> result = device.execute(command, 500).test();

        Completable.timer(1000, TimeUnit.MILLISECONDS).blockingAwait();
        String responseTopic = commandTopic + "/res";
        client.publish(responseTopic, new MqttMessage(PAYLOAD.getBytes()))
                .blockingAwait();

        client.close();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        result.assertError(TimeoutException.class);

        busSub.assertNoErrors();
        busSub.assertComplete();
        busSub.assertValueCount(1);

        List<Tuple2<String, MqttMessage>> values = busSub.values();
        assertEquals(commandTopic, values.getFirst()._1);
        assertArrayEquals(PAYLOAD.getBytes(), values.getFirst()._2.getPayload());
    }

    @Test
    void testReadData() throws MqttException {
        // Given a data flow subscriber
        TestSubscriber<String> sub = TestSubscriber.create();
        Flowable<String> data = device.readData("data", msg -> new String(msg.getPayload()));
        data.subscribe(sub);

        // When connecting client
        client.connect().blockingGet();
        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();
        // and publishing data
        client.publish("sens/" + DEVICE_ID + "/" + DATA_TYPE,
                new MqttMessage(PAYLOAD.getBytes()));
        // and waiting for a received message
        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();

        client.close();
        client.closed().blockingAwait();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        sub.assertNoErrors();
        sub.assertComplete();
        sub.assertValueCount(1);
        sub.assertValueAt(0, t -> t.equals(PAYLOAD));
    }

    @Test
    void testReadDataNull() throws MqttException {
        client.connect().blockingGet();

        TestSubscriber<String> sub = TestSubscriber.create();
        Flowable<String> data = device.readData("data", msg -> null);
        data.doOnComplete(() -> logger.atInfo().log("read data completed"))
                .subscribe(sub);

        client.publish("sens/" + DEVICE_ID + "/" + DATA_TYPE,
                        new MqttMessage(PAYLOAD.getBytes()))
                .blockingAwait();
        Completable.timer(100, TimeUnit.MILLISECONDS).blockingAwait();

        client.close();
        client.closed().blockingAwait();
        Completable.timer(CLOSE_DELAY, TimeUnit.MILLISECONDS).blockingAwait();

        sub.assertNoErrors();
        sub.assertComplete();
        sub.assertNoValues();
    }
}