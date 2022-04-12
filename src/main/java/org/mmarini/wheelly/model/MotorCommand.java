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

import org.mmarini.Tuple2;

import java.util.StringJoiner;

/**
 *
 */
public class MotorCommand {
    /**
     * @param power
     */
    public static MotorCommand create(Tuple2<Double, Double> power) {
        return create(power._1, power._2);
    }

    /**
     * @param leftPower
     * @param rightPower
     */
    public static MotorCommand create(double leftPower, double rightPower) {
        return new MotorCommand(leftPower, rightPower);
    }

    public final double leftPower;
    public final double rightPower;

    /**
     * @param leftPower
     * @param rightPower
     */
    protected MotorCommand(double leftPower, double rightPower) {
        this.leftPower = leftPower;
        this.rightPower = rightPower;
    }

    /**
     *
     */
    public double getLeftPower() {
        return leftPower;
    }

    /**
     * @param leftPower
     */
    public MotorCommand setLeftPower(double leftPower) {
        return new MotorCommand(leftPower, rightPower);
    }

    /**
     *
     */
    public double getRightPower() {
        return rightPower;
    }

    /**
     * @param rightPower
     */
    public MotorCommand setRightPower(double rightPower) {
        return new MotorCommand(leftPower, rightPower);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MotorCommand.class.getSimpleName() + "[", "]")
                .add("leftPower=" + leftPower)
                .add("rightPower=" + rightPower)
                .toString();
    }
}
