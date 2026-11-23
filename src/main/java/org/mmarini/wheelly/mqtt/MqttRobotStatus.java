/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.mqtt;

import org.mmarini.wheelly.robots.RobotStatusApi;

public record MqttRobotStatus(
        boolean started, boolean closed, boolean connecting, boolean connected,
        boolean robotConfiguring, boolean robotConfigured,
        boolean halted,
        long startTime,
        long lastActivity
) implements RobotStatusApi {

    @Override
    public boolean configured() {
        return robotConfigured;
    }

    @Override
    public boolean configuring() {
        return robotConfiguring || connecting;
    }

    /**
     * Returns the real robot state with the changed last activity time
     *
     * @param lastActivity the last activity time
     */
    public MqttRobotStatus lastActivity(long lastActivity) {
        return this.lastActivity != lastActivity
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured,
                halted, startTime, lastActivity)
                : this;
    }

    public MqttRobotStatus setClosed() {
        return !(closed && !connecting && connected && !robotConfiguring && robotConfigured)
                ? new MqttRobotStatus(started, true, false, false, false, false,
                false, startTime, lastActivity)
                : this;
    }

    /**
     * Returns the state in connecting value
     */
    public MqttRobotStatus setConnected() {
        return !(connected && !connecting)
                ? new MqttRobotStatus(started, closed, false, true, robotConfiguring, robotConfigured,
                halted, startTime, lastActivity)
                : this;
    }

    /**
     * Returns the connecting state
     */
    public MqttRobotStatus setConnecting() {
        return !(connecting && !connected)
                ? new MqttRobotStatus(started, closed, true, false, robotConfiguring, robotConfigured,
                halted,
                startTime, lastActivity)
                : this;
    }

    /**
     * Returns the robotConfigured state
     */
    public MqttRobotStatus setRobotConfigured() {
        return !(!robotConfiguring && robotConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, false, true, halted,
                startTime, lastActivity)
                : this;
    }

    /**
     * Returns the robotConfiguring state
     */
    public MqttRobotStatus setRobotConfiguring() {
        return !(robotConfiguring && !robotConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, true, false, halted,
                startTime, lastActivity)
                : this;
    }

    /**
     * Returns the robotConfigured state
     */
    public MqttRobotStatus setRobotNotConfigured() {
        return !(!robotConfiguring && !robotConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, false, false, halted,
                startTime, lastActivity)
                : this;
    }

    /**
     * Returns the unconnected state
     */
    public MqttRobotStatus setUnconnected() {
        return !(!connecting && !connected)
                ? new MqttRobotStatus(started, closed, false, false, robotConfiguring, robotConfigured, halted,
                startTime, lastActivity)
                : this;
    }

    /**
     * Returns the real robot state with the changed start simulation time
     *
     * @param startTime the start simulation time
     */
    public MqttRobotStatus startTime(long startTime) {
        return this.startTime != startTime
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured, halted, startTime, lastActivity)
                : this;
    }

    /**
     * Returns the real robot state with the changed started flag
     *
     * @param started true if robot is started
     */
    public MqttRobotStatus started(boolean started) {
        return this.started != started
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured, halted, startTime, lastActivity)
                : this;
    }
}
