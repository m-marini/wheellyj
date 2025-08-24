/*
 * Copyright (c) 2023-2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.sockets;

import io.reactivex.rxjava3.schedulers.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RobotSocketTest {

    public static final long READ_TIMEOUT = 1000L;
    public static final long CONNECTION_TIMEOUT = 1000L;
    public static final int PORT = 22;
    public static final String ROBOT_HOST = "192.168.1.43";
    private static final Logger logger = LoggerFactory.getLogger(RobotSocketTest.class);

    public static void main(String[] args) {
        LineSocket socket = new LineSocket(ROBOT_HOST, PORT, CONNECTION_TIMEOUT, READ_TIMEOUT);
        logger.atDebug().log("Connecting...");
        socket.connect();
        logger.atDebug().log("Reading...");

        socket.writeCommand("sc 90");

        Timed<String> line = socket.readLines()
                .firstElement()
                .blockingGet();
        logger.atDebug().setMessage("Read {}").addArgument(line).log();

        logger.atDebug().log("Closing...");
        socket.close();
        logger.atDebug().log("Closed.");
    }

}