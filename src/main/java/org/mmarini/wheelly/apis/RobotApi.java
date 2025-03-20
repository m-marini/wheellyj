/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apis;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * API Interface for robot
 */
public interface RobotApi extends Closeable, WithStatusCallback, WithCameraCallback {
    int MAX_PPS = 60;

    /**
     * Returns the robot api from configuration
     *
     * @param config  the json document
     * @param locator the configuration locator
     */
    static RobotApi fromConfig(JsonNode config, Locator locator) {
        return Utils.createObject(config, locator, new Object[0], new Class[0]);
    }

    /**
     * Returns the robot api from configuration
     *
     * @param file the configuration file
     * @throws IOException in case of error
     */
    static RobotApi fromFile(File file) throws IOException {
        return Utils.createObject(file, new Object[0], new Class[0]);
    }

    RobotSpec robotSpec();

    /**
     * Configures the robot
     *
     * @throws IOException in case of error
     */
    void configure() throws IOException;

    /**
     * Connects the robot
     *
     * @throws IOException in case of error
     */
    void connect() throws IOException;

    /**
     * Halts the robot
     *
     * @throws IOException in case of error
     */
    void halt() throws IOException;

    /**
     * Returns true if the robot is halted
     */
    boolean isHalt();

    /**
     * Moves robot to direction at speed
     *
     * @param dir   the direction
     * @param speed the speed in pps
     * @throws IOException in case of error
     */
    void move(Complex dir, int speed) throws IOException;

    /**
     * Moves the sensor to a direction
     *
     * @param dir the direction
     * @throws IOException in case of error
     */
    void scan(Complex dir) throws IOException;

    /**
     * Returns the robot localTime
     */
    long simulationTime();

    /**
     * Advances localTime by a localTime interval
     *
     * @param dt the interval in millis
     * @throws IOException in case of error
     */
    void tick(long dt) throws IOException;
}
