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
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.eclipse.paho.client.mqttv3.*;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * Transforms the mqtt client to a reactive client
 */
public class RxMqttClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(RxMqttClient.class);

    /**
     * Returns the reactive mqtt client
     *
     * @param serverUri the broker url
     * @param clientId  the client id
     * @param userName  the user name
     * @param password  the password
     */
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

    /**
     * Returns the mqtt client listener for the given message processor
     *
     * @param processor the message processor
     */
    private static IMqttMessageListener createListener(PublishProcessor<Tuple2<String, MqttMessage>> processor) {
        return (topic, message) -> processor.onNext(Tuple2.of(topic, message));
    }

    private final MqttAsyncClient client;
    private final MqttConnectOptions options;
    private final CompletableSubject closed;
    private final SingleSubject<Boolean> connectedSubject;
    private final Single<Boolean> connected;

    /**
     * Creates the reactive client
     *
     * @param client  the mqtt client
     * @param options the option of client
     */
    public RxMqttClient(MqttAsyncClient client, MqttConnectOptions options) {
        this.client = requireNonNull(client);
        this.options = options;
        this.closed = CompletableSubject.create();
        this.connectedSubject = SingleSubject.create();
        this.connected = connectedSubject.observeOn(Schedulers.computation());
        logger.atDebug().log("Client {} created", client.hashCode());
    }

    @Override
    public void close() throws MqttException {
        try {
            if (!client.isConnected()) {
                connectedSubject.onSuccess(false);
            } else {
                disconnect().blockingAwait();
            }
        } finally {
            try {
                logger.atDebug().log("Client {} closing ...", client.hashCode());
                client.close();
            } finally {
                closed.onComplete();
                logger.atDebug().log("Client {} closed", client.hashCode());
            }
        }
    }

    /**
     * Returns the flow of closure
     */
    public Completable closed() {
        return closed;
    }

    /**
     * Connects the client to the broker
     *
     * @return the completion of connection
     * @throws MqttException in case of error
     */
    public Single<Boolean> connect() throws MqttException {
        logger.atDebug().log("Client {} connecting ...", client.hashCode());
        client.connect(options, this, new IMqttActionListener() {
            @Override
            public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                logger.atError().setCause(throwable).log("Client {} error connecting", client.hashCode());
                connectedSubject.onError(throwable);
            }

            @Override
            public void onSuccess(IMqttToken iMqttToken) {
                logger.atDebug().log("Client {} connected", client.hashCode());
                connectedSubject.onSuccess(true);
            }
        });
        return connected;
    }

    /**
     * Returns the connection flow
     */
    public Single<Boolean> connected() {
        return connected;
    }

    /**
     * Returns the processor for the mqtt event that completes on client close
     */
    PublishProcessor<Tuple2<String, MqttMessage>> createMessageFlow() {
        PublishProcessor<Tuple2<String, MqttMessage>> result = PublishProcessor.create();
        closed.subscribe(result::onComplete);
        return result;
    }

    /**
     * Disconnects the client (do not use in mqtt callback)
     *
     * @return the completion of disconnection
     */
    private Completable disconnect() {
        CompletableSubject result = CompletableSubject.create();
        logger.atDebug().log("Client {} disconnecting ...", client.hashCode());
        try {
            client.disconnect(this, new IMqttActionListener() {
                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                    logger.atError().setCause(throwable).log("Client {} error disconnecting", client.hashCode());
                    result.onError(throwable);
                }

                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    logger.atDebug().log("Client {} disconnected", client.hashCode());
                    result.onComplete();
                }
            });
        } catch (MqttException e) {
            logger.atError().setCause(e).log("Client {} error disconnecting", client.hashCode());
            result.onError(e);
        }
        return result.observeOn(Schedulers.computation());
    }

    /**
     * Returns the client id
     */
    public String getClientId() {
        return client.getClientId();
    }

    /**
     * Returns the server uri
     */
    public String getServerURI() {
        return client.getServerURI();
    }

    /**
     * Returns true if the client is connected
     */
    public boolean isConnected() {
        return client.isConnected();
    }

    /**
     * Publishes a message to a topic
     *
     * @param topic   the topic
     * @param message the message
     * @return the completion of publishing
     */
    public Completable publish(String topic, MqttMessage message) {
        CompletableSubject result = CompletableSubject.create();
        try {
            client.publish(topic, message, this, new IMqttActionListener() {
                @Override
                public void onFailure(IMqttToken iMqttToken, Throwable throwable) {
                    logger.atError().setCause(throwable).log("Client {} error publishing", client.hashCode());
                    result.onError(throwable);
                }

                @Override
                public void onSuccess(IMqttToken iMqttToken) {
                    result.onComplete();
                }
            });
        } catch (MqttException e) {
            result.onError(e);
        }
        return result.observeOn(Schedulers.computation());
    }

    /**
     * Subscribes for a topic pattern with qos
     *
     * @param topic the topic
     * @param qos   the qos
     * @return the flow of topics and messages
     */
    public Flowable<Tuple2<String, MqttMessage>> subscribe(String topic, int qos) {
        logger.atDebug().log("Subscribing {} ...", topic);
        PublishProcessor<Tuple2<String, MqttMessage>> messages = createMessageFlow();
        try {
            client.subscribe(topic, qos, createListener(messages));
        } catch (MqttException ex) {
            logger.atError().setCause(ex).log("Client {} error subscribing", client.hashCode());
            messages.onError(ex);
        }
        return messages.observeOn(Schedulers.computation());
    }
}
