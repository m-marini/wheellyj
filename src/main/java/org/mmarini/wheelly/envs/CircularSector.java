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

package org.mmarini.wheelly.envs;

/**
 * Circular sector is a space area that keeps the distance of nearest obstacle in the area and if it has been scanned
 */
public interface CircularSector {

    CircularSector UNKNOWN = new CircularSector() {
        @Override
        public double getDistance() {
            return 0;
        }

        @Override
        public long getTimestamp() {
            return 0;
        }

        @Override
        public boolean hasObstacle() {
            return false;
        }

        @Override
        public boolean isKnown() {
            return false;
        }
    };

    /**
     * Returns a hindered sector with obstacle at specific distance
     *
     * @param timestamp the sector status timastamp
     * @param distance  the obstacle
     */
    static CircularSector create(long timestamp, double distance) {
        return new CircularSector() {
            @Override
            public double getDistance() {
                return distance;
            }

            @Override
            public long getTimestamp() {
                return timestamp;
            }

            @Override
            public boolean hasObstacle() {
                return distance > 0;
            }

            @Override
            public boolean isKnown() {
                return true;
            }
        };
    }

    /**
     * Returns an empty sector
     *
     * @param timestamp the sector status timastamp
     */
    static CircularSector empty(long timestamp) {
        return new CircularSector() {
            @Override
            public double getDistance() {
                return 0;
            }

            @Override
            public long getTimestamp() {
                return timestamp;
            }

            @Override
            public boolean hasObstacle() {
                return false;
            }

            @Override
            public boolean isKnown() {
                return true;
            }
        };
    }

    static CircularSector unknown() {
        return UNKNOWN;
    }

    double getDistance();

    long getTimestamp();

    boolean hasObstacle();

    boolean isKnown();
}
