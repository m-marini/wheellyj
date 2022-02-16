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

package org.mmarini.wheelly.swing;

import org.mmarini.wheelly.model.RemoteClock;

import java.util.Optional;
import java.util.StringJoiner;

import static java.util.Objects.requireNonNull;


/**
 *
 */
public class MotorCommand {
    private static final MotorCommand emptyCommand = new MotorCommand(null, 0, 0);

    /**
     *
     */
    public static MotorCommand empty() {
        return emptyCommand;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MotorCommand.class.getSimpleName() + "[", "]")
                .add("left=" + left)
                .add("right=" + right)
                .add("clock=" + clock)
                .toString();
    }

    public final RemoteClock clock;
    public final int left;
    public final int right;

    /**
     * @param clock the remote clock
     * @param left  the left speed
     * @param right the right speed
     */
    protected MotorCommand(RemoteClock clock, int left, int right) {
        this.clock = clock;
        this.left = left;
        this.right = right;
    }

    /**
     * @param clock the remote clock
     */
    public MotorCommand setClock(RemoteClock clock) {
        requireNonNull(clock);
        return new MotorCommand(clock, left, right);
    }

    /**
     * Returns true if any motor is running
     */
    public boolean isRunning() {
        return left != 0 || right != 0;
    }

    /**
     * @param left the left speed
     */
    public MotorCommand setLeft(int left) {
        return new MotorCommand(clock, left, right);
    }

    /**
     * @param right the right speed
     */
    public MotorCommand setRight(int right) {
        return new MotorCommand(clock, left, right);
    }
}
