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

package org.mmarini.wheelly.mqtt;

import org.mmarini.wheelly.apis.RobotStatusApi;
import org.mmarini.wheelly.apis.WatchDog;

import java.util.Objects;

public record MqttRobotStatus(
        boolean started, boolean closed, boolean connecting, boolean connected,
        boolean robotConfiguring, boolean robotConfigured,
        boolean cameraConfiguring, boolean cameraConfigured, boolean halted,
        long startTime,
        long lastActivity,
        WatchDog watchDog) implements RobotStatusApi {

    @Override
    public boolean configured() {
        return robotConfigured && cameraConfigured;
    }

    @Override
    public boolean configuring() {
        return robotConfiguring || connecting;
    }

    /**
     * Returns the real robot state with the changed halted flag
     *
     * @param halted true if robot is halted
     */
    public MqttRobotStatus halted(boolean halted) {
        return this.halted != halted
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured,
                cameraConfiguring, cameraConfigured, halted, startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed last activity time
     *
     * @param lastActivity the last activity time
     */
    public MqttRobotStatus lastActivity(long lastActivity) {
        return this.lastActivity != lastActivity
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured,
                cameraConfiguring, cameraConfigured, halted, startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the robotConfigured state
     */
    public MqttRobotStatus setCameraConfigured() {
        return !(!cameraConfiguring && cameraConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured,
                false, true, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the robotConfiguring state
     */
    public MqttRobotStatus setCameraConfiguring() {
        return !(cameraConfiguring && !cameraConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured,
                true, false, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the robotConfigured state
     */
    public MqttRobotStatus setCameraNotConfigured() {
        return !(!cameraConfiguring && !cameraConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured,
                false, false, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    public MqttRobotStatus setClosed() {
        return !(closed && !connecting && connected && !robotConfiguring && robotConfigured && !cameraConfiguring && !cameraConfigured)
                ? new MqttRobotStatus(started, true, false, false, false, false,
                false, false, false, startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the state in connecting value
     */
    public MqttRobotStatus setConnected() {
        return !(connected && !connecting)
                ? new MqttRobotStatus(started, closed, false, true, robotConfiguring, robotConfigured,
                cameraConfiguring, cameraConfigured, halted, startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the connecting state
     */
    public MqttRobotStatus setConnecting() {
        return !(connecting && !connected)
                ? new MqttRobotStatus(started, closed, true, false, robotConfiguring, robotConfigured,
                cameraConfiguring, cameraConfigured, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the robotConfigured state
     */
    public MqttRobotStatus setRobotConfigured() {
        return !(!robotConfiguring && robotConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, false, true, cameraConfiguring, cameraConfigured, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the robotConfiguring state
     */
    public MqttRobotStatus setRobotConfiguring() {
        return !(robotConfiguring && !robotConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, true, false, cameraConfiguring, cameraConfigured, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the robotConfigured state
     */
    public MqttRobotStatus setRobotNotConfigured() {
        return !(!robotConfiguring && !robotConfigured)
                ? new MqttRobotStatus(started, closed, connecting, connected, false, false, cameraConfiguring, cameraConfigured, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the unconnected state
     */
    public MqttRobotStatus setUnconnected() {
        return !(!connecting && !connected)
                ? new MqttRobotStatus(started, closed, false, false, robotConfiguring, robotConfigured, cameraConfiguring, cameraConfigured, halted,
                startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed start simulation time
     *
     * @param startTime the start simulation time
     */
    public MqttRobotStatus startTime(long startTime) {
        return this.startTime != startTime
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured, cameraConfiguring, cameraConfigured, halted, startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed started flag
     *
     * @param started true if robot is started
     */
    public MqttRobotStatus started(boolean started) {
        return this.started != started
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured, cameraConfiguring, cameraConfigured, halted, startTime, lastActivity, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed watch dog
     *
     * @param watchDog the watch dog
     */
    public MqttRobotStatus watchDog(WatchDog watchDog) {
        return !Objects.equals(this.watchDog, watchDog)
                ? new MqttRobotStatus(started, closed, connecting, connected, robotConfiguring, robotConfigured, cameraConfiguring, cameraConfigured, halted, startTime, lastActivity, watchDog)
                : this;
    }
}
