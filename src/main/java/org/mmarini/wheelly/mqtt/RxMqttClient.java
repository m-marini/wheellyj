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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.eclipse.paho.client.mqttv3.*;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class RxMqttClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RxMqttClient.class);

    public static RxMqttClient create(String serverUri, String clientId, String userName, String password) throws MqttException {
        requireNonNull(serverUri);
        requireNonNull(userName);
        requireNonNull(password);
        if (clientId == null) {
            clientId = MqttAsyncClient.generateClientId();
        }
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(userName);
        options.setPassword(password.toCharArray());
        MqttAsyncClient client = new MqttAsyncClient(serverUri, clientId);
        return new RxMqttClient(client, options);
    }

    private final MqttAsyncClient client;
    private final PublishProcessor<Tuple2<String, MqttMessage>> messages;
    private final MqttConnectOptions options;

    public RxMqttClient(MqttAsyncClient client, MqttConnectOptions options) {
        this.client = requireNonNull(client);
        this.options = options;
        this.messages = PublishProcessor.create();
    }

    public void close(boolean force) throws MqttException {
        try {
            client.close(force);
        } finally {
            messages.onComplete();
        }
    }

    @Override
    public void close() throws MqttException {
        close(false);
    }

    public Single<IMqttToken> connect() throws MqttException {
        SingleSubject<IMqttToken> result = SingleSubject.create();
        client.connect(options, this, new IMqttActionListener() {
            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                logger.atError().setCause(throwable).log("Error connecting mqtt");
                result.onError(throwable);
            }

            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                logger.atDebug().log("Connected mqtt");
                result.onSuccess(iMqttToken);
            }
        });
        return result;
    }

    public Single<IMqttToken> disconnect() throws MqttException {
        SingleSubject<IMqttToken> result = SingleSubject.create();
        client.disconnect(this, new IMqttActionListener() {
            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                logger.atError().setCause(throwable).log("Error disconnecting mqtt");
                result.onError(throwable);
            }

            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                logger.atDebug().log("Disconnected mqtt");
                result.onSuccess(iMqttToken);
            }
        });
        return result;
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public Single<IMqttToken> publish(String topic, MqttMessage message) throws MqttException {
        SingleSubject<IMqttToken> result = SingleSubject.create();
        client.publish(topic, message, this, new IMqttActionListener() {
            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                logger.atError().setCause(throwable).log("Error publishing mqtt");
                result.onError(throwable);
            }

            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                logger.atDebug().log("Published mqtt");
                result.onSuccess(iMqttToken);
            }
        });
        return result;
    }

    public Flowable<Tuple2<String, MqttMessage>> readMessages() {
        return messages;
    }

    public RxMqttClient reconnect() throws MqttException {
        client.reconnect();
        return this;
    }

    public Flowable<Tuple2<String, MqttMessage>> subscribe(String topics, int qos) throws MqttException {
        client.subscribe(topics, qos, (topic, msg) ->
                messages.onNext(Tuple2.of(topic, msg)));
        return messages;
    }

    public Single<IMqttToken> unsubscribe(String... topics) throws MqttException {
        SingleSubject<IMqttToken> result = SingleSubject.create();
        client.unsubscribe(topics, this, new IMqttActionListener() {
            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                logger.atError().setCause(throwable).log("Error unsubscribing mqtt");
                result.onError(throwable);
            }

            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                logger.atDebug().log("Unsubscribed mqtt");
                result.onSuccess(iMqttToken);
            }
        });
        return result;
    }

}
