/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import static java.util.Objects.requireNonNull;

/**
 * Returns the robot specifications
 *
 * @param maxRadarDistance the maximum radar distance (m)
 * @param receptiveAngle   the receptive angle
 * @param contactRadius    the contact radius (m)
 */
public record RobotSpec(double maxRadarDistance, Complex receptiveAngle, double contactRadius) {
    /**
     * Creates the robot specification
     *
     * @param maxRadarDistance the maximum radar distance (m)
     * @param receptiveAngle   the receptive angle
     * @param contactRadius    the contact radius (m)
     */
    public RobotSpec(double maxRadarDistance, Complex receptiveAngle, double contactRadius) {
        this.maxRadarDistance = maxRadarDistance;
        this.receptiveAngle = requireNonNull(receptiveAngle);
        this.contactRadius = contactRadius;
    }
}
