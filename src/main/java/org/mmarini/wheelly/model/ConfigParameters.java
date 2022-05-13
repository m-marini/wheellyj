/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

/**
 * Configuration parameters
 */
public class ConfigParameters {

    /**
     * Creates configuration parameters
     *
     * @param host
     * @param port
     * @param connectionTimeout
     * @param retryConnectionInterval
     * @param readTimeout
     * @param motorCommandInterval
     * @param scanCommandInterval
     */
    public static ConfigParameters create(String host, int port,
                                          long connectionTimeout, long retryConnectionInterval, long readTimeout,
                                          long motorCommandInterval, long scanCommandInterval) {
        return new ConfigParameters(host, port,
                connectionTimeout, retryConnectionInterval, readTimeout,
                motorCommandInterval, scanCommandInterval);
    }

    public final long connectionTimeout;
    public final String host;
    public final long motorCommandInterval;
    public final int port;
    public final long readTimeout;
    public final long retryConnectionInterval;
    public final long scanCommandInterval;

    /**
     * Creates configuration parameters
     *
     * @param host
     * @param port
     * @param connectionTimeout
     * @param retryConnectionInterval
     * @param readTimeout
     * @param motorCommandInterval
     * @param scanCommandInterval
     */
    protected ConfigParameters(String host, int port,
                               long connectionTimeout, long retryConnectionInterval, long readTimeout,
                               long motorCommandInterval, long scanCommandInterval) {
        this.connectionTimeout = connectionTimeout;
        this.host = host;
        this.port = port;
        this.readTimeout = readTimeout;
        this.retryConnectionInterval = retryConnectionInterval;
        this.motorCommandInterval = motorCommandInterval;
        this.scanCommandInterval = scanCommandInterval;
    }
}
