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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.schema.Locator;

import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Yaml.config;

/**
 * Configuration parameters
 */
public class ConfigParameters {
    /**
     * Creates configuration parameters
     *
     * @param host                    the host name
     * @param port                    the port
     * @param connectionTimeout       the connection timeout (ms)
     * @param retryConnectionInterval the retry connection interval (ms)
     * @param readTimeout             the read timeout (ms)
     * @param responseTime            the minimum interval between inference process (ms)
     * @param motorCommandInterval    the interval of motor command (ms)
     * @param scanCommandInterval     the interval of scanner command (ms)
     * @param dumpFile                the file dump of inference engine
     * @param robotLogFile            the file log
     * @param netMonitor
     */
    public static ConfigParameters create(String host, int port,
                                          long connectionTimeout, long retryConnectionInterval, long readTimeout,
                                          long responseTime, long motorCommandInterval, long scanCommandInterval, String dumpFile, String robotLogFile, boolean netMonitor) {
        return new ConfigParameters(host, port,
                connectionTimeout, retryConnectionInterval, readTimeout,
                responseTime, motorCommandInterval, scanCommandInterval, dumpFile, robotLogFile, netMonitor);
    }

    public static ConfigParameters fromJson(JsonNode root, Locator locator) {
        requireNonNull(root);
        requireNonNull(locator);
        config().apply(locator).accept(root);
        return ConfigParameters.create(
                locator.path("host").getNode(root).asText(),
                locator.path("port").getNode(root).asInt(),
                locator.path("connectionTimeout").getNode(root).asLong(),
                locator.path("retryConnectionInterval").getNode(root).asLong(),
                locator.path("readTimeout").getNode(root).asLong(),
                locator.path("responseTime").getNode(root).asLong(),
                locator.path("motorCommandInterval").getNode(root).asLong(),
                locator.path("scanCommandInterval").getNode(root).asLong(),
                locator.path("dumpFile").getNode(root).asText(null),
                locator.path("robotLogFile").getNode(root).asText(null),
                locator.path("netMonitor").getNode(root).asBoolean(false));
    }

    public final long connectionTimeout;
    public final String dumpFile;
    public final String host;
    public final long motorCommandInterval;
    public final boolean netMonitor;
    public final int port;
    public final long readTimeout;
    public final long responseTime;
    public final long retryConnectionInterval;
    public final String robotLogFile;
    public final long scanCommandInterval;

    /**
     * Creates configuration parameters
     *
     * @param host                    the host name
     * @param port                    the port
     * @param connectionTimeout       the connection timeout (ms)
     * @param retryConnectionInterval the retry connection interval (ms)
     * @param readTimeout             the read timeout (ms)
     * @param responseTime            the response time of inference engine
     * @param motorCommandInterval    the interval of motor command (ms)
     * @param scanCommandInterval     the interval of scanner command (ms)
     * @param dumpFile                the dump file with action , command
     * @param robotLogFile
     * @param netMonitor              true if monitor active
     */
    protected ConfigParameters(String host, int port,
                               long connectionTimeout, long retryConnectionInterval, long readTimeout,
                               long responseTime, long motorCommandInterval, long scanCommandInterval,
                               String dumpFile, String robotLogFile, boolean netMonitor) {
        this.connectionTimeout = connectionTimeout;
        this.host = requireNonNull(host);
        this.port = port;
        this.readTimeout = readTimeout;
        this.retryConnectionInterval = retryConnectionInterval;
        this.motorCommandInterval = motorCommandInterval;
        this.scanCommandInterval = scanCommandInterval;
        this.dumpFile = dumpFile;
        this.responseTime = responseTime;
        this.robotLogFile = robotLogFile;
        this.netMonitor = netMonitor;
    }
}
