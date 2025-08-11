/*
 * Copyright (c) 2022-2025 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

import static org.mmarini.wheelly.mqtt.MqttRobotTest.COMMAND_TOPIC;
import static org.mmarini.wheelly.mqtt.MqttRobotTest.SENSOR_TOPIC;

public class MockMqttClient implements Closeable {
    public static final String MQTT_BROKER = "tcp://localhost:1883";
    public static final String MQTT_USER = "JavaClient";
    public static final String MQTT_PASSWORD = "JavaPass";
    public static final String CLIENT_ID = "mockRobot";
    private static final Logger logger = LoggerFactory.getLogger(MockMqttClient.class);
    private final TestSubscriber<String> messagesSub;
    private final RxMqttClient client;

    public MockMqttClient() throws MqttException {
        this.client = RxMqttClient.create(MQTT_BROKER, CLIENT_ID, MQTT_USER, MQTT_PASSWORD);
        messagesSub = new TestSubscriber<>();
        this.client.readMessages()
                .map(t -> new String(t._2.getPayload()))
                .subscribe(messagesSub);
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        try {
            if (client.isConnected()) {
                client.unsubscribe(COMMAND_TOPIC).blockingGet();
                client.disconnect().blockingGet();
            }
            client.close();
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error closing mqtt");
        }
    }

    public Flowable<String> readMessages() {
        return client.readMessages().map(t -> new String(t._2.getPayload()));
    }

    public void send(String s) throws MqttException {
        logger.atDebug().log("Sending {} ...", s);
        if (client.isConnected()) {
            client.publish(SENSOR_TOPIC, new MqttMessage(s.getBytes())).blockingGet();
        }
    }

    public MockMqttClient start() throws MqttException {
        logger.atInfo().log("Connecting...");
        client.connect().blockingGet();
        logger.atInfo().log("Subscribing...");
        client.subscribe(COMMAND_TOPIC, 0).subscribe();
        logger.atInfo().log("Subscribed");
        return this;
    }

    public TestSubscriber<String> subscriber() {
        return messagesSub;
    }
}
