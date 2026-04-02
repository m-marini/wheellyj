/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.objectives;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.awt.geom.Point2D;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveToLabelTest {

    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 100;

    static WorldModel createState(Point2D robotLocation, int robotDeg, Point2D marker) {
        return new WorldModelBuilder()
                .robotLocation(robotLocation)
                .robotDir(robotDeg)
                .addMarker("A", marker)
                .build();
    }

    public static Stream<Arguments> dataReward() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 17) // robotX
                .uniform(-2.0, 2.0, 17) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(1.0, 2.0, 17) // markerDistance
                .uniform(0, 359) // markerDeg
                .exponential(0.1, 0.3, 17) // move distance
                .uniform(-45, 45) // move direction
                .exponential(0.1, 1, 17) // reward
                .build(NUM_RANDOM_TEST_CASES);
    }

    public static Stream<Arguments> dataReward0() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-2.0, 2.0, 17) // robotX
                .uniform(-2.0, 2.0, 17) // robotY
                .uniform(0, 359) // robotDeg
                .uniform(1.0, 2.0, 17) // markerDistance
                .uniform(0, 359) // markerDeg
                .exponential(0.1, 0.3, 17) // move distance
                .uniform(135, 215) // move direction
                .build(NUM_RANDOM_TEST_CASES);
    }

    @ParameterizedTest(name = "[{index}] robot R{0},{1} R{2} marker {3} m R{4} move {5} m R{6} reward {7}")
    @MethodSource("dataReward")
    void testReward(double robotX, double robotY, int robotDeg, double markerDistance, int markerDeg, double moveDistance, int moveToTargetDeg, double matchReward) {
        // Given two states
        Point2D.Double robotLocation0 = new Point2D.Double(robotX, robotY);
        Point2D marker = Complex.fromDeg(markerDeg).at(robotLocation0, markerDistance);
        WorldModel s0 = createState(robotLocation0, robotDeg, marker);
        Point2D robotLocation1 = Complex.fromDeg(markerDeg + moveToTargetDeg).at(robotLocation0, moveDistance);
        WorldModel s1 = createState(robotLocation1, robotDeg, marker);

        double reward = MoveToLabel.moveToLabel(matchReward).applyAsDouble(s0, RobotCommands.halt(), s1);

        assertEquals(matchReward, reward);
    }

    @ParameterizedTest(name = "[{index}] robot R{0},{1} R{2} marker {3} m R{4} move {5} m R{6}")
    @MethodSource("dataReward0")
    void testReward0(double robotX, double robotY, int robotDeg, double markerDistance, int markerDeg, double moveDistance, int moveToTargetDeg) {
        // Given two states
        Point2D.Double robotLocation0 = new Point2D.Double(robotX, robotY);
        Point2D marker = Complex.fromDeg(markerDeg).at(robotLocation0, markerDistance);
        WorldModel s0 = createState(robotLocation0, robotDeg, marker);
        Point2D robotLocation1 = Complex.fromDeg(markerDeg + moveToTargetDeg).at(robotLocation0, moveDistance);
        WorldModel s1 = createState(robotLocation1, robotDeg, marker);

        double reward = MoveToLabel.moveToLabel(1).applyAsDouble(s0, RobotCommands.halt(), s1);

        assertEquals(0, reward);
    }
}