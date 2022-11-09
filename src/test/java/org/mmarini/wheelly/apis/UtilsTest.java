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

package org.mmarini.wheelly.apis;

import org.jbox2d.common.Vec2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.ArgumentsGenerator.*;
import static org.mmarini.wheelly.apis.Utils.direction;
import static org.mmarini.wheelly.apis.Utils.normalizeAngle;

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

    static Stream<Arguments> normalAngleSet() {
        return Stream.of(
                Arguments.of(0D, 0D),
                Arguments.of(PI, -PI),
                Arguments.of(-PI, -PI),
                Arguments.of(PI * 3 / 2, -PI / 2),
                Arguments.of(-PI * 3 / 2, PI / 2),
                Arguments.of(PI * 5 / 2, PI / 2),
                Arguments.of(-PI * 5 / 2, -PI / 2)
        );
    }

    static Stream<Arguments> vect2dArgs() {
        return createStream(1234,
                uniform(-MAX_COORD, MAX_COORD),
                uniform(-MAX_COORD, MAX_COORD)
        );
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0",
            "0.5,0.5",
            "1,1",
            "2,1",
            "-0.5,-0.5",
            "-1,-1",
            "-2,-1"
    })
    void clipFloat(float value, float expected) {
        assertEquals(expected, Utils.clip(value, -1F, 1F));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0,-1,1,-10,10",
            "5,0.5,-1,1,-10,10",
            "10,1,-1,1,-10,10",
            "20,2,-1,1,-10,10",
            "-5,-0.5,-1,1,-10,10",
            "-10,-1,-1,1,-10,10",
            "-20,-2,-1,1,-10,10"
    })
    void linear(double expected, double value, double minX, double maxX, double minY, double maxY) {
        assertEquals(expected, Utils.linear(value, minX, maxX, minY, maxY));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0,-1,1,-10,10",
            "5,0.5,-1,1,-10,10",
            "10,1,-1,1,-10,10",
            "20,2,-1,1,-10,10",
            "-5,-0.5,-1,1,-10,10",
            "-10,-1,-1,1,-10,10",
            "-20,-2,-1,1,-10,10"
    })
    void linearFloat(float expected, float value, float minX, float maxX, float minY, float maxY) {
        assertEquals(expected, Utils.linear(value, minX, maxX, minY, maxY));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0",
            "179,179",
            "-179,-179",
            "450,90",
            "-450,-90",
            "270,-90",
            "-270,90",
    })
    void normalizeDegAngle(int angle, int expected) {
        assertEquals(expected, Utils.normalizeDegAngle(angle));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0",
            "179,179",
            "-179,-179",
            "450,90",
            "-450,-90",
            "270,-90",
            "-270,90",
    })
    void normalizeDegAngleDouble(double angle, double expected) {
        assertEquals(expected, Utils.normalizeDegAngle(angle));
    }

    @Test
    void regex() {
        String className = Utils.class.getCanonicalName();
        String method = "method";
        String string = className + "." + method;
        String regex = "^([a-zA-Z_]\\w*\\.)+([a-zA-Z_]\\w*)$";
        Matcher m = Pattern.compile(regex).matcher(string);
        String className1 = string.substring(0, string.length() - method.length() - 1);

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

        assertThat(normalizeAngle(dir - rad), closeTo(0, 1e-2));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,1,0",
            "1,1,45",
            "1,0,90",
            "1,-1,135",
            "0,-1,180",
            "-1,1,-45",
            "-1,0,-90",
            "-1,-1,-135",
    })
    void testDirection1(double x1, double y1, int deg) {
        Point2D offset = new Point2D.Double();
        Point2D location = new Point2D.Double(x1, y1);

        double dir = toDegrees(direction(offset, location));

        assertThat(dir, closeTo(deg, 1e-2));
    }

    @ParameterizedTest
    @MethodSource("normalAngleSet")
    void testNormalizeAngle(double angle, double expected) {
        assertEquals(expected, Utils.normalizeAngle(angle));
    }

    @ParameterizedTest
    @MethodSource("vect2dArgs")
    void vect2d(double x, double y) {
        Vec2 expected = new Vec2((float) x, (float) y);
        assertEquals(expected, Utils.vec2((float) x, (float) y));
        assertEquals(expected, Utils.vec2(new float[]{(float) x, (float) y}));
    }
}