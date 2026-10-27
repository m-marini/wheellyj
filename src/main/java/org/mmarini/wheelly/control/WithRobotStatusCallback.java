/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.control;

import java.util.function.Consumer;

/**
 * Defines the contract for components that support the registration of robot status callbacks.
 * <p>
 * This interface provides a standardized mechanism to attach asynchronous event listeners
 * capable of intercepting incoming robot telemetry. It decouples the raw telemetry providers
 * from downstream processing units, such as logging utilities, safety supervisors, or state trackers.
 * </p>
 */
public interface WithRobotStatusCallback {

    /**
     * Registers a callback consumer to be invoked whenever a fresh robot status update is available.
     * <p>
     * The attached callback listener reactively ingests the latest {@link RobotStatus} snapshot,
     * allowing upper architectural layers to track continuous changes in position, sensors,
     * and subsystem metrics.
     * </p>
     *
     * @param callback The consumer routine that handles the incoming {@link RobotStatus} telemetry data.
     */
    void addOnRobotStatus(Consumer<RobotStatus> callback);
}
