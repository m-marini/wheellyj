/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.model;

/**
 * The properties related to an obstacle and a proxy sample
 */
public class ObstacleSampleProperties {
    /**
     * The obstacle
     */
    public final Obstacle obstacle;
    /**
     * The direction of obstacle relative to the proximity sensor
     */
    public final double obstacleSensorDirection;
    /**
     * The distance from sensor
     */
    public final double robotObstacleDistance;
    /**
     * The distance from proximity echo sample if any
     */
    public final double sampleObstacleDistance;

    /**
     * Creates the properties
     *
     * @param obstacle                the obstacle
     * @param robotObstacleDistance   the distance from robot
     * @param sampleObstacleDistance  the distance from proximity echo sample if any
     * @param obstacleSensorDirection the direction of obstacle relative to the proximity sensor
     */
    public ObstacleSampleProperties(Obstacle obstacle, double robotObstacleDistance, double sampleObstacleDistance, double obstacleSensorDirection) {
        this.obstacle = obstacle;
        this.robotObstacleDistance = robotObstacleDistance;
        this.sampleObstacleDistance = sampleObstacleDistance;
        this.obstacleSensorDirection = obstacleSensorDirection;
    }

    /**
     * Returns the obstacle
     */
    public Obstacle getObstacle() {
        return obstacle;
    }

    /**
     * Returns the direction of obstacle relative to the proximity sensor
     */
    public double getObstacleSensorDirection() {
        return obstacleSensorDirection;
    }

    /**
     * Returns the distance from the robot
     */
    public double getRobotObstacleDistance() {
        return robotObstacleDistance;
    }

    /**
     * Returns the distance from proximity echo sample if any
     */
    public double getSampleObstacleDistance() {
        return sampleObstacleDistance;
    }
}
