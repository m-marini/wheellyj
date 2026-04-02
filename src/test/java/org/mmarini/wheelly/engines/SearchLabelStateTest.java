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

package org.mmarini.wheelly.engines;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mmarini.Matchers.pointCloseTo;
import static org.mmarini.wheelly.apis.Utils.MM;
import static org.mmarini.wheelly.engines.AbstractSearchAndMoveState.*;
import static org.mmarini.wheelly.engines.TimeOutState.DEFAULT_TIMEOUT;

class SearchLabelStateTest {

    public static final double TARGET_DISTANCE = 0.4;

    private SearchLabelState state;

    @BeforeEach
    void setUp() {
        this.state = SearchLabelState.create("id", DEFAULT_TIMEOUT, Integer.MAX_VALUE, 3, DEFAULT_MAX_SEARCH_TIME,
                TARGET_DISTANCE, DEFAULT_GROWTH_DISTANCE, 1234, DEFAULT_SAFETY_DISTANCE, null, null, null);
    }

    //@ParameterizedTest(name = "[{index}]")
    //@MethodSource("dataTest")
    @Test
    void testInit() {
        String mapText = """
                .........
                ....o....
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                .+++++++.
                .+++^+++.
                .+++++++.
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ..+++++..
                ....A....
                .........
                """;
        int robotDeg = 90;
        // Given a robot status with both sensors not clear
        ProcessorContextBuilder builder = new ProcessorContextBuilder()
                .robotDirection(robotDeg)
                .applyMap(mapText);
        ProcessorContextApi ctx = builder.build();
        Point2D marker = ctx.worldModel().markers().get("A").location();
        assertThat(marker, pointCloseTo(0, -1, MM));

        // When ...
        state.init(ctx);
        state.entry(ctx);

        // Then ...
        List<Point2D> path = state.path();
        assertNotNull(path);
        assertThat(path, hasSize(greaterThanOrEqualTo(1)));
        Point2D target = path.getLast();
        double at = marker.distance(target);
        assertThat(at, lessThanOrEqualTo(TARGET_DISTANCE));
    }
}