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
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

class RxMqttClientTest {
    public static final String MQTT_BROKER = "tcp://192.168.1.253:1883";
    public static final String MQTT_TOPIC = "test/#";
    public static final String MQTT_TOPIC_A = "test/A";
    public static final String MQTT_TOPIC_B = "test/B";
    public static final String MQTT_USER = "wheellyj";
    public static final String MQTT_PASSWORD = "wheellyj";
    public static final String MQTT_MESSAGE = "Hi from the Java application";
    public static final String MQTT_CLIENT = "testMqtt";
    private static final Logger logger = LoggerFactory.getLogger(RxMqttClientTest.class);

    //@Test
    void testCompletion() throws MqttException {
        RxMqttClient client = RxMqttClient.create(MQTT_BROKER, MQTT_CLIENT, MQTT_USER, MQTT_PASSWORD);

        logger.atInfo().log("Subscribing ...");
        TestSubscriber<Tuple2<String, MqttMessage>> sub = new TestSubscriber<>();
        client.connected().flatMapPublisher(
                connected ->
                        connected
                                ? client.subscribe(MQTT_TOPIC, 0)
                                : Flowable.empty()
        ).subscribe(sub);

        logger.atInfo().log("Connecting ...");
        client.connect()
                .doOnSuccess(connected ->
                        logger.atInfo().log("Connected {}", connected))
                .blockingGet();
        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();

        logger.atInfo().log("Publishing ...");
        client.publish(MQTT_TOPIC_A, new MqttMessage(MQTT_MESSAGE.getBytes()))
                .doOnComplete(() ->
                        logger.atInfo().log("Published"))
                .blockingAwait();

        logger.atInfo().log("Publishing ...");
        client.publish(MQTT_TOPIC_B, new MqttMessage(MQTT_MESSAGE.getBytes()))
                .doOnComplete(() ->
                        logger.atInfo().log("Published"))
                .blockingAwait();

        logger.atInfo().log("Publishing ...");
        client.publish(MQTT_TOPIC_B, new MqttMessage(MQTT_MESSAGE.getBytes()))
                .doOnComplete(() ->
                        logger.atInfo().log("Published"))
                .blockingAwait();

        logger.atInfo().log("Closing ...");
        client.close();
        client.closed().blockingAwait();
        Completable.timer(500, TimeUnit.MILLISECONDS).blockingAwait();
        logger.atInfo().log("Closed");

        sub.assertComplete();
        sub.assertNoErrors();
        sub.assertValueCount(3);
    }

    @Test
    void testNotConnected() throws MqttException {
        RxMqttClient client = RxMqttClient.create(MQTT_BROKER, MQTT_CLIENT, MQTT_USER, MQTT_PASSWORD);

        logger.atInfo().log("Subscribing ...");
        TestSubscriber<Tuple2<String, MqttMessage>> sub = new TestSubscriber<>();
        client.connected().flatMapPublisher(
                connected ->
                        connected
                                ? client.subscribe(MQTT_TOPIC, 0)
                                : Flowable.empty()
        ).subscribe(sub);

        logger.atInfo().log("Closing ...");
        client.close();
        client.closed().blockingAwait();
        logger.atInfo().log("Closed");
        Completable.timer(10, TimeUnit.MILLISECONDS).blockingAwait();

        sub.assertComplete();
        sub.assertNoErrors();
        sub.assertNoValues();
    }
}