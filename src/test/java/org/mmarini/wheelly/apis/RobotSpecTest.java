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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.DEFAULT_ROBOT_SPEC;

class RobotSpecTest {

    public static final double MM1 = 1e-3;
    RobotSpec spec;

    @BeforeEach
    void setUp() {
        spec = DEFAULT_ROBOT_SPEC;
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0,   0,45e-3", // 30+16 mm
            "0,0, 0, -90, -15e-3,30e-3",
            "0,0, 0, 90,  15e-3,30e-3",

            "0,0, -90, 0,   -45e-3,0",
            "0,0, -90, -90, -30e-3,-15e-3",
            "0,0, -90, 90,  -30e-3,15e-3",

            "0,0, -180, 0,   0,-45e-3",
            "0,0, -180, -90, 15e-3,-30e-3",
            "0,0, -180, 90,  -15e-3,-30e-3",

            "0,0, 90, 0,   45e-3,0",
            "0,0, 90, -90, 30e-3,15e-3",
            "0,0, 90, 90,  30e-3,-15e-3",
    })
    void testCameraLocation(double xRobot, double yRobot,
                            int robotDirDeg, int sensorDirDeg,
                            double expX, double expY) {
        Point2D.Double robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D location = spec.cameraLocation(robotLocation, Complex.fromDeg(robotDirDeg), Complex.fromDeg(sensorDirDeg));
        assertThat(location, pointCloseTo(expX, expY, MM1));
    }

    @ParameterizedTest(
            name = "[{index}] @({0},{1}) R{2} head {3} DEG"
    )
    @CsvSource({
            "0,0, 0, 0,   0,45e-3", // 30 + 15
            "0,0, 0, -90, -15e-3,30e-3",
            "0,0, 0, 90,  15e-3,30e-3",

            "0,0, -90, 0,   -45e-3,0",
            "0,0, -90, -90, -30e-3,-15e-3",
            "0,0, -90, 90,  -30e-3,15e-3",

            "0,0, -180, 0,   0,-45e-3",
            "0,0, -180, -90, 15e-3,-30e-3",
            "0,0, -180, 90,  -15e-3,-30e-3",

            "0,0, 90, 0,   45e-3,0",
            "0,0, 90, -90, 30e-3,15e-3",
            "0,0, 90, 90,  30e-3,-15e-3",

            "1,2, 0, 0,   1,2.045", // 30 + 15
            "1,2, 0, -90, 0.985,2.030",
            "1,2, 0, 90,  1.015,2.03",

            "1,2, -90, 0,   0.955,2",
            "1,2, -90, -90, 0.97,1.985",
            "1,2, -90, 90,  0.97,2.015",

            "1,2, -180, 0,   1,1.955",
            "1,2, -180, -90, 1.015,1.97",
            "1,2, -180, 90,  0.985,1.97",

            "1,2, 90, 0,   1.045,2",
            "1,2, 90, -90, 1.03,2.015",
            "1,2, 90, 90,  1.03,1.985",
    })
    void testFrontLidarLocation(double xRobot, double yRobot,
                                int robotDirDeg, int sensorDirDeg,
                                double expX, double expY) {
        Point2D.Double robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D location = spec.frontLidarLocation(robotLocation, Complex.fromDeg(robotDirDeg), Complex.fromDeg(sensorDirDeg));
        assertThat(location, pointCloseTo(expX, expY, MM1));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 0,   0,15e-3",
            "0,0, 0, -90, 15e-3,30e-3",
            "0,0, 0, 90,  -15e-3,30e-3",

            "0,0, 180, 0,   0,-15e-3",
            "0,0, 180, -90, -15e-3,-30e-3",
            "0,0, 180, 90,  15e-3,-30e-3",

            "0,0, -90, 0,   -15e-3,0",
            "0,0, -90, -90, -30e-3,15e-3",
            "0,0, -90, 90,  -30e-3,-15e-3",

            "0,0, 90, 0,   15e-3,0",
            "0,0, 90, -90, 30e-3,-15e-3",
            "0,0, 90, 90,  30e-3,15e-3",
    })
    void testRearLidarLocation(double xRobot, double yRobot,
                               int robotDirDeg, int sensorDirDeg,
                               double expX, double expY) {
        Point2D.Double robotLocation = new Point2D.Double(xRobot, yRobot);
        Point2D location = spec.rearLidarLocation(robotLocation, Complex.fromDeg(robotDirDeg), Complex.fromDeg(sensorDirDeg));
        assertThat(location, pointCloseTo(expX, expY, MM1));
    }
}