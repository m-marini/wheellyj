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

import java.util.StringJoiner;

/**
 *
 */
public class MotorCommand {
    /**
     * @param leftPower
     * @param rightPower
     */
    public static MotorCommand create(int leftPower, int rightPower) {
        return new MotorCommand(leftPower, rightPower);
    }

    public final int leftPower;
    public final int rightPower;

    /**
     * @param leftPower
     * @param rightPower
     */
    protected MotorCommand(int leftPower, int rightPower) {
        this.leftPower = leftPower;
        this.rightPower = rightPower;
    }

    /**
     *
     */
    public int getLeftPower() {
        return leftPower;
    }

    /**
     * @param leftPower
     */
    public MotorCommand setLeftPower(int leftPower) {
        return new MotorCommand(leftPower, rightPower);
    }

    /**
     *
     */
    public int getRightPower() {
        return rightPower;
    }

    /**
     * @param rightPower
     */
    public MotorCommand setRightPower(int rightPower) {
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