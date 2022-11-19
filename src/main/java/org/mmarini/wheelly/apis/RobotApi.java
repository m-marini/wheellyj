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

import java.io.Closeable;

/**
 * API Interface for robot
 */
public interface RobotApi extends Closeable {

    /**
     * Returns the robot status
     */
    WheellyStatus getStatus();


    /**
     * Halts the robot
     */
    void halt();

    /**
     * Moves robot to direction at speed
     *
     * @param dir   the directino in DEG
     * @param speed the speed in -1, 1 range
     */
    void move(int dir, float speed);

    /**
     * Resets the robot
     */
    void reset();

    /**
     * Moves the sensor to a direction
     *
     * @param dir the direction in DEG
     */
    void scan(int dir);

    /**
     * Starts the robot interface
     */
    void start();

    /**
     * Advances time by a time interval
     *
     * @param dt the interval in millis
     */
    void tick(long dt);

}
