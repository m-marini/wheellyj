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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.net.StandardSocketOptions.SO_KEEPALIVE;

public class RobotSocket implements Closeable {
    private static final String LF = "\r\n";
    private static final String CRLF_PATTERN = "\\r?\\n";
    private static final int READ_BUFFER_SIZE = 1024;

    private static final Logger logger = LoggerFactory.getLogger(RobotSocket.class);

    private final String robotHost;
    private final int port;
    private final long connectionTimeout;
    private final long readTimeout;
    private final ByteBuffer buffer;
    private SocketChannel channel;
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
    public RobotSocket(String robotHost, int port, long connectionTimeout, long readTimeout) {
        this.robotHost = robotHost;
        this.port = port;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        this.lines = List.of();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * Connects the robot
     *
     * @throws IOException in case of error
     */
    public RobotSocket connect() throws IOException {
        logger.debug("Connection {}, {}", robotHost, port);
        InetSocketAddress socketAddress = new InetSocketAddress(robotHost, port);
        SocketChannel channel = SocketChannel.open();
        channel.setOption(SO_KEEPALIVE, true);
        this.channel = channel;
        channel.socket().connect(socketAddress, (int) connectionTimeout);
        logger.debug("Connected {}, {}", robotHost, port);
        return this;
    }

    /**
     * Returns true if it has lines in the buffer
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
        if (channel != null && channel.isConnected()) {
            int n = channel.read(buffer);
            if (n > 0) {
                logger.debug("Read {} bytes", n);
                buffer.flip();
                splitLines();
            }
        }
    }

    public Timed<String> readLine() throws IOException {
        if (!hasLines()) {
            long timeout = System.currentTimeMillis() + readTimeout;
            while (!hasLines() && System.currentTimeMillis() <= timeout) {
                readBuffer();
            }
        }
        Timed<String> line = hasLines() ? lines.get(readPosition++) : null;
        if (line != null) {
            logger.debug("Read line {}", line.value());
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
        logger.debug("Read {} lines", this.lines.size());
        this.readPosition = 0;
    }


    /**
     * Writes a command to robot
     *
     * @param cmd the command
     */
    public void writeCommand(String cmd) throws IOException {
        logger.debug("Writing command {}", cmd);
        if (channel != null && channel.isConnected()) {
            ByteBuffer buffer = ByteBuffer.wrap((cmd + LF).getBytes(StandardCharsets.UTF_8));
            while (buffer.remaining() > 0) {
                channel.write(buffer);
            }
            logger.debug("Written command {}", cmd);
        }
    }
}