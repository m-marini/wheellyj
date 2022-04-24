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

import io.reactivex.rxjava3.core.*;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.MaybeSubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static java.net.StandardSocketOptions.SO_KEEPALIVE;
import static java.util.Objects.requireNonNull;

/**
 * The AsyncSocketImpl handle the asynchronous communication via socket
 * The read text lines is published on string flow via readLines method.
 * The writing text lines is sent println method.
 * A closed completion flow can be used to get the notification of socket closure or any errors related the socket
 */
public class AsyncSocketImpl implements AsyncSocket {
    private static final String LF = "\n";
    private static final String CRLF_PATTERN = "\\r?\\n";
    private static final int READ_BUFFER_SIZE = 1024;
    private static final Logger dataLogger = LoggerFactory.getLogger(AsyncSocketImpl.class.getCanonicalName() + ".dataLog");
    private static final Logger logger = LoggerFactory.getLogger(AsyncSocketImpl.class);

    /**
     * @param host           the host
     * @param port           the port
     * @param connectTimeout the connection timeout in millis
     * @param readTimeout    the read timeout in millis
     */
    public static AsyncSocketImpl create(String host, int port, long connectTimeout, long readTimeout) {
        return new AsyncSocketImpl(host, port, connectTimeout, readTimeout);
    }

    /**
     * @param bfr the buffer
     */
    private static Stream<Timed<String>> splitLines(ByteBuffer bfr) {
        long timestamp = Instant.now().toEpochMilli();
        String data = new String(bfr.array(), bfr.position(), bfr.remaining(), StandardCharsets.UTF_8);
        String[] fragments = data.split(CRLF_PATTERN, -1);
        String tail = fragments[fragments.length - 1];
        Stream<Timed<String>> result = Arrays.stream(fragments).limit(fragments.length - 1)
                .map(line -> new Timed<>(line, timestamp, TimeUnit.MILLISECONDS));
        // enqueues to the buffer the tail (begin of next lines)
        bfr.clear();
        bfr.put(tail.getBytes(StandardCharsets.UTF_8));
        return result;
    }

    private final SingleSubject<SocketChannel> channel;
    private final String host;
    private final int port;
    private final PublishProcessor<Timed<String>> readFlow;
    private final PublishProcessor<String> writeFlow;
    private final Scheduler ioScheduler;
    private final CompletableSubject closed;
    private final MaybeSubject<Throwable> errors;
    private final CompletableSubject connectRequest;
    private final long connectTimeout;
    private final BehaviorProcessor<Boolean> connected;
    private final long readTimeout;

    /**
     * Creates an asynchronous socket
     *
     * @param host           the host
     * @param port           the port
     * @param connectTimeout the connection timeout in millis
     * @param readTimeout    the read timeout in millis
     */
    protected AsyncSocketImpl(String host, int port, long connectTimeout, long readTimeout) {
        this.host = requireNonNull(host);
        this.port = port;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.channel = SingleSubject.create();
        this.closed = CompletableSubject.create();
        this.connectRequest = CompletableSubject.create();
        this.errors = MaybeSubject.create();
        this.readFlow = PublishProcessor.create();
        this.writeFlow = PublishProcessor.create();
        this.ioScheduler = Schedulers.io();
        this.connected = BehaviorProcessor.createDefault(false);
        this.connectRequest.observeOn(ioScheduler)
                .subscribe(this::createChannel);
        channel.subscribe(this::readBody,
                ex -> {
                });

        channel.observeOn(ioScheduler)
                .subscribe(this::writeBody,
                        ex -> {
                        });

    }

    public AsyncSocketImpl close() {
        channel.observeOn(ioScheduler)
                .subscribe(channel -> {
                            try {
                                writeFlow.onComplete();
                                channel.close();
                                closed.onComplete();
                            } catch (IOException ex) {
                                errors.onSuccess(ex);
                                closed.onError(ex);
                            }
                            this.connected.onNext(false);
                            this.connected.onComplete();
                        },
                        ex -> closed.onComplete());
        return this;
    }

    public Completable closed() {
        return closed;
    }

    public AsyncSocketImpl connect() {
        connectRequest.onComplete();
        return this;
    }

    public Completable connected() {
        return channel.ignoreElement();
    }

    private void createChannel() {
        try {
            logger.info("Creating channel ...");
            InetSocketAddress socketAddress = new InetSocketAddress(host, port);
            SocketChannel channel = SocketChannel.open();
            channel.setOption(SO_KEEPALIVE, true);
            channel.socket().connect(socketAddress, (int) connectTimeout);
            this.channel.onSuccess(channel);
            this.connected.onNext(true);
        } catch (Throwable ex) {
            errors.onSuccess(ex);
            this.channel.onError(ex);
            this.closed.onComplete();
            this.connected.onComplete();
        }
    }

    /**
     * @param lines the lines flow
     */
    public Completable println(Flowable<String> lines) {
        CompletableSubject result = CompletableSubject.create();
        connected().observeOn(ioScheduler)
                .subscribe(() -> lines.subscribe(
                                writeFlow::onNext,
                                result::onError,
                                result::onComplete),
                        result::onError);
        return result;
    }

    /**
     * @param line the line
     */
    public AsyncSocketImpl println(String line) {
        writeFlow.onNext(line);
        return this;
    }

    private void readBody(SocketChannel ch) {
        readLines(ch).subscribeOn(ioScheduler)
                .timeout(this.readTimeout, TimeUnit.MILLISECONDS)
                .subscribe(
                        readFlow::onNext,
                        ex -> {
                            if (ex instanceof TimeoutException) {
                                ch.close();
                            }
                            errors.onSuccess(ex);
                            readFlow.onError(ex);
                            connected.onNext(false);
                            connected.onComplete();
                            closed.onComplete();
                        },
                        readFlow::onComplete);
    }

    @Override
    public Flowable<Boolean> readConnection() {
        return connected;
    }

    public Maybe<Throwable> readErrors() {
        return errors;
    }

    /**
     * @param channel the channel
     */
    private Flowable<Timed<String>> readLines(SocketChannel channel) {
        return Flowable.create(emitter -> {
            try {
                ByteBuffer bfr = ByteBuffer.allocate(READ_BUFFER_SIZE);
                for (; channel.isConnected(); ) {
                    int n = channel.read(bfr);
                    if (n < 0) {
                        // End of file
                        break;
                    } else if (n > 0) {
                        bfr.flip();
                        Stream<Timed<String>> lines = splitLines(bfr);
                        lines.forEach(line -> {
                            dataLogger.trace("{} <-- {}", channel, line.value());
                            emitter.onNext(line);
                        });
                    }
                }
                emitter.onComplete();
            } catch (Throwable ex) {
                if (!emitter.isCancelled()) {
                    emitter.onError(ex);
                }
            }
        }, BackpressureStrategy.ERROR);
    }

    public Flowable<Timed<String>> readLines() {
        return readFlow;
    }

    private void writeBody(SocketChannel ch) {
        writeFlow.observeOn(ioScheduler)
                .subscribe(line -> {
                            if (ch.isConnected()) {
                                ByteBuffer buffer = ByteBuffer.wrap((line + LF).getBytes(StandardCharsets.UTF_8));
                                try {
                                    while (buffer.remaining() > 0) {
                                        ch.write(buffer);
                                    }
                                    dataLogger.trace("{} --> {}", ch, line);
                                } catch (Throwable ex) {
                                    errors.onSuccess(ex);
                                    closed.onComplete();
                                    this.connected.onNext(false);
                                    this.connected.onComplete();
                                }
                            }
                        },
                        ex -> {
                        });
    }
}
