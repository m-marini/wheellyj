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
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;

import java.io.Closeable;
import java.io.IOException;

/**
 * API Interface for robot
 */
public interface RobotApi extends Closeable, WithStatusCallback {
    int MAX_PPS = 32;

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
     * Moves robot to direction at speed
     *
     * @param dir   the direction in DEG
     * @param speed the speed in pps
     * @throws IOException in case of error
     */
    void move(int dir, int speed) throws IOException;

    /**
     * Resets the robot
     *
     * @throws IOException in case of error
     */
    void reset() throws IOException;

    /**
     * Moves the sensor to a direction
     *
     * @param dir the direction in DEG
     * @throws IOException in case of error
     */
    void scan(int dir) throws IOException;

    /**
     * Advances time by a time interval
     *
     * @param dt the interval in millis
     * @throws IOException in case of error
     */
    void tick(long dt) throws IOException;
}
