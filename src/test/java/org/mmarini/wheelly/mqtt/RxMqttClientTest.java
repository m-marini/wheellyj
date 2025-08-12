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
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RxMqttClientTest {
    public static final String MQTT_BROKER = "tcp://192.168.1.145:1883";
    public static final String MQTT_TOPIC = "/test/topic";
    public static final String MQTT_USER = "";
    public static final String MQTT_PASSWORD = "";
    public static final String MQTT_MESSAGE = "Hi from the Java application";
    public static final String MQTT_CLIENT = "testMqtt";
    private static final Logger logger = LoggerFactory.getLogger(RxMqttClientTest.class);

    @Test
    void test() {
        assertDoesNotThrow(() -> {
            RxMqttClient client = RxMqttClient.create(MQTT_BROKER, MQTT_CLIENT, MQTT_USER, MQTT_PASSWORD);

            logger.atInfo().log("Connecting ...");
            client.connect()
                    .doOnSuccess(token ->
                            logger.atInfo().log("Connected"))
                    .blockingGet();

            logger.atInfo().log("Subscribing ...");
            client.subscribe(MQTT_TOPIC, 0)
                    .subscribe(t ->
                                    logger.atInfo().log("Received {} {}", t._1, new String(t._2.getPayload())),
                            ex -> {
                            },
                            () -> logger.atInfo().log("Messages completed")
                    );

            logger.atInfo().log("Publishing ...");
            client.publish(MQTT_TOPIC, new MqttMessage(MQTT_MESSAGE.getBytes()))
                    .doOnSuccess(token ->
                            logger.atInfo().log("Published"))
                    .blockingGet();

            logger.atInfo().log("Unsubscribing ...");
            client.unsubscribe(MQTT_TOPIC)
                    .doOnSuccess(token ->
                            logger.atInfo().log("Unsubscribed"))
                    .blockingGet();

            logger.atInfo().log("Publishing ...");
            client.publish(MQTT_TOPIC, new MqttMessage(MQTT_MESSAGE.getBytes()))
                    .doOnSuccess(token ->
                            logger.atInfo().log("Published"))
                    .blockingGet();

            logger.atInfo().log("Disconnecting ...");
            client.disconnect()
                    .doOnSuccess(token ->
                            logger.atInfo().log("Disconnected"))
                    .blockingGet();

            logger.atInfo().log("Reconnecting ...");
            client.reconnect();
            Completable.timer(1000, TimeUnit.MILLISECONDS).blockingAwait();
            assertTrue(client.isConnected());

            logger.atInfo().log("Subscribing ...");
            client.subscribe(MQTT_TOPIC, 0)
                    .subscribe();

            logger.atInfo().log("Publishing ...");
            client.publish(MQTT_TOPIC, new MqttMessage(MQTT_MESSAGE.getBytes()))
                    .doOnSuccess(token ->
                            logger.atInfo().log("Published"))
                    .blockingGet();

            logger.atInfo().log("Disconnecting ...");
            client.disconnect()
                    .doOnSuccess(token ->
                            logger.atInfo().log("Disconnected"))
                    .blockingGet();

            logger.atInfo().log("Closing ...");
            client.close(true);
            logger.atInfo().log("Closed");

            client.readMessages().ignoreElements().blockingAwait();
        });
    }
}