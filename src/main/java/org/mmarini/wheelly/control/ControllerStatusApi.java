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

package org.mmarini.wheelly.control;

/**
 * Provides a read-only snapshot of the robot controller's operational state.
 * <p>
 * This interface exposes the state flags required to monitor whether the controller
 * has active execution loops, has established valid connections, or is currently
 * processing AI/vision inference routines.
 * </p>
 */
public interface ControllerStatusApi {
    /**
     * Indicates whether the controller is currently running inference loops.
     *
     * @return {@code true} if the inference engine is actively processing sensor telemetry,
     * {@code false} otherwise
     */
    boolean inferencing();

    /**
     * Indicates whether the controller has completed initialisation and is ready.
     *
     * @return {@code true} if the connection pipelines are active and prepared for commands,
     * {@code false} otherwise
     */
    boolean ready();

    /**
     * Indicates whether the controller's background threads have been started.
     *
     * @return {@code true} if the daemon loops are running, {@code false} if the controller
     * is stopped or has been shut down
     */
    boolean started();
}
