/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.model;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.just;

/**
 * The ReliableSocket manages the connectivity to a socket by handling the io errors reconnecting to the host
 */
public class ReliableSocket implements AsyncSocket {
    private final static Logger logger = LoggerFactory.getLogger(ReliableSocket.class);

    /**
     * Returns a reliable socket
     *
     * @param host              the host
     * @param port              the port
     * @param connectionTimeout the connection timeout
     * @param retryInterval     the retry interval
     * @param readTimeout       the read timeout
     */
    public static ReliableSocket create(String host, int port, long connectionTimeout, long retryInterval, long readTimeout) {
        return new ReliableSocket(host, port, connectionTimeout, retryInterval, readTimeout);
    }

    private final String host;
    private final int port;
    private final long retryInterval;
    private final long connectionTimeout;
    private final long readTimeout;
    private final BehaviorProcessor<Optional<AsyncSocketImpl>> sockets;
    private final PublishProcessor<Timed<String>> readLines;
    private final PublishProcessor<String> writeLines;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<AsyncSocketImpl> badSockets;
    private final CompletableSubject closed;

    /**
     * Creates a reliable socket
     *
     * @param host              the host
     * @param port              the port
     * @param connectionTimeout the connection timeout
     * @param retryInterval     the retry interval
     * @param readTimeout       the read timeout
     */
    protected ReliableSocket(String host, int port, long connectionTimeout, long retryInterval, long readTimeout) {
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.retryInterval = retryInterval;
        this.readTimeout = readTimeout;
        this.sockets = BehaviorProcessor.createDefault(Optional.empty());
        this.readLines = PublishProcessor.create();
        this.writeLines = PublishProcessor.create();
        this.errors = PublishProcessor.create();
        this.badSockets = PublishProcessor.create();
        this.closed = CompletableSubject.create();
        // Close bad socket and regenerate the socket after the retry interval
        badSockets.distinctUntilChanged()
                .subscribe(socket -> retryConnection());
    }

    @Override
    public ReliableSocket close() {
        sockets.lastElement()
                .concatMap(opt ->
                        opt.map(chan -> chan.close().closed().toMaybe())
                                .orElse(Maybe.empty())
                ).doOnError(errors::onNext)
                .ignoreElement()
                .subscribe(closed);
        sockets.onNext(Optional.empty());
        sockets.onComplete();
        readLines.onComplete();
        writeLines.onComplete();
        badSockets.onComplete();
        errors.onComplete();
        return this;
    }

    @Override
    public Completable closed() {
        return closed;
    }

    @Override
    public ReliableSocket connect() {
        logger.info("Connecting ...");
        return generateNewSocket();
    }

    /**
     * Generates a new socket
     */
    private ReliableSocket generateNewSocket() {
        AsyncSocketImpl socket = AsyncSocketImpl.create(host, port, connectionTimeout, readTimeout);
        socket.connect();
        socket.connected()
                .subscribe(() -> {
                            logger.debug("Socket connected");
                            // Attaches for writing each generated sockets and copies the writing lines from internal publisher to sockets
                            socket.readErrors()
                                    .subscribe(ex -> {
                                        errors.onNext(ex);
                                        sockets.onNext(Optional.empty());
                                        badSockets.onNext(socket);
                                    });
                            socket.println(writeLines);
                            socket.readLines().subscribe(
                                    readLines::onNext,
                                    ex -> {
                                    });
                            sockets.onNext(Optional.of(socket));
                        },
                        ex -> {
                            logger.error("Error creating socket", ex);
                            errors.onNext(ex);
                            retryConnection();
                        });
        return this;
    }

    /**
     * Returns the connection status
     */
    public Single<Boolean> isConnected() {
        return just(true).withLatestFrom(sockets, (a, b) -> b.isPresent()).lastOrError();
    }

    @Override
    public Completable println(Flowable<String> dataFlow) {
        CompletableSubject result = CompletableSubject.create();
        // Copies the data to internal publisher
        dataFlow.subscribe(
                writeLines::onNext,
                ex -> {
                    logger.error("Error writing data", ex);
                    // In case of errors signals the errors flow, error complete the result and resume with empty flow
                    errors.onNext(ex);
                    result.onError(ex);
                },
                result::onComplete
        );
        return result;
    }

    @Override
    public Flowable<Boolean> readConnection() {
        return sockets.map(Optional::isPresent);
    }

    /**
     * Returns the errors flow
     */
    public Flowable<Throwable> readErrors() {
        return errors;
    }

    @Override
    public Flowable<Timed<String>> readLines() {
        return readLines;
    }

    /**
     * Retries to create socket after retry interval
     */
    private ReliableSocket retryConnection() {
        Completable.timer(retryInterval, TimeUnit.MILLISECONDS)
                .subscribe(this::generateNewSocket);
        return this;
    }
}
