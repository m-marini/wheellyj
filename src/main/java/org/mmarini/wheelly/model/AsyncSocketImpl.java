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
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The AsyncSocketImpl handle the asynchronous communication via socket
 * The read text lines is published as string flow via readLines method.
 * The writing text lines is sent println method.
 * A closed completion flow can be used to get the notification of socket closure or any errors related the socket
 */
public class AsyncSocketImpl implements AsyncSocket {
    private static final int READ_BUFFER_SIZE = 1024;
    private static final Logger logger = LoggerFactory.getLogger(AsyncSocketImpl.class);
    private static final Logger dataLogger = LoggerFactory.getLogger(AsyncSocketImpl.class.getCanonicalName() + ".dataLog");
    private static final String CRLF = "\r\n";
    private static final String LF = "\n";
    private static final String CRLF_PATTERN = "\\r?\\n";

    /**
     * Returns a asynchronous socket
     *
     * @param host the host
     * @param port the port
     */
    public static AsyncSocketImpl create(String host, int port) {
        return new AsyncSocketImpl(host, port);
    }

    /**
     * @param host the host
     * @param port the port
     */
    private static Single<SocketChannel> createChannel(String host, int port) {
        requireNonNull(host);
        return Single.create(emitter -> {
            try {
                logger.debug("Opening socket ...");
                InetSocketAddress socketAddress = new InetSocketAddress(host, port);
                SocketChannel channel = SocketChannel.open();
                //channel.setOption(SO_SNDBUF, 30);
                //channel.setOption(SO_RCVBUF, 100);
                //channel.setOption(SO_KEEPALIVE, true);
                logger.debug("Connecting socket ...");
                channel.connect(socketAddress);
                emitter.onSuccess(channel);
            } catch (Throwable ex) {
                logger.error("Error connecting socket", ex);
                emitter.onError(ex);
            }
        });
    }

    /**
     * @param channel the channel
     */
    private static Flowable<Timed<String>> readLines(SocketChannel channel) {
        return Flowable.create(emitter -> {
            try {
                ByteBuffer bfr = ByteBuffer.allocate(READ_BUFFER_SIZE);
                for (; ; ) {
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
                try {
                    channel.close();
                } catch (IOException e) {
                    logger.error("Error closing socket", ex);
                }
                emitter.onError(ex);
            }
        }, BackpressureStrategy.BUFFER);
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

    private final String host;
    private final int port;
    private final SingleSubject<SocketChannel> channel;
    private final PublishProcessor<Timed<String>> readFlow;
    private final PublishProcessor<String> writeFlow;
    private final Scheduler ioScheduler;
    private final CompletableSubject closed;
    private final BehaviorProcessor<Boolean> connected;

    /**
     * Creates an asynchronous socket
     *
     * @param host the host
     * @param port the port
     */
    protected AsyncSocketImpl(String host, int port) {
        this.host = requireNonNull(host);
        this.port = port;
        this.channel = SingleSubject.create();
        this.readFlow = PublishProcessor.create();
        this.writeFlow = PublishProcessor.create();
        this.closed = CompletableSubject.create();
        this.connected = BehaviorProcessor.createDefault(false);
        this.ioScheduler = Schedulers.io();

        // Subscribe for data read
        channel.toFlowable()
                .observeOn(ioScheduler)
                .doOnNext(ch -> logger.debug("Start reading socket {}", ch))
                .concatMap(AsyncSocketImpl::readLines)
                .subscribe(readFlow);

        // Subscribe for data write
        channel.observeOn(ioScheduler)
                .doOnSuccess(channel -> {
                    logger.debug("Start writing socket {}", channel);
                    write(channel, writeFlow)
                            .doOnError(ex -> {
                                channel.close();
                                closed.onError(ex);
                            })
                            .doOnComplete(closed::onComplete)
                            .subscribe();
                }).ignoreElement()
                .onErrorComplete()
                .subscribe();

        // Debugging
        if (logger.isDebugEnabled()) {
            channel.doOnSuccess(ch -> logger.debug("Debug: channel {}", ch)).subscribe();
            readFlow.doOnNext(data -> logger.debug("Debug: readFlow {}", data)).subscribe();
            writeFlow.doOnNext(data -> logger.debug("Debug: writeFLow {}", data)).subscribe();
            closed.doOnComplete(() -> logger.debug("Debug: closed")).subscribe();
        }
    }

    @Override
    public AsyncSocketImpl close() {
        channel.doOnSuccess(SocketChannel::close)
                .ignoreElement()
                .onErrorComplete()
                .subscribe();
        readFlow.onComplete();
        connected.onNext(false);
        connected.onComplete();
        closed.onComplete();

        return this;
    }

    @Override
    public Completable closed() {
        return closed;
    }

    @Override
    public AsyncSocket connect() {
        createChannel(host, port)
                .subscribeOn(ioScheduler)
                .doOnSuccess(x -> connected.onNext(true))
                .doOnError(connected::onError)
                .subscribe(channel);
        return this;
    }

    /**
     * @param lines the lines flow
     */
    public Completable println(Flowable<String> lines) {
        CompletableSubject result = CompletableSubject.create();
        channel.ignoreElement()
                .concatWith(lines
                        .doOnNext(writeFlow::onNext)
                        .ignoreElements())
                .subscribe(result);
        return result;
    }

    @Override
    public Flowable<Boolean> readConnection() {
        return connected;
    }

    @Override
    public Flowable<Timed<String>> readLines() {
        return readFlow;
    }

    /**
     * @param channel the channel
     * @param lines   the lines
     */
    private Completable write(SocketChannel channel, Flowable<String> lines) {
        CompletableSubject result = CompletableSubject.create();
        logger.debug("Writing data to channel...");
        lines.observeOn(ioScheduler)
                .doOnNext(line -> {
                            ByteBuffer buffer = ByteBuffer.wrap((line + LF).getBytes(StandardCharsets.UTF_8));
                            try {
                                while (buffer.remaining() > 0) {
                                    channel.write(buffer);
                                }
                                dataLogger.trace("{} --> {}", channel, line);
                            } catch (Throwable ex) {
                                logger.error("Error writing socket", ex);
                                try {
                                    channel.close();
                                } catch (IOException ex1) {
                                    logger.error("Error closing socket", ex1);
                                }
                                throw ex;
                            }
                        }
                )
                .doOnComplete(() -> {
                    logger.debug("Write completed, closing channel");
                    channel.close();
                    result.onComplete();
                })
                .doOnError(result::onError)
                .subscribe();
        return result;
    }
}
