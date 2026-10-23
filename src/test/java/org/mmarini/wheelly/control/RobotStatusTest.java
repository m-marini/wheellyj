/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.utils.Utils.MM;

class RobotStatusTest {

    RobotStatusBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RobotStatusBuilder();
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 1,1, 1,1",
            "0,0, 0, 1,-1, 1,-1",
            "0,0, 0, -1,-1, -1,-1",
            "0,0, 0, -1,1, -1,1",
            /*
                   |       |
                  4|^      |
                ---+--- -4-1---
                  3|2      |
                   |     3 2
             */
            "1,1, 0, 1,1, 0,0",
            "1,1, 0, 1,-1, 0,-2",
            "1,1, 0, -1,-1, -2,-2",
            "1,1, 0, -1,1, -2,0",

            /*
                   |       |
                  4|>      |
                ---+--- ---1-2-
                  3|2      |
                   |       4 3
             */
            "1,1, 90, 1,1, 0,0",
            "1,1, 90, 1,-1, 2,0",
            "1,1, 90, -1,-1, 2,-2",
            "1,1, 90, -1,1, 0,-2",

            /*
                   |       2 3
                  4|v      |
                ---+--- ---1-4-
                  3|2      |
                   |       |
             */
            "1,1, -180, 1,1, 0,0",
            "1,1, -180, 1,-1, 0,2",
            "1,1, -180, -1,-1, 2,2",
            "1,1, -180, -1,1, 2,0",
            /*
                   |     3 4
                  4|<      |
                ---O--- -2-1---
                  3|2      |
                   |       |
             */
            "1,1, -90, 1,1, 0,0",
            "1,1, -90, 1,-1, -2,0",
            "1,1, -90, -1,-1, -2,2",
            "1,1, -90, -1,1, 0,2",
    })
    void testEgocentric(double x, double y, int deg, double px, double py, double expX, double expY) {
        // Given a robot status
        Point2D robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(deg);
        // And a point
        Point2D p = new Point2D.Double(px, py);

        // When translate point to egocentric space
        Point2D ego = builder.build().egocentric(p);

        // Then transleted point should be expected
        assertThat(ego, pointCloseTo(expX, expY, MM));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0, 0, 1,1, 1,1",
            "0,0, 0, 1,-1, 1,-1",
            "0,0, 0, -1,-1, -1,-1",
            "0,0, 0, -1,1, -1,1",
            /*
                   |       4 1
                  4|1      |^
                ---O--- ---3-2-
                  3|2      |
                   |       |
             */
            "1,1, 0, 1,1, 2,2",
            "1,1, 0, 1,-1, 2,0",
            "1,1, 0, -1,-1, 0,0",
            "1,1, 0, -1,1, 0,2,",

            /*
                   |       3 4
                  4|1      |>
                ---O--- ---2-1-
                  3|2      |
                   |       |
             */
            "1,1, 90, 1,1, 2,0",
            "1,1, 90, 1,-1, 0,0",
            "1,1, 90, -1,-1, 0,2",
            "1,1, 90, -1,1, 2,2",

            /*
                   |       2 3
                  4|1      |v
                ---O--- ---1-4-
                  3|2      |
                   |       |
             */
            "1,1, -180, 1,1, 0,0",
            "1,1, -180, 1,-1, 0,2",
            "1,1, -180, -1,-1, 2,2",
            "1,1, -180, -1,1, 2,0",
            /*
                   |       1 2
                  4|1      |<
                ---O--- ---4-3-
                  3|2      |
                   |       |
             */
            "1,1, -90, 1,1, 0,2",
            "1,1, -90, 1,-1, 2,2",
            "1,1, -90, -1,-1, 2,0",
            "1,1, -90, -1,1, 0,0",
    })
    void testWorldPoint(double x, double y, int deg, double px, double py, double expX, double expY) {
        // Given a robot status
        Point2D robotLocation = new Point2D.Double(x, y);
        builder.robotLocation(robotLocation)
                .robotDir(deg);
        // And a point
        Point2D p = new Point2D.Double(px, py);

        // When translate point to egocentric space
        Point2D ego = builder.build().worldPoint(p);

        // Then transleted point should be expected
        assertThat(ego, pointCloseTo(expX, expY, MM));
    }
}