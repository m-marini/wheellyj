/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.Closeable;
import java.io.File;

/**
 * API interface for controlling and managing the robot behaviour.
 * Provides crucial methods for connection handling, movement control,
 * sensor exploration, and status monitoring.
 */
public interface RobotApi extends Closeable, WithErrorFlowable,
        WithWheellyMessageCallback, WithCameraCallback {

    /**
     * Initialises and returns a robot API instance from a JSON configuration document.
     *
     * @param config  the JSON configuration document
     * @param locator the configuration locator utilised to resolve specific paths
     * @return the initialised {@link RobotApi} instance
     */
    static RobotApi fromConfig(JsonNode config, Locator locator) {
        return Utils.createObject(config, locator, new Object[0], new Class[0]);
    }

    /**
     * Initialises and returns a robot API instance from a specified configuration file.
     *
     * @param file the configuration file
     * @return the initialised {@link RobotApi} instance
     * @throws Throwable if any error occurs whilst reading the file or instantiating the object
     */
    static RobotApi fromFile(File file) throws Throwable {
        return Utils.createObject(file, new Object[0], new Class[0]);
    }

    /**
     * Establishes a connection with the robot.
     */
    void connect();

    /**
     * Halts the robot immediately.
     *
     * @return a {@link Single} emitting {@code true} if the robot successfully halted, {@code false} otherwise
     */
    Single<Boolean> halt();

    /**
     * Checks whether the robot is currently halted.
     *
     * @return {@code true} if the robot is halted, {@code false} otherwise
     */
    boolean isHalt();

    /**
     * Moves the robot to the specified coordinates whilst managing forward or backward orientation.
     *
     * @param frontMove {@code true} to move forward, {@code false} to move backward
     * @param location  the target location coordinates (expressed in degrees)
     * @return a {@link Single} emitting {@code true} if the move command was successful, {@code false} otherwise
     */
    Single<Boolean> move(boolean frontMove, Point2D location);

    /**
     * Returns a continuous stream monitoring the robot line status.
     *
     * @return a {@link Flowable} emitting real-time updates of {@link RobotStatusApi}
     */
    Flowable<RobotStatusApi> readRobotStatus();

    /**
     * Reconnects to the robot, resetting the current connection state.
     */
    void reconnect();

    /**
     * Returns the technical specifications and data parameters of the robot.
     *
     * @return the {@link RobotSpec} instance containing robot specifications
     */
    RobotSpec robotSpec();

    /**
     * Returns the current internal local time of the robot.
     *
     * @return the robot local time in milliseconds
     */
    long robotTime();

    /**
     * Rotates the robot to the given absolute direction.
     *
     * @param dir the target direction angle in degrees (DEG)
     * @return a {@link Single} emitting {@code true} if the rotation command was successful, {@code false} otherwise
     */
    Single<Boolean> rotate(int dir);

    /**
     * Moves the sensor to the specified direction to scan the surrounding environment.
     *
     * @param direction the target sensor direction angle in degrees (DEG)
     * @return a {@link Single} emitting {@code true} if the scan command was successful, {@code false} otherwise
     */
    Single<Boolean> scan(int direction);

    /**
     * Returns the execution speed or power factor of the simulated environment.
     *
     * @return the simulation speed multiplier as a double
     */
    double simulationSpeed();

    /**
     * Tracks a specific target point using the robot sensor alignment.
     *
     * @param frontTrack {@code true} to track from the front, {@code false} to track from the rear
     * @param target     the target coordinates to track
     * @return a {@link Single} emitting {@code true} if the tracking command was successful, {@code false} otherwise
     */
    Single<Boolean> track(boolean frontTrack, Point2D target);
}
