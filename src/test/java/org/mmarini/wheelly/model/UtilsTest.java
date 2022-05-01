/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.model.Utils.direction;
import static org.mmarini.wheelly.model.Utils.normalizeAngle;

class UtilsTest {

    static final double MAX_COORD = 3d;
    static double MIN_DIST = 0.1;
    static double MAX_DIST = 3;

    static Stream<Arguments> directionArgs() {
        return createStream(1234,
                uniform(-MAX_COORD, MAX_COORD),
                uniform(-MAX_COORD, MAX_COORD),
                exponential(MIN_DIST, MAX_DIST),
                uniform(-180, 179)
        );
    }

    @Test
    void regex() {
        String className = Utils.class.getCanonicalName();
        String method = "method";
        String string = className + "." + method;
        String regex = "^([a-zA-Z_]\\w*\\.)+([a-zA-Z_]\\w*)$";
        Matcher m = Pattern.compile(regex).matcher(string);
        String className1 = string.substring(0, string.length() - method.length()-1);

        assertTrue(m.matches());
        assertThat(m.groupCount(), equalTo(2));
        assertThat(m.group(2), equalTo(method));
        assertThat(className1, equalTo(className));
    }

    @ParameterizedTest
    @MethodSource("directionArgs")
    void testDirection(double x, double y, double dist, int deg) {
        Point2D offset = new Point2D.Double(x, y);
        double rad = toRadians(deg);
        Point2D location = new Point2D.Double(
                x + dist * sin(rad),
                y + dist * cos(rad)
        );

        double dir = direction(offset, location);

        assertThat(normalizeAngle(dir - rad), closeTo(0, 1e-3));
    }
}