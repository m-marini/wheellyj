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

import java.util.Objects;

public record RealRobotStatus(
        boolean started, boolean connecting, boolean connected, boolean configuring, boolean configured, boolean halted,
        long startTime,
        long lastActivity, LineSocket robotSocket,
        LineSocket cameraSocket,
        ClockConverter clockConverter,
        WatchDog watchDog) implements RobotStatusApi {

    /**
     * Returns the real robot state with the changed camera socket
     *
     * @param cameraSocket the camera socket
     */
    public RealRobotStatus cameraSocket(LineSocket cameraSocket) {
        return !Objects.equals(this.cameraSocket, cameraSocket)
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed clock converter
     *
     * @param clockConverter the clock converter
     */
    public RealRobotStatus clockConverter(ClockConverter clockConverter) {
        return !Objects.equals(this.clockConverter, clockConverter)
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed halted flag
     *
     * @param halted true if robot is halted
     */
    public RealRobotStatus halted(boolean halted) {
        return this.halted != halted
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed last activity time
     *
     * @param lastActivity the last activity time
     */
    public RealRobotStatus lastActivity(long lastActivity) {
        return this.lastActivity != lastActivity
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed robot socket
     *
     * @param robotSocket the robot socket
     */
    public RealRobotStatus robotSocket(LineSocket robotSocket) {
        return !Objects.equals(this.robotSocket, robotSocket)
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the configured state
     */
    public RealRobotStatus setConfigured() {
        return !(!connecting && connected && !configuring && configured)
                ? new RealRobotStatus(started, false, true, false, true, halted,
                startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the configuring state
     */
    public RealRobotStatus setConfiguring() {
        return !(!connecting && connected && configuring && !configured)
                ? new RealRobotStatus(started, false, true, true, false, halted,
                startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the state in connecting value
     */
    public RealRobotStatus setConnected() {
        return !(connected && !connecting && !configuring && !configured)
                ? new RealRobotStatus(started, false, true, false, false, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the connecting state
     */
    public RealRobotStatus setConnecting() {
        return !(connecting && !connected && !configuring && !configured)
                ? new RealRobotStatus(started, true, false, false, false, halted,
                startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the unconnected state
     */
    public RealRobotStatus setUnconnected() {
        return !(!connecting && !connected && !configuring && !configured)
                ? new RealRobotStatus(started, false, false, false, false, halted,
                startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed start simulation time
     *
     * @param startTime the start simulation time
     */
    public RealRobotStatus startTime(long startTime) {
        return this.startTime != startTime
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed started flag
     *
     * @param started true if robot is started
     */
    public RealRobotStatus started(boolean started) {
        return this.started != started
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }

    /**
     * Returns the real robot state with the changed watch dog
     *
     * @param watchDog the watch dog
     */
    public RealRobotStatus watchDog(WatchDog watchDog) {
        return !Objects.equals(this.watchDog, watchDog)
                ? new RealRobotStatus(started, connecting, connected, configuring, configured, halted, startTime, lastActivity, robotSocket, cameraSocket, clockConverter, watchDog)
                : this;
    }
}
