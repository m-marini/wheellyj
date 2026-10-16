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

package org.mmarini.wheelly.envs;

import org.mmarini.wheelly.robots.RobotStatus;

/**
 * Creates and updates the radar maps
 */
public interface RadarModeller {

    /**
     * Returns cleans up the map for timeout
     *
     * @param time the simulation markerTime instant
     */
    RadarMap clean(RadarMap radarMap, long time);

    /**
     * Returns the empty radar map
     */
    default RadarMap empty() {
        return RadarMap.empty(topology());
    }

    /**
     * Returns the topology
     */
    GridTopology topology();

    /**
     * Returns the radar map updated with the radar status.
     * It uses the localTime and signals from robot to update the status of radar map
     *
     * @param map    the radar map
     * @param status the robot status
     */
    RadarMap update(RadarMap map, RobotStatus status);
}