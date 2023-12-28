/*
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.OptionalLong;

import static java.lang.Math.max;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * Implements the command method of state node:
 * <code>init</code>,
 * <code>entry</code>,
 * <code>exit</code><br>
 * Implements access to key,value processor context by prefixing node id in the key.<br>
 * Implements commons functions to manage timeout, robot block, automatic scanning
 */
public abstract class AbstractStateNode implements StateNode {
    private static final Logger logger = LoggerFactory.getLogger(AbstractStateNode.class);

    /**
     * Returns the command to set the auto scan behavior of node from configuration
     *
     * @param root    the configuration
     * @param locator the auto scan locator
     * @param id      the node id
     */
    protected static ProcessorCommand loadAutoScanOnInit(JsonNode root, Locator locator, String id) {
        return ProcessorCommand.setProperties(Map.of(
                id + ".scanInterval", locator.path("scanInterval").getNode(root).asLong(),
                id + ".minSensorDir", locator.path("minSensorDir").getNode(root).asInt(),
                id + ".maxSensorDir", locator.path("maxSensorDir").getNode(root).asInt(),
                id + ".sensorDirNumber", locator.path("sensorDirNumber").getNode(root).asInt()));
    }

    /**
     * Returns the command to set the timeout properties of node from configuration
     *
     * @param root    the configuration document
     * @param locator the locator of node
     * @param id      the identifier of node
     */
    protected static ProcessorCommand loadTimeout(JsonNode root, Locator locator, String id) {
        JsonNode node = locator.path("timeout").getNode(root);
        return !node.isMissingNode()
                ? ProcessorCommand.setProperties(Map.of(id + ".timeout", node.asLong()))
                : null;
    }

    private final String id;
    private final ProcessorCommand onInit;
    private final ProcessorCommand onEntry;
    private final ProcessorCommand onExit;

    /**
     * Create the abstract node
     *
     * @param id      the node identifier
     * @param onInit  the initialization command or null if none
     * @param onEntry the entry command or null if none
     * @param onExit  the exit command or null if none
     */
    protected AbstractStateNode(String id, ProcessorCommand onInit, ProcessorCommand onEntry, ProcessorCommand onExit) {
        this.id = requireNonNull(id);
        this.onInit = onInit;
        this.onEntry = onEntry;
        this.onExit = onExit;
    }

    @Override
    public void entry(ProcessorContext context) {
        long time = context.getRobotStatus().getLocalTime();
        context.put(format("%s.entryTime", id), time);
        if (onEntry != null) {
            onEntry.execute(context);
        }
    }

    /**
     * Performs the entry process to set the auto scan
     *
     * @param context the processor context
     */
    protected void entryAutoScan(ProcessorContext context) {
        put(context, "scanTime", -1);
        put(context, "scanIndex", 0);
        tickAutoScan(context);
    }

    @Override
    public void exit(ProcessorContext context) {
        if (onExit != null) {
            onExit.execute(context);
        }
    }

    /**
     * Returns the value by node key
     *
     * @param context the processor context
     * @param key     the node key
     * @param <T>     the type of value
     */
    protected <T> T get(ProcessorContext context, String key) {
        return context.get(format("%s.%s", id, key));
    }

    /**
     * Returns the double value by node key or 0 if not exits
     *
     * @param context the processor context
     * @param key     the node key
     */
    public double getDouble(ProcessorContext context, String key) {
        return context.getDouble(format("%s.%s", id, key));
    }

    @Override
    public long getElapsedTime(ProcessorContext context) {
        return context.getRobotStatus().getLocalTime() -
                getEntryTime(context);
    }

    @Override
    public long getEntryTime(ProcessorContext context) {
        return context.getLong(format("%s.entryTime", id));
    }

    @Override
    public String getId() {
        return id;
    }

    /**
     * Returns the int value by node key or 0 if not exits
     *
     * @param context the processor context
     * @param key     the node key
     */
    public int getInt(ProcessorContext context, String key) {
        return context.getInt(format("%s.%s", id, key));
    }

    /**
     * Returns the long value by node key or 0 if not exits
     *
     * @param context the processor context
     * @param key     the node key
     */
    public long getLong(ProcessorContext context, String key) {
        return context.getLong(format("%s.%s", id, key));
    }

    @Override
    public void init(ProcessorContext context) {
        if (onInit != null) {
            onInit.execute(context);
        }
    }

    @Override
    public boolean isTimeout(ProcessorContext context) {
        OptionalLong timeout = context.getOptLong(format("%s.timeout", id));
        return timeout.isPresent() && getElapsedTime(context) >= timeout.getAsLong();
    }

    /**
     * Put a value in the context prefixed by node id
     *
     * @param context the processor context
     * @param key     the key
     * @param value   the value
     */
    protected void put(ProcessorContext context, String key, Object value) {
        context.put(format("%s.%s", id, key), value);
    }

    /**
     * Remove a value in the context prefixed by node id
     *
     * @param context the processor context
     * @param key     the key
     */
    protected void remove(ProcessorContext context, String key) {
        context.remove(format("%s.%s", id, key));
    }

    /**
     * Performs the auto scan behaviors.
     * Moves the sensor to a random direction within a range at given steps on given time interval
     *
     * @param context the processor context
     */
    protected Tuple2<String, RobotCommands> tickAutoScan(ProcessorContext context) {
        long scanInterval = getLong(context, "scanInterval");
        // Check for scan interval set
        if (scanInterval > 0) {
            long scanTime = getLong(context, "scanTime");
            long time = context.getRobotStatus().getLocalTime();
            // Check for scan timeout
            long t0 = System.currentTimeMillis();
            logger.atDebug().log("tickAutoScan currentTime={}, remoteTime={}, statusDt={}, statusScanDt={}, time to next scan={}",
                    t0,
                    context.getRobotStatus().getRobotTime(),
                    t0 - time,
                    t0 - scanTime,
                    scanTime + scanInterval - time);
            if (scanTime < 0 || time > scanTime + scanInterval) {
                int minSensorDir = clip(getInt(context, "minSensorDir"), -90, 90);
                int maxSensorDir = clip(getInt(context, "maxSensorDir"), -90, 90);
                int sensorDirNumber = max(getInt(context, "sensorDirNumber"), 1);
                // Check for random scan direction
                RobotCommands command;
                if (sensorDirNumber > 1) {
                    int scanIndex = getInt(context, "scanIndex");
                    int mod = (sensorDirNumber - 1) * 2;
                    int x = scanIndex;
                    if (x >= sensorDirNumber) {
                        x = mod - x;
                    }
                    int dir = x * (maxSensorDir - minSensorDir) / (sensorDirNumber - 1) + minSensorDir;
                    put(context, "scanIndex", (scanIndex + 1) % mod);
                    command = RobotCommands.scan(dir);
                } else {
                    // Fix scan direction
                    command = RobotCommands.scan((minSensorDir + maxSensorDir) / 2);
                }
                put(context, "scanTime", time);
                logger.atDebug().log("sensor scan {}", command.scanDirection);
                return Tuple2.of(NONE_EXIT, command);
            }
        }
        return NONE_RESULT;
    }
}
