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

package org.mmarini.wheelly.engines.deepl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.toRadians;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class FullFeaturesSignalEncoderTest {

    @ParameterizedTest
    @CsvSource(value = {
            "-180,0",
            "-187,0",
            "-173,0",
            "-172,1",
            "-158,1",
            "-157,2",
            "-8,11",
            "-7,12",
            "7,12",
            "8,13",
            "172,23",
            "173,0",
            "179,0",
    })
    void encodeDirection(int dir, int expected) {
        assertThat(FullFeaturesSignalEncoder.encodeDirection(dir), equalTo(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "-6,0",
            "-5,0",
            "-4.8,1",
            "0,25",
            "4.8,49",
            "5,50",
            "6,50",
    })
    void encodeLinear(double distance, int expected) {
        assertThat(FullFeaturesSignalEncoder.encodeLinear(distance), equalTo(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "-6,-6,0",
            "-5,-5,0",
            "-4.8,-5,1",
            "0,-5,25",
            "4.8,-5,49",
            "5,-5,50",
            "6,-5,50",
            "0,0,1300",
            "5,5,2600",
            "6,6,2600",
    })
    void encodeLocation(double x, double y, int expected) {
        assertThat(FullFeaturesSignalEncoder.encodeLocation(new Point2D.Double(x, y)), equalTo(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0,0.2,0,1300,1301",
            "0,0,0,0.2,1300,1351",
    })
    void encodePoints(double x0, double y0, double x1, double y1, int exp1, int exp2) {
        Stream<Point2D> points = Stream.of(
                new Point2D.Double(x0, y0),
                new Point2D.Double(x1, y1)
        );
        List<Integer> result = FullFeaturesSignalEncoder.encodePoints(points).boxed().collect(Collectors.toList());
        assertThat(result, contains(exp1, exp2));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "0,0",
            "0.099,0",
            "0.1,1",
            "0.199,1",
            "2.901,29",
            "3,29",
    })
    void encodeDistance(double distance, int expected) {
        assertThat(FullFeaturesSignalEncoder.encodeDistance(distance), equalTo(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "-90,0",
            "-71,0",
            "-70,1",
            "-51,1",
            "-50,2",
            "-11,3",
            "-10,4",
            "0,4",
            "9,4",
            "10,5",
            "69,7",
            "70,8",
            "90,8",
    })
    void encodeSensor(int dir, int expected) {
        assertThat(FullFeaturesSignalEncoder.encodeSensor(dir), equalTo(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "8,0",
            "8.99,0",
            "9.01,1",
            "9.99,1",
            "10.01,2",
            "11.99,3",
            "12.01,4",
    })
    void encodeVoltage(double distance, int expected) {
        assertThat(FullFeaturesSignalEncoder.encodeVoltage(distance), equalTo(expected));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "10,10, 0, 10,10, 0,0",
            "10,10, 0, 10.2,10, 0.2,0",
            "10,10, 0, 10,10.2, 0,0.2",
            "10,10, 90, 10,10, 0,0",
            "10,10, 90, 10.2,10, 0,-0.2",
            "10,10, 90, 10,10.2, 0.2,0",
    })
    void transform(double ox, double oy, int thetaDeg, double x0, double y0, double expX, double expY) {
        Stream<Point2D> points = Stream.of(
                new Point2D.Double(x0, y0));
        Point2D offset = new Point2D.Double(ox, oy);
        double thetaRad = toRadians(thetaDeg);

        List<Point2D> result = FullFeaturesSignalEncoder.transform(points, offset, thetaRad).collect(Collectors.toList());

        assertThat(result, contains(
                allOf(
                        hasProperty("x", closeTo(expX, 1e-3)),
                        hasProperty("y", closeTo(expY, 1e-3))
                )));
    }
}