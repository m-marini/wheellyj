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
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class MqttTest {
    public static final String MQTT_BROKER = "tcp://192.168.1.145:1883";
    public static final String MQTT_TOPIC = "/test/topic";
    public static final String MQTT_USER = "JavaClient";
    public static final String MQTT_PASSWORD = "JavaPass";
    public static final String MQTT_MESSAGE = "Hi from the Java application";
    public static final String MQTT_CLIENT = "mqttClient";
    private static final Logger logger = LoggerFactory.getLogger(MqttTest.class);

    @Test
    void testAsyncMqtt() {
        PublishProcessor<Tuple2<String, MqttMessage>> messages = PublishProcessor.create();
        try {
            MqttAsyncClient mqttClient = new MqttAsyncClient(MQTT_BROKER, MQTT_CLIENT);
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable throwable) {
                    logger.atInfo().log("Connection lost");
                    logger.atError().setCause(throwable).log("Connection error");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                    logger.atInfo().log("Delivery completed");
                }

                @Override
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                    logger.atInfo().log("Message arrived {}", new String(mqttMessage.getPayload()));
                }
            });
            MqttConnectOptions mqttOptions = new MqttConnectOptions();
            mqttOptions.setUserName(MQTT_USER);
            mqttOptions.setPassword(MQTT_PASSWORD.toCharArray());
            logger.atInfo().log("Connecting...");
            mqttClient.connect(mqttOptions, new IMqttActionListener() {
                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                    logger.atInfo().log("Connection error {}", iMqttToken.isComplete());
                    logger.atError().setCause(throwable).log("Connection error");
                }

                @Override
                public void onSuccess(IMqttToken token) {
                    logger.atInfo().log("Connection success {}", token.isComplete());
                    try {
                        mqttClient.subscribe(MQTT_TOPIC, 0, (topic, msg) ->
                                logger.atInfo().log("Message {}: {}", topic, new String(msg.getPayload()))
                        );
                        logger.atInfo().log("Publishing message ...");
                        MqttMessage mqttMessage = new MqttMessage(MQTT_MESSAGE.getBytes());
                        mqttClient.publish(MQTT_TOPIC, mqttMessage, null, new IMqttActionListener() {

                            @Override
                            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                                logger.atError().setCause(throwable).log("Publish error");
                            }

                            @Override
                            public void onSuccess(IMqttToken iMqttToken) {
                                logger.atInfo().log("Publish success {}", iMqttToken.isComplete());
                            }
                        });
                    } catch (MqttException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            /* Keep the application open, so that the subscribe operation can tested */
            /* Proceed with disconnecting */
            Completable.timer(1000, TimeUnit.MILLISECONDS)
                    .blockingAwait();
            logger.atInfo().log("Closing ...");
            mqttClient.disconnect(null, new IMqttActionListener() {
                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                    logger.atError().setCause(throwable).log("disconnect error");
                }

                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    logger.atInfo().log("Closed");
                }
            });
            Completable.timer(1000, TimeUnit.MILLISECONDS)
                    .blockingAwait();
            mqttClient.close();
        } catch (MqttException ex) {
            logger.atError().setCause(ex).log("MQTT error");
        }
    }
}
