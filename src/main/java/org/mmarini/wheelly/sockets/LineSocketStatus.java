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

package org.mmarini.wheelly.sockets;

import java.nio.channels.AsynchronousSocketChannel;
import java.util.Objects;

/**
 * defines the status of line socket
 *
 * @param connected  true if the socket is connected
 * @param connecting true if the socket is connectinf
 * @param closed     true if the socket is closed
 * @param channel    the channel
 */
public record LineSocketStatus(
        boolean connected,
        boolean connecting,
        boolean closed,
        AsynchronousSocketChannel channel) {
    /**
     * Returns the status with changed closed flag
     *
     * @param channel true if the socket is closed
     */
    public LineSocketStatus channel(AsynchronousSocketChannel channel) {
        return !Objects.equals(this.channel, channel)
                ? new LineSocketStatus(connected, connecting, closed, channel)
                : this;
    }

    /**
     * Returns the status with changed closed flag
     *
     * @param closed true if the socket is closed
     */
    public LineSocketStatus closed(boolean closed) {
        return this.closed != closed
                ? new LineSocketStatus(connected, connecting, closed, channel)
                : this;
    }

    /**
     * Returns the status with changed connected flag
     *
     * @param connected true if the socket is connected
     */
    public LineSocketStatus connected(boolean connected) {
        return this.connected != connected
                ? new LineSocketStatus(connected, connecting, closed, channel)
                : this;
    }

    /**
     * Returns the status with changed connecting flag
     *
     * @param connecting true if the socket is connecting
     */
    public LineSocketStatus connecting(boolean connecting) {
        return this.connecting != connecting
                ? new LineSocketStatus(connected, connecting, closed, channel)
                : this;
    }
}
