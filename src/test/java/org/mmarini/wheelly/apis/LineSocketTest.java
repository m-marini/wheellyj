/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class LineSocketTest {

    public static final String ROBOT_HOST = "192.168.1.43";
    public static final int PORT = 22;
    public static final int CONNECTION_TIMEOUT = 10000;
    public static final int READ_TIMEOUT = CONNECTION_TIMEOUT;
    private LineSocket socket;

    // @Test
    void connectTest() throws InterruptedException {
        socket.connect();
        socket.writeCommand("sc 90");
        Thread.sleep(1000);
        socket.writeCommand("sc -90");
        Thread.sleep(1000);
        socket.writeCommand("sc 0");
    }

    @BeforeEach
    void setUp() {
        socket = new LineSocket(ROBOT_HOST, PORT, CONNECTION_TIMEOUT, READ_TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        socket.close();
    }
}
