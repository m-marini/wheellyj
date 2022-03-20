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
import org.mmarini.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.empty;
import static io.reactivex.rxjava3.core.Flowable.just;

/**
 * The ReliableSocket manages the connectivity to a socket by handling the io errors reconnecting to the host
 */
public class ReliableSocket implements AsyncSocket {
    private final static Logger logger = LoggerFactory.getLogger(ReliableSocket.class);

    /**
     * Returns a reliable socket
     *
     * @param host          the host
     * @param port          the port
     * @param retryInterval the retry interval
     */
    public static ReliableSocket create(String host, int port, long retryInterval) {
        return new ReliableSocket(host, port, retryInterval);
    }

    private final String host;
    private final int port;
    private final long retryInterval;
    private final BehaviorProcessor<Optional<AsyncSocketImpl>> sockets;
    private final PublishProcessor<Timed<String>> readLines;
    private final PublishProcessor<String> writeLines;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<AsyncSocketImpl> badSockets;
    private final CompletableSubject closed;

    /**
     * Creates a reliable socket
     *
     * @param host          the host
     * @param port          the port
     * @param retryInterval the retry interval
     */
    protected ReliableSocket(String host, int port, long retryInterval) {
        this.host = host;
        this.port = port;
        this.retryInterval = retryInterval;
        this.sockets = BehaviorProcessor.createDefault(Optional.empty());
        this.readLines = PublishProcessor.create();
        this.writeLines = PublishProcessor.create();
        this.errors = PublishProcessor.create();
        this.badSockets = PublishProcessor.create();
        this.closed = CompletableSubject.create();

        // Attaches for reading each generated sockets and copies the lines from sockets to publisher
        sockets.concatMap(Utils::optionalToFlow)
                .concatMap(socket -> {
                    logger.debug("Connected for read");
                    return socket.readLines()
                            .doOnNext(line -> logger.debug("Line read from socket {}", line))
                            .onErrorResumeNext(ex -> {
                                errors.onNext(ex);
                                badSockets.onNext(socket);
                                return empty();
                            });
                })
                .doOnNext(line -> logger.debug("Line write to publisher {}", line))
                .subscribe(readLines);

        // Attaches for writing each generated sockets and copies the writing lines from internal publisher to sockets
        sockets.concatMap(Utils::optionalToFlow)
                .concatMap(socket -> {
                    logger.debug("Connected for write");
                    return socket.println(writeLines)
                            .toFlowable()
                            .onErrorResumeNext(ex -> {
                                errors.onNext(ex);
                                badSockets.onNext(socket);
                                return empty();
                            });
                })
                .subscribe();

        // Close bad socket and regenerate the socket after the retry interval
        badSockets.distinctUntilChanged()
                .doOnNext(socket -> {
                    socket.close();
                    generateNewSocket();
                })
                .onErrorResumeNext(ex -> {
                    logger.error("Error managing bad sockets", ex);
                    return empty();
                })
                .subscribe();


        if (logger.isDebugEnabled()) {
            sockets.subscribe(x -> logger.debug("Debug: sockets {}", x));
            readLines.subscribe(x -> logger.debug("Debug: readLines {}", x));
            writeLines.subscribe(x -> logger.debug("Debug: writeLines {}", x));
            errors.subscribe(x -> logger.debug("Debug: errors {}", x.getMessage()));
            badSockets.subscribe(x -> logger.debug("Debug: badSockets {}", x));
            closed.subscribe(() -> logger.debug("Debug: closed"));
        }
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
        return generateNewSocket();
    }

    /**
     * Generates a new socket
     */
    private ReliableSocket generateNewSocket() {
        AsyncSocketImpl socket = AsyncSocketImpl.create(host, port);
        logger.debug("Connecting");
        socket.connect();
        socket.readConnection()
                .filter(connected -> connected)
                .firstElement()
                .ignoreElement()
                .doOnComplete(() -> {
                    logger.debug("Connected");
                    sockets.onNext(Optional.of(socket));
                })
                .onErrorResumeNext(ex -> {
                    logger.error("Error creating socket", ex);
                    errors.onNext(ex);
                    retryConnection();
                    return Completable.complete();
                })
                .subscribe();
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
        dataFlow.doOnNext(writeLines::onNext)
                .doOnComplete(result::onComplete)
                .onErrorResumeNext(ex -> {
                    logger.error("Error writing data", ex);
                    // In case of errors signals the errors flow, error complete the result and resume with empty flow
                    errors.onNext(ex);
                    result.onError(ex);
                    return empty();
                })
                .subscribe();
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
                .doOnComplete(this::generateNewSocket)
                .subscribe();
        return this;
    }
}
