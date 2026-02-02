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
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.flowables.ConnectableFlowable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * Handles the remote device allowing to receive the sensor data and to send commands
 */
public class RemoteDevice {
    private static final Logger logger = LoggerFactory.getLogger(RemoteDevice.class);
    private final String id;
    private final RxMqttClient client;
    private final Map<String, Flowable<Tuple2<String, MqttMessage>>> commandReplies;

    /**
     * Creates the remote device
     *
     * @param id      the device id
     * @param client  the mqtt client
     */
    public RemoteDevice(String id, RxMqttClient client) {
        this.id = requireNonNull(id);
        this.client = requireNonNull(client);
        this.commandReplies = new HashMap<>();
    }

    /**
     * Returns the mqtt client
     */
    public RxMqttClient client() {
        return client;
    }

    /**
     * Returns the completion of close
     */
    public Completable closed() {
        return client.closed();
    }

    /**
     * Returns the command topic path
     */
    public String commandTopicPath() {
        return "cmd/" + id();
    }

    /**
     * Returns the sensor topic path
     */
    public String id() {
        return id;
    }

    /**
     * Executes the command
     *
     * @param command the command to execute
     * @param timeout the maximum wait time (ms)
     * @param <R>     the command response type
     * @return the command response
     */
    public <R> Maybe<R> execute(DeviceCommand<R> command, long timeout) {
        String commandTopic = commandTopicPath() + "/" + command.id();
        String errorTopic = commandTopic + "/err";
        ConnectableFlowable<Tuple2<String, MqttMessage>> replyConnectable = getCommandReply(commandTopic)
                .publish();
        Maybe<R> reply = replyConnectable.firstElement()
                .timeout(timeout, TimeUnit.MILLISECONDS)
                .flatMap(t -> {
                    if (t._1.equals(errorTopic)) {
                        return Maybe.error(new DeviceException(new String(t._2.getPayload())));
                    }
                    R response = command.response(t._2);
                    return response != null
                            ? Maybe.just(response)
                            : Maybe.empty();
                });
        Completable publish = client.publish(commandTopic, command.toMessage());
        replyConnectable.connect();
        return publish.andThen(reply);
    }

    /**
     * Returns the command reply flow
     *
     * @param commandTopic the command topic
     */
    private Flowable<Tuple2<String, MqttMessage>> getCommandReply(String commandTopic) {
        Flowable<Tuple2<String, MqttMessage>> result = commandReplies.get(commandTopic);
        if (result == null) {
            String replyTopics = commandTopic + "/+";
            result = client.subscribe(replyTopics, 1);
            commandReplies.put(commandTopic, result);
        }
        return result;
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * Returns the flow of cmd messages
     */
    public Flowable<Tuple2<String, MqttMessage>> readAllTopics() {
        return client.subscribe("+/" + id() + "/#", 0);
    }

    /**
     * Subscribes to read sensor data
     *
     * @param dataType the data type (topic suffix)
     * @param factory  the factory of data type
     * @param <T>      the type of data
     * @return the flow of read data
     */
    public <T> Flowable<T> readData(String dataType, Function<MqttMessage, T> factory) {
        String topic = sensorTopicPath() + "/" + dataType;
        return client.connected()
                .flatMapPublisher(connected -> {
                    logger.atDebug().log("Subscribing for data {}", topic);
                    return connected
                            ? client.subscribe(topic, 0)
                            : Flowable.empty();
                })
                .flatMap(t -> {
                    logger.atDebug().log("Data {}", t._2);
                    T result = factory.apply(t._2);
                    return result == null
                            ? Flowable.empty()
                            : Flowable.just(result);
                })
                .publish()
                .autoConnect()
                .observeOn(Schedulers.computation())
                .doOnSubscribe(x -> logger.atDebug().log("Subscribe flow {}", topic))
                .doOnNext(x -> logger.atDebug().log("Message {}", x));
    }

    /**
     * Returns the sensor topic path
     */
    public String sensorTopicPath() {
        return "sens/" + id();
    }
}