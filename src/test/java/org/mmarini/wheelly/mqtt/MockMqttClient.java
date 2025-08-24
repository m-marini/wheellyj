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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

public class MockMqttClient implements Closeable {
    public static final String MQTT_BROKER = "tcp://localhost:1883";
    public static final String MQTT_USER = "wheellyj";
    public static final String MQTT_PASSWORD = "wheellyj";
    private static final Logger logger = LoggerFactory.getLogger(MockMqttClient.class);
    public static final String COMMAND_TOPIC = "cmd/+/+";
    public static final String SENSOR_TOPIC = "sens/+";
    private static final String CAMERA_SENSOR_TOPIC = "sens/wheellycam";
    private static final String QR_SENSOR_TOPIC = "sens/wheellyqr";
    private static final String ROBOT_SENSOR_TOPIC = "sens/wheelly";
    private final TestSubscriber<Tuple2<String, MqttMessage>> messagesSub;
    private final RxMqttClient client;
    private Flowable<Tuple2<String, MqttMessage>> commandFlow;

    public MockMqttClient() throws MqttException {
        this.client = RxMqttClient.create(MQTT_BROKER, null, MQTT_USER, MQTT_PASSWORD);
        messagesSub = new TestSubscriber<>();
    }

    public Completable publish(String s, String message) throws MqttException {
        return publish(s, new MqttMessage(message.getBytes()));
    }

    public Completable publish(String s, MqttMessage message) throws MqttException {
        return client.publish(s, message);
    }

    Flowable<Tuple2<String, MqttMessage>> readCommands() {
        return commandFlow;
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        try {
            client.close();
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Error closing mqtt");
        }
    }

    public void sendCamera(String dataType, String data) {
        logger.atDebug().log("Sending {} {} ...", dataType, data);
        if (client.isConnected()) {
            client.publish(CAMERA_SENSOR_TOPIC + "/" + dataType, new MqttMessage(data.getBytes())).blockingAwait();
        }
    }

    public void sendQr(String dataType, String data) {
        logger.atDebug().log("Sending {} {} ...", dataType, data);
        if (client.isConnected()) {
            client.publish(QR_SENSOR_TOPIC + "/" + dataType, new MqttMessage(data.getBytes())).blockingAwait();
        }
    }

    public void sendRobot(String dataType, String data) {
        logger.atDebug().log("Sending {} {} ...", dataType, data);
        if (client.isConnected()) {
            client.publish(ROBOT_SENSOR_TOPIC + "/" + dataType, new MqttMessage(data.getBytes())).blockingAwait();
        }
    }

    public MockMqttClient start() throws MqttException {
        logger.atInfo().log("Connecting...");
        client.connect().blockingGet();
        logger.atInfo().log("Subscribing...");
        this.commandFlow = client.subscribe(COMMAND_TOPIC, 0);
        client.subscribe(SENSOR_TOPIC, 0)
                .mergeWith(commandFlow)
                .subscribe(messagesSub);
        logger.atInfo().log("Subscribed");
        return this;
    }

    public TestSubscriber<Tuple2<String, MqttMessage>> subscriber() {
        return messagesSub;
    }
}
