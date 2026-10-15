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

class WheellyLidarMessageTest {

    public static final long SIM_TIME = 1234L;

    @ParameterizedTest
    @CsvSource({
            "'4321,0,0,0,0,0,0,0,0,0,0', 0, 0, 0, 0, 0, 0, 0, 0, 0,0",

            "'4321,100,0,0,0,0,0,0,0,0,0',  100,0, 0,0, 0, 0, 0, 0, 0,0",
            "'4321,0,200,0,0,0,0,0,0,0,0',  0,200, 0,0, 0, 0, 0, 0, 0,0",

            "'4321,0,0,10.5,20.5,0,0,0,0,0,0',   0,0, 10.5,20.5,   0, 0, 0, 0, 0,0",
            "'4321,0,0,-10.5,-20.5,0,0,0,0,0,0', 0,0, -10.5,-20.5, 0, 0, 0, 0, 0,0",

            "'4321,0,0,0,0,135,0,0,0,0,0',  0,0, 0,0, 135,  0, 0, 0, 0,0",
            "'4321,0,0,0,0,-135,0,0,0,0,0', 0,0, 0,0, -135, 0, 0, 0, 0,0",

            "'4321,0,0,0,0,0,45,0,0,0,0',  0,0, 0,0, 0, 45, 0, 0, 0,0",
            "'4321,0,0,0,0,0,-45,0,0,0,0', 0,0, 0,0, 0, -45, 0, 0, 0,0",

            "'4321,0,0,0,0,0,0,45,0,0,0',  0,0, 0,0, 0, 0, 45, 0, 0,0",
            "'4321,0,0,0,0,0,0,-45,0,0,0', 0,0, 0,0, 0, 0, -45, 0, 0,0",

            "'4321,0,0,0,0,0,0,0,1,0,0',   0,0, 0,0, 0, 0, 0, 1, 0,0",

            "'4321,0,0,0,0,0,0,0,0,123.4,-234.5',   0,0, 0,0, 0, 0, 0, 0, 123.4,-234.5",
            "'4321,0,0,0,0,0,0,0,0,-123.4,234.5',   0,0, 0,0, 0, 0, 0, 0, -123.4,234.5",
    })
    void testParse(String arg, int frontDistance, int rearDistance, double x, double y, int yaw,
                   int headDeg, int headTargetDeg, int state, double xTarget, double yTarget) {
        // [sampleTime] [headDirectionDeg] [distance (mm)] [rearDistance (mm)] [xLocation] [yLocation] [yaw]
        WheellyLidarMessage m = WheellyLidarMessage.parse(SIM_TIME, arg);

        assertNotNull(m);
        assertEquals(SIM_TIME, m.time());
        assertEquals(headDeg, m.headDirectionDeg());
        assertEquals(frontDistance, m.frontDistance());
        assertEquals(rearDistance, m.rearDistance());
        assertEquals(x, m.xPulses());
        assertEquals(y, m.yPulses());
        assertEquals(yaw, m.robotYawDeg());
        assertEquals(headDeg, m.headDirectionDeg());
        assertEquals(headTargetDeg, m.headTargetDeg());
        assertEquals(state, m.trackingState().ordinal());
        assertThat(m.xTarget(), closeTo(xTarget, MM));
        assertThat(m.yTarget(), closeTo(yTarget, MM));

        assertEquals(headDeg, m.headDirection().toIntDeg());
        assertEquals(yaw, m.robotYaw().toIntDeg());
        assertEquals(headTargetDeg, m.headTarget().toIntDeg());
        assertThat(m.robotLocation(), pointCloseTo(pulses2Location(x, y), MM));
        assertThat(m.target(), pointCloseTo(pulses2Location(xTarget, yTarget), MM));
    }
}