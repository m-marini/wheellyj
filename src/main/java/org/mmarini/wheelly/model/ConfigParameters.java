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
     * @param numClockSample
     * @param clockInterval
     * @param clockTimeout
     * @param restartClockSyncDelay
     * @param statusInterval
     * @param startQueryDelay
     */
    public static ConfigParameters create(String host, int port,
                                          long connectionTimeout, long retryConnectionInterval, long readTimeout,
                                          int numClockSample, long clockInterval, long clockTimeout, long restartClockSyncDelay,
                                          long statusInterval, long startQueryDelay) {
        return new ConfigParameters(host, port,
                connectionTimeout, retryConnectionInterval, readTimeout,
                numClockSample, clockInterval, clockTimeout, restartClockSyncDelay,
                statusInterval, startQueryDelay);
    }

    public final long clockInterval;
    public final long clockTimeout;
    public final long connectionTimeout;
    public final String host;
    public final int numClockSample;
    public final int port;
    public final long statusInterval;
    public final long readTimeout;
    public final long restartClockSyncDelay;
    public final long retryConnectionInterval;
    public final long startQueryDelay;

    /**
     * Creates configuration parameters
     *
     * @param host
     * @param port
     * @param connectionTimeout
     * @param retryConnectionInterval
     * @param readTimeout
     * @param numClockSample
     * @param clockInterval
     * @param clockTimeout
     * @param restartClockSyncDelay
     * @param statusInterval
     * @param startQueryDelay
     */
    protected ConfigParameters(String host, int port,
                               long connectionTimeout, long retryConnectionInterval, long readTimeout,
                               int numClockSample, long clockInterval, long clockTimeout, long restartClockSyncDelay,
                               long statusInterval, long startQueryDelay) {
        this.clockInterval = clockInterval;
        this.clockTimeout = clockTimeout;
        this.connectionTimeout = connectionTimeout;
        this.host = host;
        this.numClockSample = numClockSample;
        this.port = port;
        this.statusInterval = statusInterval;
        this.readTimeout = readTimeout;
        this.restartClockSyncDelay = restartClockSyncDelay;
        this.retryConnectionInterval = retryConnectionInterval;
        this.startQueryDelay = startQueryDelay;
    }
}
