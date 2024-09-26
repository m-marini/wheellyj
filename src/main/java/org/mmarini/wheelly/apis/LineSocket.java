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

import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subjects.SingleSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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

    private final String robotHost;
    private final int port;
    private final long connectionTimeout;
    private final long readTimeout;
    private final ByteBuffer buffer;
    private AsynchronousSocketChannel channel;
    private List<Timed<String>> lines;
    private int readPosition;

    /**
     * Creates a robot socket
     *
     * @param robotHost         the robot host
     * @param port              the robot port
     * @param connectionTimeout the connection timeout in millis
     * @param readTimeout       the read timeout in millis
     */
    public LineSocket(String robotHost, int port, long connectionTimeout, long readTimeout) {
        this.robotHost = robotHost;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.lines = List.of();
    }

    @Override
    public void close() throws IOException {
        AsynchronousSocketChannel ch = channel;
        channel = null;
        if (ch != null) {
            logger.atDebug().log("Closing ...");
            try {
                ch.close();
            } catch (Throwable ex) {
                logger.atError().setCause(ex).log();
            }
        }
    }

    /**
     * Connects the robot socket
     *
     * @throws IOException in case of error
     */
    public LineSocket connect() throws IOException {
        logger.atDebug().setMessage("Connection {}, {}")
                .addArgument(robotHost).addArgument(port).log();
        InetSocketAddress socketAddress = new InetSocketAddress(robotHost, port);
        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
        channel.setOption(SO_KEEPALIVE, true);
        Future<Void> x = channel.connect(socketAddress);
        try {
            x.get(connectionTimeout, TimeUnit.MILLISECONDS);
            this.channel = channel;
            logger.atDebug().setMessage("Connected {}, {}")
                    .addArgument(robotHost).addArgument(port).log();
            return this;
        } catch (InterruptedException | TimeoutException e) {
            throw new InterruptedIOException("Connection timeout");
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
    }

    /**
     * Returns true if it has lines in the buffer
     * It does not poll for receivd data.
     */
    private boolean hasLines() {
        return readPosition < lines.size();
    }

    /**
     * Reads the buffer
     *
     * @throws IOException in case of error
     */
    private void readBuffer() throws IOException {
        AsynchronousSocketChannel ch = channel;
        if (ch != null && ch.isOpen()) {
            try {
                SingleSubject<Integer> result = SingleSubject.create();
                CompletionHandler<Integer, SingleSubject<Integer>> callback = new CompletionHandler<>() {
                    @Override
                    public void completed(Integer integer, SingleSubject<Integer> subject) {
                        subject.onSuccess(integer);
                    }

                    @Override
                    public void failed(Throwable throwable, SingleSubject<Integer> subject) {
                        subject.onError(throwable);
                    }
                };
                ch.read(buffer, readTimeout, TimeUnit.MILLISECONDS, result, callback);
                int n = result.blockingGet();
                if (n > 0) {
                    buffer.flip();
                    splitLines();
                }
            } catch (Throwable ex) {
                /*
                if (ch != channel) {
                    logger.atError().setCause(ex).log();
                    return;
                }

                 */
                Throwable cause = ex.getCause();
                if (cause instanceof InterruptedByTimeoutException) {
                    throw new InterruptedIOException("Read timeout");
                } else if (!(cause instanceof AsynchronousCloseException)) {
                    throw new IOException(cause);
                }
            }
        }
    }

    /**
     * Returns the read line from socket or null if no line available
     *
     * @throws IOException in case of error
     */
    public Timed<String> readLine() throws IOException {
        if (!hasLines()) {
            long timeout = System.currentTimeMillis() + readTimeout;
            while (!hasLines() && System.currentTimeMillis() <= timeout) {
                readBuffer();
            }
        }
        Timed<String> line = hasLines() ? lines.get(readPosition++) : null;
        if (line != null) {
            logger.atDebug().setMessage("Read line {}").addArgument(line::value).log();
        }
        return line;
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
        this.lines = Arrays.stream(fragments).limit(fragments.length - 1)
                .map(line -> new Timed<>(line, timestamp, TimeUnit.MILLISECONDS))
                .collect(Collectors.toList());
        this.readPosition = 0;
    }


    /**
     * Writes a command to robot
     *
     * @param cmd the command
     */
    public void writeCommand(String cmd) throws IOException {
        AsynchronousSocketChannel ch = channel;
        if (ch != null && ch.isOpen()) {
            ByteBuffer buffer = ByteBuffer.wrap((cmd + LF).getBytes(StandardCharsets.UTF_8));
            while (buffer.remaining() > 0) {
                try {
                    ch.write(buffer).get();
                } catch (RuntimeException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    }
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
            logger.atDebug().setMessage("Written command {}").addArgument(cmd).log();
        }
    }
}
