/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.net.StandardSocketOptions.SO_KEEPALIVE;

/**
 * The LineSocket manages the communication channel via socket.
 * <p>
 * Any IO function can throw an IOException on errors or timeout
 * </p>
 * <p>
 * The usage samples is
 * <code><pre>
 *
 *     LineSocket socket = new LineSocket(...);
 *
 *     socket.connect();
 *
 *     socket.writeCommand("...");
 *
 *     Timed<String> line = socket.readLine();
 *     ...
 *
 *     socket.close();
 * </pre>
 * </code>
 * </p>
 */
public class AsyncLineSocket implements Closeable {
    public static final String CONNECTING = "connecting";
    public static final String CONNECTED = "connected";
    private static final String LF = "\r\n";
    private static final String CRLF_PATTERN = "\\r?\\n";
    private static final int READ_BUFFER_SIZE = 1024;
    private static final Logger logger = LoggerFactory.getLogger(AsyncLineSocket.class);
    private final String host;
    private final int port;
    private final long connectionTimeout;
    private final long readTimeout;
    private final ByteBuffer buffer;
    private final PublishProcessor<Timed<String>> lines;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<String> status;
    private final CompletableSubject closeSubj;
    private AsynchronousSocketChannel channel;
    private boolean closed;

    /**
     * Creates a robot socket
     *
     * @param host              the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout in millis
     * @param readTimeout       the read timeout in millis
     */
    public AsyncLineSocket(String host, int port, long connectionTimeout, long readTimeout) {
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.lines = PublishProcessor.create();
        this.errors = PublishProcessor.create();
        this.status = PublishProcessor.create();
        this.closeSubj = CompletableSubject.create();
    }

    @Override
    public void close() {
        closed = true;
        closeSubj.onComplete();
        AsynchronousSocketChannel ch = channel;
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException e) {
                errors.onNext(e);
            }
            lines.onComplete();
            errors.onComplete();
        }
        closeSubj.onComplete();
    }

    /**
     * Connects the socket
     */
    public AsyncLineSocket connect() {
        if (channel == null && !closed) {
            logger.atDebug().log("{}:{} connecting ...", host, port);
            try {
                status.onNext(CONNECTING);
                InetSocketAddress socketAddress = new InetSocketAddress(host, port);
                AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
                ch.setOption(SO_KEEPALIVE, true);
                Completable.fromFuture(ch.connect(socketAddress))
                        .observeOn(Schedulers.io())
                        .doOnComplete(() -> onConnection(ch))
                        .doOnError(ex -> onConnectionError(ch, ex))
                        .subscribe();
            } catch (IOException e) {
                onConnectionError(null, e);
            }
        }
        return this;
    }

    /**
     * Handles connection
     */
    private void onConnection(AsynchronousSocketChannel ch) {
        logger.atDebug().log("{}:{} connected", host, port);
        channel = ch;
        status.onNext(CONNECTED);
        readNext();
    }

    /**
     * Handles connection error
     *
     * @param ch the channel
     * @param e  the error
     */
    private void onConnectionError(AsynchronousSocketChannel ch, Throwable e) {
        logger.atError().setCause(e).log("{}:{} error connecting channel", host, port);
        if (ch != null) {
            try {
                ch.close();
            } catch (Throwable ex) {
                logger.atError().setCause(ex).log("{}:{} error closing channel", host, port);
            }
        }
        channel = null;
        errors.onNext(e);
        waitRetry();
    }

    /**
     * Handles data read
     *
     * @param size the number of read bytes
     */
    private void onRead(int size) {
        logger.atDebug().log("Read {} bytes", size);
        if (size > 0) {
            buffer.flip();
            splitLines();
        }
        readNext();
    }

    /**
     * Handles the data read error
     *
     * @param e the error
     */
    private void onReadError(Throwable e) {
        logger.atError().setCause(e).log("{}:{} error reading channel", host, port);
        try {
            channel.close();
        } catch (IOException ex) {
            logger.atError().setCause(ex).log("{}:{} error closing channel", host, port);
        }
        channel = null;
        errors.onNext(e);
        connect();
    }

    /**
     * Handles the written data error
     *
     * @param e the error
     */
    private void onWriteError(Throwable e) {
        try {
            channel.close();
        } catch (IOException ex) {
            logger.atError().setCause(ex).log("{}:{} error closing channel", host, port);
        }
        channel = null;
        logger.atError().setCause(e).log("{}:{} error reading channel", host, port);
        errors.onNext(e);
        connect();
    }

    /**
     * Returns the closeable of close socket
     */
    public Completable readClose() {
        return closeSubj;
    }

    /**
     * Returns the completable for connection
     */
    public Maybe<String> readConnected() {
        if (channel != null) {
            return Maybe.just(CONNECTED);
        } else {
            return readStatus()
                    .filter(CONNECTED::equals)
                    .firstElement();
        }
    }

    /**
     * Returns the error flow
     */
    public Flowable<Throwable> readError() {
        return errors;
    }

    /**
     * Returns the line flow
     */
    public Flowable<Timed<String>> readLines() {
        return lines;
    }

    /**
     * Reads the next buffer
     */
    private void readNext() {
        channel.read(buffer, readTimeout, TimeUnit.MILLISECONDS, this, callback);
    }

    /**
     * Returns the line flow
     */
    public Flowable<String> readStatus() {
        return status;
    }

    /**
     * Splits the buffer to lines
     */
    private void splitLines() {
        long timestamp = System.currentTimeMillis();
        String data = new String(buffer.array(), buffer.position(), buffer.remaining(), StandardCharsets.UTF_8);
        String[] fragments = data.split(CRLF_PATTERN, -1);
        String tail = fragments[fragments.length - 1];
        buffer.clear();
        buffer.put(tail.getBytes(StandardCharsets.UTF_8));
        Arrays.stream(fragments).limit(fragments.length - 1)
                .map(line -> new Timed<>(line, timestamp, TimeUnit.MILLISECONDS))
                .forEach(lines::onNext);
    }

    /**
     * Waits for the connection retry interval
     */
    private void waitRetry() {
        logger.atDebug().log("Waiting retry");
        Completable.timer(connectionTimeout, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.io())
                .doOnComplete(this::connect)
                .subscribe();
    }

    /**
     * Writes a command to robot
     *
     * @param cmd the command
     */
    public void writeCommand(String cmd) throws IOException {
        AsynchronousSocketChannel ch = channel;
        if (ch != null) {
            ByteBuffer buffer = ByteBuffer.wrap((cmd + LF).getBytes(StandardCharsets.UTF_8));
            while (buffer.remaining() > 0) {
                try {
                    ch.write(buffer).get();
                } catch (RuntimeException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException ex1) {
                        onWriteError(ex1);
                        throw ex1;
                    }
                    throw e;
                } catch (Throwable e) {
                    onWriteError(e);
                }
            }
            logger.atDebug().setMessage("Written command {}").addArgument(cmd).log();
        }
    }

    private static final CompletionHandler<Integer, AsyncLineSocket> callback = new CompletionHandler<>() {
        @Override
        public void completed(Integer size, AsyncLineSocket subject) {
            subject.onRead(size);
        }

        @Override
        public void failed(Throwable ex, AsyncLineSocket subject) {
            subject.onReadError(ex);
        }
    };


}
