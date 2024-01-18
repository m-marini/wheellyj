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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mmarini.ArgumentsGenerator.createStream;
import static org.mmarini.ArgumentsGenerator.uniform;

class UtilsTest {

    static final double MAX_COORD = 3d;

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
    @MethodSource("vect2dArgs")
    void vect2d(double x, double y) {
        Vec2 expected = new Vec2((float) x, (float) y);
        assertEquals(expected, Utils.vec2((float) x, (float) y));
        assertEquals(expected, Utils.vec2(new float[]{(float) x, (float) y}));
    }
}