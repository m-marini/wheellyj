/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Completable;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;

/**
 * Manages the processing threads and event generation to interface the robot.
 * <p>
 * The usage samples is
 * <code><pre>
 *     RobotControllerApi ctrl = ...;
 *
 *     // Subscribe for required events
 *
 *     // Reactive read for status change
 *     ctrl.readRobotStatus(status -> {
 *        // Code on new status
 *         ...
 *     });
 *
 *     // Reactive read for inference
 *     ctrl.readInference(status -> {
 *        // Code on inference status
 *         ...
 *     });
 *
 *     // Reactive read for errors
 *     ctrl.readError(exception -> {
 *        // Code on exception
 *         ...
 *     });
 *
 *     // Reactive read controller status
 *     ctrl.readControllerStatus(statusString -> {
 *        // Code on controller status
 *         ...
 *     });
 *
 *     // Reactive read sent commands
 *     ctrl.readCommand(command -> {
 *        // Code on command sent
 *         ...
 *     });
 *
 *     // Reactive read on data sent
 *     ctrl.readWriteLine(lineString -> {
 *        // Code on data sent
 *         ...
 *     });
 *
 *     // Reactive read on data received
 *     ctrl.readReadLine(lineString -> {
 *        // Code on data received
 *         ...
 *     });
 *
 *      // Starts the robot controller
 *     ctrl.start();
 *     ...
 *
 *     // Shutdown the controller
 *     ctrl.shutdown();
 *
 *     // Sending any command
 *     ctrl.execute(...);
 *
 *     // Reactive wait for shutdown
 *     ctrl.readShutdown().blockingAwait();
 * </pre>
 * </code>
 * </p>
 */
public interface RobotControllerApi extends RobotControllerConnector, WithRobotStatusFlowable, WithErrorFlowable, WithCommandFlowable, WithControllerFlowable {
    /**
     * Returns the robot controller from configuration JSON
     *
     * @param config  the root document
     * @param locator the configuration locator
     * @param robot   the robot api
     */
    static RobotControllerApi fromConfig(JsonNode config, Locator locator, RobotApi robot) {
        return Utils.createObject(config, locator, new Object[]{robot}, new Class[]{RobotApi.class});
    }

    /**
     * Returns the robot controller from configuration JSON
     *
     * @param file the configuration file
     */
    static RobotControllerApi fromFile(File file) throws IOException {
        return Utils.createObject(file);
    }

    /**
     * Connects the robot
     *
     * @param robot the robot
     */
    RobotControllerApi connectRobot(RobotApi robot);

    /**
     * Returns the shutdown signal
     */
    Completable readShutdown();

    /**
     * Reconnect the robot
     */
    void reconnect();

    /**
     * Shutdowns the controller
     */
    void shutdown();

    /**
     * Returns the real simulation speed
     */
    double simRealSpeed();

    /**
     * Starts the controller
     */
    void start();
}
