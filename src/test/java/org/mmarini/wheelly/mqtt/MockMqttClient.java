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
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.*;
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
    private final PublishProcessor<String> messages;
    private final TestSubscriber<String> messagesSub;
    private final CompletableSubject connected;
    private MqttAsyncClient mqttClient;

    public MockMqttClient() {
        this.messages = PublishProcessor.create();
        this.connected = CompletableSubject.create();
        messagesSub = new TestSubscriber<>();
        messages.subscribe(messagesSub);
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.unsubscribe(COMMAND_TOPIC).waitForCompletion();
                    mqttClient.disconnect().waitForCompletion();
                }
                mqttClient.close();
            } catch (MqttException e) {
                logger.atError().setCause(e).log("Error closing mqtt");
            } finally {
                mqttClient = null;
                messages.onComplete();
            }
        }
    }

    private void handleError(Throwable e) {
        logger.atError().setCause(e).log("Error captured");
        messages.onError(e);
    }

    private void onMessage(String msg) {
        logger.atDebug().log("Message {}", msg);
        messages.onNext(msg);
    }

    public Completable readConnected() {
        return connected;
    }

    public Flowable<String> readMessages() {
        return messages;
    }

    public void send(String s) {
        logger.atDebug().log("Sending {} ...", s);
        if (mqttClient.isConnected()) {
            try {
                mqttClient.publish(SENSOR_TOPIC, new MqttMessage(s.getBytes()));
            } catch (MqttException e) {
                handleError(e);
            }
        }
    }

    public MockMqttClient start() {
        try {
            mqttClient = new MqttAsyncClient(MQTT_BROKER, CLIENT_ID);
            MqttConnectOptions mqttOptions = new MqttConnectOptions();
            mqttOptions.setUserName(MQTT_USER);
            mqttOptions.setPassword(MQTT_PASSWORD.toCharArray());
            logger.atInfo().log("Connecting...");
            IMqttToken token = mqttClient.connect(mqttOptions);
            token.waitForCompletion();
            if (token.isComplete()) {
                logger.atInfo().log("Connection success {}", token.isComplete());
                connected.onComplete();
                try {
                    mqttClient.subscribe(COMMAND_TOPIC, 0, (topic, msg) ->
                            onMessage(new String(msg.getPayload())));
                } catch (MqttException e) {
                    handleError(e);
                }
            } else {
                Throwable throwable = token.getException();
                handleError(throwable);
                connected.onError(throwable);
            }
        } catch (Throwable e) {
            handleError(e);
        }
        return this;
    }

    public TestSubscriber<String> subscriber() {
        return messagesSub;
    }
}
