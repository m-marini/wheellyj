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
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
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
public class LineSocket implements Closeable {
    private static final String LF = "\r\n";
    private static final String CRLF_PATTERN = "\\r?\\n";
    private static final int READ_BUFFER_SIZE = 1024;
    private static final Logger logger = LoggerFactory.getLogger(LineSocket.class);
    private final String host;
    private final int port;
    private final long connectionTimeout;
    private final long readTimeout;
    private final ByteBuffer buffer;
    private final PublishProcessor<Timed<String>> lines;
    private final PublishProcessor<Throwable> errors;
    private final BehaviorProcessor<LineSocketStatus> states;
    private final CompletableSubject closeSubj;
    private final AtomicReference<LineSocketStatus> status;

    /**
     * Creates a robot socket
     *
     * @param host              the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout in millis
     * @param readTimeout       the read timeout in millis
     */
    public LineSocket(String host, int port, long connectionTimeout, long readTimeout) {
        this.host = host;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.lines = PublishProcessor.create();
        this.errors = PublishProcessor.create();
        this.states = BehaviorProcessor.create();
        this.closeSubj = CompletableSubject.create();
        this.status = new AtomicReference<>(new LineSocketStatus(false, false, false, null));
    }

    @Override
    public void close() {
        LineSocketStatus s0 = status.getAndUpdate(s -> s.closed(true));
        if (!s0.closed()) {
            LineSocketStatus st = status.getAndUpdate(s -> s.channel(null)
                    .connected(false)
                    .connecting(false));
            states.onNext(st);
            states.onComplete();
            lines.onComplete();
            closeSubj.onComplete();
            AsynchronousSocketChannel ch = s0.channel();
            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException e) {
                    errors.onNext(e);
                }
            }
            errors.onComplete();
        }
    }

    /**
     * Connects the socket
     */
    public LineSocket connect() {
        LineSocketStatus s0 = status.get();
        if (!s0.closed() && s0.channel() == null) {
            logger.atDebug().log("{}:{} connecting ...", host, port);
            try {
                InetSocketAddress socketAddress = new InetSocketAddress(host, port);
                AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
                ch.setOption(SO_KEEPALIVE, true);
                LineSocketStatus st = status.updateAndGet(s -> s.connecting(true).connected(false));
                states.onNext(st);
                Completable.fromFuture(ch.connect(socketAddress))
                        .subscribe(() -> onConnection(ch),
                                e -> onConnectionError(ch, e));
            } catch (IOException e) {
                onConnectionError(null, e);
            }
        }
        return this;
    }

    /**
     * Notifies an error
     *
     * @param e the error
     */
    private Throwable notifyError(Throwable e) {
        // traverse for error message
        if (e instanceof InterruptedByTimeoutException && e.getMessage() == null) {
            e = new InterruptedIOException(format("Timeout channel %s:%d", host, port));
        }
        errors.onNext(e);
        return e;
    }

    /**
     * Handles connection
     */
    private void onConnection(AsynchronousSocketChannel ch) {
        logger.atDebug().log("{}:{} connected", host, port);
        LineSocketStatus st = status.updateAndGet(s -> s.connected(true).connecting(false).channel(ch));
        states.onNext(st);
        if (!st.closed()) {
            readNext();
        } else {
            st = status.updateAndGet(s -> s.connected(false).connecting(false).channel(null));
            states.onNext(st);
            try {
                ch.close();
            } catch (IOException e) {
                logger.atError().setCause(e).log("Error closing socket");
            }
        }
    }

    /**
     * Handles connection error
     *
     * @param ch the socket channel
     * @param e  the error
     */
    private void onConnectionError(AsynchronousSocketChannel ch, Throwable e) {
        LineSocketStatus st = status.updateAndGet(s -> s.connected(false).connecting(false));
        states.onNext(st);
        e = notifyError(e);
        logger.atError().setCause(e).log("{}:{} error connecting channel", host, port);
        if (ch != null) {
            try {
                ch.close();
            } catch (Throwable ex) {
                logger.atError().setCause(ex).log("{}:{} error closing channel", host, port);
            }
        }
        if (!st.closed()) {
            waitRetry();
        }
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
        LineSocketStatus s0 = status.getAndUpdate(s -> s.channel(null).connected(false));
        AsynchronousSocketChannel ch = s0.channel();
        states.onNext(status.get());
        e = notifyError(e);
        logger.atError().setCause(e).log("{}:{} error reading channel", host, port);
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ex) {
                logger.atError().setCause(ex).log("{}:{} error closing channel", host, port);
            }
        }
        connect();
    }

    /**
     * Handles the written data error
     *
     * @param e the error
     */
    private void onWriteError(Throwable e) {
        LineSocketStatus s0 = status.getAndUpdate(s -> s.channel(null).connected(false));
        AsynchronousSocketChannel ch = s0.channel();
        states.onNext(status.get());
        e = notifyError(e);
        logger.atError().setCause(e).log("{}:{} error writing channel", host, port);
        try {
            ch.close();
        } catch (IOException ex) {
            logger.atError().setCause(ex).log("{}:{} error closing channel", host, port);
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
        AsynchronousSocketChannel ch = status.get().channel();
        if (ch != null) {
            ch.read(buffer, readTimeout, TimeUnit.MILLISECONDS, this, readCallback);
        }
    }

    /**
     * Returns the line flow
     */
    public Flowable<LineSocketStatus> readStatus() {
        return states;
    }

    /**
     * Splits the buffer to lines
     */
    private void splitLines() {
        long timestamp = System.nanoTime();
        String data = new String(buffer.array(), buffer.position(), buffer.remaining(), StandardCharsets.UTF_8);
        String[] fragments = data.split(CRLF_PATTERN, -1);
        String tail = fragments[fragments.length - 1];
        buffer.clear();
        buffer.put(tail.getBytes(StandardCharsets.UTF_8));
        Arrays.stream(fragments).limit(fragments.length - 1)
                .map(line -> new Timed<>(line, timestamp, TimeUnit.NANOSECONDS))
                .forEach(lines::onNext);
    }

    /**
     * Returns the completable of close socket
     */
    public Completable waitForClose() {
        return closeSubj;
    }

    /**
     * Returns the Single of status connected (true)
     */
    public Single<Boolean> waitForConnected() {
        return readStatus()
                .subscribeOn(Schedulers.io())
                .filter(LineSocketStatus::connected)
                .map(ignored -> true)
                .first(false);
    }

    /**
     * Waits for the connection retry interval
     */
    private void waitRetry() {
        logger.atDebug().log("Waiting retry");
        Completable.timer(connectionTimeout, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.io())
                .subscribe(this::connect);
    }

    /**
     * Returns true if successfully writes the command
     *
     * @param cmd the command
     */
    public boolean writeCommand(String cmd) {
        AsynchronousSocketChannel ch = status.get().channel();
        if (ch == null) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap((cmd + LF).getBytes(StandardCharsets.UTF_8));
            while (buffer.remaining() > 0) {
                try {
                    ch.write(buffer).get();
                } catch (RuntimeException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException ex1) {
                        onWriteError(ex1);
                    }
                    return false;
                } catch (Throwable e) {
                    onWriteError(e);
                    return false;
                }
            }
            logger.atDebug().setMessage("Written command {}").addArgument(cmd).log();
        return true;
    }

    private static final CompletionHandler<Integer, LineSocket> readCallback = new CompletionHandler<>() {
        @Override
        public void completed(Integer size, LineSocket subject) {
            subject.onRead(size);
        }

        @Override
        public void failed(Throwable ex, LineSocket subject) {
            subject.onReadError(ex);
        }
    };


}
