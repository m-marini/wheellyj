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

import io.reactivex.rxjava3.core.Flowable;

/**
 * Defines the reactive data-stream API for extracting the comprehensive robot controller status.
 * <p>
 * This interface bridges the robot control infrastructure with asynchronous consumer layers,
 * leveraging RxJava {@link Flowable} streams to continuously publish operational telemetry,
 * connection states, and structural diagnostics of the underlying controller.
 * </p>
 */
public interface WithControllerStatusFlowable {

    /**
     * Returns a reactive stream publishing the operational status updates of the robot controller.
     * <p>
     * The emitted data tokens encapsulate the holistic state of the control connector, allowing
     * upstream components to reactively monitor subsystem availability, sensor telemetry, and
     * actuation feedback loops.
     * </p>
     *
     * @return A {@link Flowable} emitting sequential {@link ControllerStatusApi} snapshots
     * reflecting the live controller configuration and state changes.
     */
    Flowable<ControllerStatusApi> readControllerStatus();
}
