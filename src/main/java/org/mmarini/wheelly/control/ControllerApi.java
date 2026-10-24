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
import org.mmarini.wheelly.robots.HeadStatus;
import org.mmarini.wheelly.robots.MotionStatus;
import org.mmarini.wheelly.robots.RobotApi;
import org.mmarini.wheelly.utils.WithErrorFlowable;
import org.mmarini.wheelly.utils.WithShutdownCompletable;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.util.function.Consumer;

/**
 * Manages processing threads and event generation to interface with the robot.
 * <p>
 * This controller centralises asynchronous communication, telemetry ingestion,
 * and command processing. It uses a reactive, non-blocking model to handle high-frequency
 * updates from the robot's hardware, sensors, and inference engines without stalling
 * the calling thread.
 * </p>
 *
 * <h3>Lifecycle Management</h3>
 * <p>
 * The controller adheres to a strict lifecycle sequence:
 * </p>
 * <ol>
 *   <li><b>Instantiation:</b> Create the controller instance, optionally loading it from a JSON configuration via {@link #fromFile(File)}.</li>
 *   <li><b>Initialisation:</b> Connect the physical or simulated robot instance using {@link #init(RobotApi)}.</li>
 *   <li><b>Subscription:</b> Register callbacks and event listeners onto the respective consumer streams.</li>
 *   <li><b>Activation:</b> Call {@link #start()} to spawn internal processing threads and establish active connection channels.</li>
 *   <li><b>Operation:</b> Monitor the system via {@link #readReady()} and trigger {@link #reconnect()} if connection maintenance is required.</li>
 *   <li><b>Termination:</b> Call {@link #shutdown()} to release resources, safely close communication pipelines, and synchronously await full finalisation via the shutdown completable.</li>
 * </ol>
 *
 * <h3>Usage Sample</h3>
 * <pre>{@code
 *     RobotApi robot = ...;
 *     ControllerApi ctrl = ControllerApi.fromFile(new File("config.json"));
 *
 *     // Initialise connection with the robot backend
 *     ctrl.init(robot);
 *
 *     // Register event listeners prior to activation
 *     ctrl.addOnRobotStatus(status -> {
 *         // Code executed on new status
 *     });
 *
 *     ctrl.onLatch(status -> {
 *         // Code executed on latch updates
 *     });
 *
 *     ctrl.onInference(status -> {
 *         // Code executed on inference updates
 *         ctrl.motionStatus(motionStatus);
 *         ctrl.headStatus(headStatus);
 *     });
 *
 *     // Reactive stream for exception and fault handling
 *     ctrl.readError().subscribe(exception -> {
 *         // Code executed on error
 *     });
 *
 *     // Reactive stream for internal controller readiness status
 *     ctrl.readReady().subscribe(ready -> {
 *         // Code executed on readiness status change
 *     });
 *
 *     // Starts the robot controller and allocates threads
 *     ctrl.start();
 *     ...
 *
 *     // Initiates orderly shutdown of the controller
 *     ctrl.shutdown();
 *
 *     // Synchronously blocks until the shutdown sequence is fully finalised
 *     ctrl.readShutdown().blockingAwait();
 * }</pre>
 *
 * @see #init(RobotApi)
 * @see #start()
 * @see #shutdown()
 * @see #reconnect()
 */
public interface ControllerApi extends Controller,
        WithRobotStatusCallback, WithErrorFlowable, WithControllerStatusFlowable, WithControllerReadyFlowable,
        WithShutdownCompletable {

    /**
     * Instantiates a robot controller parsed from a configuration JSON file.
     *
     * @param file the configuration file containing the object blueprint
     * @return the initialised controller instance
     * @throws Throwable if parsing, structural validation, or instantiation fails
     */
    static ControllerApi fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }

    /**
     * Initialises the controller by linking the designated robot interface.
     *
     * @param robot the target robot API dependency
     * @return a reference to this controller instance to support method chaining
     */
    ControllerApi init(RobotApi robot);


    /**
     * Registers a callback to be executed upon updates to the robot's head status.
     * <p>
     * This method appends the new listener to the existing callback chain, ensuring
     * that all registered consumers are notified sequentially.
     * </p>
     *
     * @param callback the consumer function processing the new {@link HeadStatus}
     * @return a reference to this controller instance to support method chaining
     */
    RobotController onHeadStatus(Consumer<HeadStatus> callback);

    /**
     * Registers a callback to be executed upon updates to the robot's motion status.
     * <p>
     * This method appends the new listener to the existing callback chain, ensuring
     * that all registered consumers are notified sequentially.
     * </p>
     *
     * @param callback the consumer function processing the new {@link MotionStatus}
     * @return a reference to this controller instance to support method chaining
     */
    RobotController onMotionStatus(Consumer<MotionStatus> callback);

    /**
     * Returns the flow of the controller ready status.
     *
     * @return a {@link Flowable} emitting {@code true} when the controller is ready,
     * {@code false} otherwise
     */
    Flowable<Boolean> readReady();

    /**
     * Recovers and re-establishes the connection pipeline to the robot backend.
     */
    void reconnect();

    /**
     * Finalises active operations and safely shuts down the controller.
     */
    void shutdown();

    /**
     * Measures the real processing performance speed of the simulation loop.
     *
     * @return the calculated real simulation power coefficient
     */
    double simRealSpeed();

    /**
     * Starts the internal processing loops and daemon threads.
     */
    void start();
}
