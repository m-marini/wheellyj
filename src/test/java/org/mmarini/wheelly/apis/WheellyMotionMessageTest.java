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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.RobotSpec.pulses2Location;
import static org.mmarini.wheelly.apis.Utils.MM;

class WheellyMotionMessageTest {

    public static final long SIM_TIME = 1234L;

    @ParameterizedTest
    @CsvSource({
            "'407393,1.5,-13.9,-6,12.6,-34.2,0,3,99,0,20,-20,62,-14,0.0,0.0', 1.5,-13.9, -6, 12.6,-34.2, 0, 3, 99, 20,-20, 62,-14, 0,0",
            "'4321,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0', 0,0, 0, 0,0, 0, 0, 0, 0,0, 0,0, 0,0",

            // location
            "'4321,123.4,-234.5,0,0,0,0,0,0,0,0,0,0,0,0,0', 123.4,-234.5, 0, 0,0, 0, 0, 0, 0,0, 0,0, 0,0",
            "'4321,-123.4,234.5,0,0,0,0,0,0,0,0,0,0,0,0,0', -123.4,234.5, 0, 0,0, 0, 0, 0, 0,0, 0,0, 0,0",

            // yaw
            "'4321,0,0,-135,0,0,0,0,0,0,0,0,0,0,0,0', 0,0, -135, 0,0, 0, 0, 0, 0,0, 0,0, 0,0",
            "'4321,0,0,135,0,0,0,0,0,0,0,0,0,0,0,0',  0,0, 135, 0,0, 0, 0, 0, 0,0, 0,0, 0,0",

            // pps
            "'4321,0,0,0,123.4,-234.5,0,0,0,0,0,0,0,0,0,0', 0,0, 0, 123.4,-234.5, 0, 0, 0, 0,0, 0,0, 0,0",
            "'4321,0,0,0,-123.4,234.5,0,0,0,0,0,0,0,0,0,0', 0,0, 0, -123.4,234.5, 0, 0, 0, 0,0, 0,0, 0,0",

            // mpu error
            "'4321,0,0,0,0,0,1,0,0,0,0,0,0,0,0,0', 0,0, 0, 0,0, 1, 0, 0, 0,0, 0,0, 0,0",

            // state
            "'4321,0,0,0,0,0,0,1,0,0,0,0,0,0,0,0', 0,0, 0, 0,0, 0, 1, 0, 0,0, 0,0, 0,0",

            // targetDeg
            "'4321,0,0,0,0,0,0,0,-135,0,0,0,0,0,0,0', 0,0, 0, 0,0, 0, 0, -135, 0,0, 0,0, 0,0",
            "'4321,0,0,0,0,0,0,0,135,0,0,0,0,0,0,0', 0,0, 0, 0,0, 0, 0, 135, 0,0, 0,0, 0,0",

            // target pps
            "'4321,0,0,0,0,0,0,0,-0,0,123,-234,0,0,0,0', 0,0, 0, 0,0, 0, 0, 0, 123,-234, 0,0, 0,0",
            "'4321,0,0,0,0,0,0,0,-0,0,-123,234,0,0,0,0', 0,0, 0, 0,0, 0, 0, 0, -123,234, 0,0, 0,0",

            // pwm
            "'4321,0,0,0,0,0,0,0,-0,0,0,0,123,-234,0,0', 0,0, 0, 0,0, 0, 0, 0, 0,0, 123,-234, 0,0",
            "'4321,0,0,0,0,0,0,0,-0,0,0,0,-123,234,0,0', 0,0, 0, 0,0, 0, 0, 0, 0,0, -123,234, 0,0",

            // target
            "'4321,0,0,0,0,0,0,0,-0,0,0,0,0,0,123.4,-234.5', 0,0, 0, 0,0, 0, 0, 0, 0,0, 0,0, 123.4,-234.5",
            "'4321,0,0,0,0,0,0,0,-0,0,0,0,0,0,-123.4,234.5', 0,0, 0, 0,0, 0, 0, 0, 0,0, 0,0, -123.4,234.5",
    })
    void testParse(String arg, double x, double y,
                   int yawDeg,
                   double leftPps, double rightPps,
                   int mpuError, int state, int targetDeg,
                   int leftTargetPps, int rightTargetPps,
                   int leftPwm, int rightPwm, double xTarget, double yTarget) {
        // [sampleTime] [headDirectionDeg] [distance (mm)] [rearDistance (mm)] [xLocation] [yLocation] [yaw]
        WheellyMotionMessage m = WheellyMotionMessage.parse(SIM_TIME, arg);

        assertNotNull(m);
        assertEquals(SIM_TIME, m.time());
        assertThat(m.xPulses(), closeTo(x, 0.1));
        assertThat(m.yPulses(), closeTo(y, 0.1));
        assertEquals(yawDeg, m.directionDeg());
        assertThat(m.leftPps(), closeTo(leftPps, 0.1));
        assertThat(m.rightPps(), closeTo(rightPps, 0.1));
        assertEquals(mpuError, m.imuFailure());
        assertEquals(state, m.status().ordinal());
        assertEquals(targetDeg, m.targetDeg());
        assertEquals(leftTargetPps, m.leftTargetPps());
        assertEquals(rightTargetPps, m.rightTargetPps());
        assertEquals(leftPwm, m.leftPower());
        assertEquals(rightPwm, m.rightPower());
        assertThat(m.xTarget(), closeTo(xTarget, 0.1));
        assertThat(m.yTarget(), closeTo(yTarget, 0.1));

        assertEquals(yawDeg, m.direction().toIntDeg());
        assertEquals(targetDeg, m.targetDir().toIntDeg());
        assertThat(m.location(), pointCloseTo(pulses2Location(x, y), MM));
        assertThat(m.target(), pointCloseTo(pulses2Location(xTarget, yTarget), MM));
    }
}