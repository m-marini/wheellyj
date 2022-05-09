/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mmarini.wheelly.model.GridScannerMap.THRESHOLD_DISTANCE;

/**
 * See tests enumeration figure
 */
public class GridScannerMapNoEchoTest implements GridScannerTest {

    static Stream<Arguments> test1ArgSet() {
        return GridScannerTest.noEchoTestSet(GridScannerTest.centralDegAngleGen())
                .filter(TestSet::isCentralArea)
                .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("test1ArgSet")
    void test1(TestSet args) {
        List<Obstacle> obstacles = List.of(args.obstacle);
        GridScannerMap map = GridScannerMap.create(obstacles, THRESHOLD_DISTANCE);

        /*
        When create the new map from sample
         */
        Point2D obstacleLocation = args.obstacle.location;
        long timestamp = args.proxySample.time(TimeUnit.MILLISECONDS);
        List<Obstacle> result = map.createObstacles(args.proxySample).collect(Collectors.toList());

        assertThat(result, hasSize(1));

        assertThat(result, hasItem(
                allOf(
                        hasProperty("location", equalTo(obstacleLocation)),
                        hasProperty("likelihood", closeTo(0, 1e-3)),
                        hasProperty("timestamp", equalTo(timestamp))
                )));
    }
}