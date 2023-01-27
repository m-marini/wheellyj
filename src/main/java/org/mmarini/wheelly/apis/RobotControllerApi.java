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

import io.reactivex.rxjava3.core.Completable;

import java.util.function.Consumer;

/**
 * Manages the processing threads and event generation to interface the robot
 */
public interface RobotControllerApi extends WithIOCallback, WithStatusCallback, WithErrorCallback {
    /**
     * Returns the apis
     */
    RobotApi getRobot();

    /**
     * Halt the robot
     */
    void haltRobot();

    /**
     * Moves the robot
     *
     * @param direction the robot direction (DEG)
     * @param speed     the robot speed (pps)
     */
    void moveRobot(int direction, int speed);

    /**
     * Move the sensor
     *
     * @param direction the sensor direction (DEG)
     */
    void moveSensor(int direction);

    Completable readShutdown();

    /**
     * Registers the consumer of inference event
     *
     * @param callback the callback
     */
    void setOnInference(Consumer<RobotStatus> callback);

    /**
     * Shutdowns the controller
     */
    void shutdown();

    /**
     * Starts the controller
     */
    void start();
}
