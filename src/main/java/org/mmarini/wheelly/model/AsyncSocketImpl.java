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
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.cqfn.rio.Buffers;
import org.cqfn.rio.channel.ReadableChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 *
 */
public class AsyncSocketImpl implements AsyncSocket {
    private static final Logger logger = LoggerFactory.getLogger(AsyncSocketImpl.class);
    private static final Scheduler scheduler = Schedulers.io();

    private final InetSocketAddress address;
    private final SingleSubject<SocketChannel> channel;
    private final PublishProcessor<Timed<String>> readFlow;

    /**
     * @param address the address
     */
    protected AsyncSocketImpl(InetSocketAddress address) {
        this.address = address;
        this.channel = SingleSubject.create();
        this.readFlow = PublishProcessor.create();
        connect();
        AsyncSocket.toLines(createReadFlow()
                        .map(AsyncSocket::buffer2String))
                .subscribe(this.readFlow);
    }

    @Override
    public Completable close() {
        CompletableSubject result = CompletableSubject.create();
        channel.observeOn(scheduler)
                .subscribe(chan -> {
                            try {
                                readFlow.onComplete();
                                logger.debug("Closing ...");
                                chan.close();
                                logger.debug("Closed.");
                                result.onComplete();
                            } catch (Throwable ex) {
                                logger.error(ex.getMessage(), ex);
                                result.onError(ex);
                                readFlow.onError(ex);
                            }
                        },
                        result::onError);
        return result;
    }

    /**
     *
     */
    private AsyncSocketImpl connect() {
        scheduler.scheduleDirect(() -> {
            try {
                logger.debug("Opening socket ...");
                SocketChannel channel = SocketChannel.open();
                logger.debug("Connecting socket ...");
                channel.connect(address);
                logger.debug("Connected.");
                this.channel.onSuccess(channel);
            } catch (Throwable ex) {
                logger.error(ex.getMessage(), ex);
                channel.onError(ex);
            }
        });
        return this;
    }

    /**
     *
     */
    private Flowable<Timed<ByteBuffer>> createReadFlow() {
        return channel.toFlowable().concatMap(cli ->
                new ReadableChannel(() -> cli)
                        .read(Buffers.Standard.K1)
        ).timestamp();
    }

    @Override
    public Completable print(Flowable<String> dataFlow) {
        return write(
                Flowable.fromPublisher(dataFlow)
                        .map(text ->
                                ByteBuffer.wrap((text).getBytes(StandardCharsets.UTF_8)))
        );
    }

    @Override
    public Flowable<Timed<String>> readLines() {
        return readFlow;
    }

    /**
     * @param dataFlow the data flow
     */
    Completable write(Flowable<ByteBuffer> dataFlow) {
        CompletableSubject result = CompletableSubject.create();
        channel.observeOn(scheduler)
                .flatMapCompletable(chan -> {
                    logger.debug("Got channel for write ...");
                    return dataFlow.doOnNext(data -> {
                                logger.debug("Writing ...");
                                chan.write(data);
                            })
                            .ignoreElements();
                })
                .subscribe(result);
        return result;
    }
}
