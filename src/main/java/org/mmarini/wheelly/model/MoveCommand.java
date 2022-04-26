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
public class MoveCommand implements MotionComand {
    /**
     * Returns move command
     *
     * @param direction the direction DEG
     * @param speed     the speed in range -1, 1
     */
    public static MoveCommand create(int direction, double speed) {
        return new MoveCommand(direction, speed);
    }

    public final int direction;
    public final double speed;

    /**
     * Creates move command
     *
     * @param direction the direction DEG
     * @param speed     the speed in range -1, 1
     */
    protected MoveCommand(int direction, double speed) {
        this.direction = direction;
        this.speed = speed;
    }

    @Override
    public String getString() {
        return "mv " + direction + " " + speed;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MoveCommand.class.getSimpleName() + "[", "]")
                .add("direction=" + direction)
                .add("speed=" + speed)
                .toString();
    }
}
