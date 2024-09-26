/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import io.reactivex.rxjava3.schedulers.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Implements the Camera interface to the real camera.
 * <p>
 * The usage samples is
 * <code><pre>
 *
 *     CameraApi camera = new Camera(...);
 *
 *     camera.connect();
 *
 *     // Setting the callback on events
 *     robot.setOnCamera(...);
 *
 *     // Polling for status changed
 *     while (...) {
 *         robot.tick(...);
 *     }
 *
 *     robotSocket.close();
 * </pre>
 * </code>
 * </p>
 */
public class Camera implements CameraApi {
    private static final Logger logger = LoggerFactory.getLogger(Camera.class);
    private final LineSocket socket;
    private Consumer<CameraEvent> onCamera;

    protected Camera(String host, int port, long connectionTimeout, long readTimeout) {
        socket = new LineSocket(host, port, connectionTimeout, readTimeout);
    }

    @Override
    public void close() throws IOException {
        logger.atInfo().log("Closing ...");
        try {
            socket.close();
        } catch (Exception ex) {
            logger.atError().setCause(ex).log();
        }
    }

    @Override
    public void connect() throws IOException {
        socket.connect();
    }

    /**
     * Parses the data received for messages.
     * The robot status is updated with received message.
     *
     * @param line the data line received
     */
    private void parseForMessage(Timed<String> line) {
        CameraEvent event = CameraEvent.create(line.value());
        if (onCamera != null) {
            onCamera.accept(event);
        }
    }

    /**
     * Returns the line read from roboto connection
     * Notifies the line if onReadLine call back has been registered
     *
     * @throws IOException in case of error
     */
    private Timed<String> readLine() throws IOException {
        Timed<String> line = socket.readLine();
        logger.atDebug().setMessage("Read {}").addArgument(line).log();
        return line;
    }

    @Override
    public void setOnCamera(Consumer<CameraEvent> callback) {
        onCamera = callback;
    }

    @Override
    public void tick(long dt) throws IOException {
        long t0 = System.currentTimeMillis();
        long timeout = t0 + dt;
        long t = t0;
        // Repeat until interval timeout
        while (t < timeout) {
            // Read the robot status
            Timed<String> line = readLine();
            if (line != null) {
                parseForMessage(line);
            }
            t = System.currentTimeMillis();
        }
    }
}
