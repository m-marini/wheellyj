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

import static java.lang.Math.min;
import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;


/**
 *
 */
public class MotorCommand {
    private static final MotorCommand emptyCommand = new MotorCommand(null, Direction.NONE, 0f, 0);
    private static final int MAX_PULSE_COUNT = 4;

    /**
     *
     */
    public static MotorCommand empty() {
        return emptyCommand;
    }

    public final RemoteClock clock;
    public final Direction direction;
    public final float speed;
    public final long tick;

    /**
     * @param clock     the remote clock
     * @param direction the direction
     * @param speed     the speed
     * @param tick      the tick clock counter
     */
    protected MotorCommand(RemoteClock clock, Direction direction, float speed, long tick) {
        this.clock = clock;
        this.direction = requireNonNull(direction);
        this.speed = speed;
        this.tick = tick;
    }

    /**
     *
     */
    public boolean isPulse() {
        int n = round(min(speed, 1f) * MAX_PULSE_COUNT);
        return (tick % MAX_PULSE_COUNT) < n;
    }

    /**
     *
     */
    public float getSpeed() {
        return speed;
    }

    /**
     * @param speed the speed
     */
    public MotorCommand setSpeed(float speed) {
        return new MotorCommand(clock, direction, speed, tick);
    }

    /**
     *
     */
    public Optional<RemoteClock> getClock() {
        return Optional.ofNullable(clock);
    }

    /**
     * @param clock the remote clock
     */
    public MotorCommand setClock(RemoteClock clock) {
        requireNonNull(clock);
        return new MotorCommand(clock, direction, speed, tick);
    }

    /**
     *
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * @param direction the direction
     */
    public MotorCommand setDirection(Direction direction) {
        requireNonNull(direction);
        return new MotorCommand(clock, direction, speed, tick);
    }

    /**
     *
     */
    public long getTick() {
        return tick;
    }

    /**
     * @param tick the clock tick
     */
    public MotorCommand setTick(long tick) {
        return new MotorCommand(clock, direction, speed, tick);
    }

    /**
     *
     */
    public int getLeftSpeed() {
        if (isPulse()) {
            switch (direction) {
                case N:
                case NE:
                case E:
                    return 255;
                case W:
                case S:
                case SW:
                    return -255;
            }
        }
        return 0;
    }

    /**
     *
     */
    public int getRightSpeed() {
        if (isPulse()) {
            switch (direction) {
                case N:
                case NW:
                case W:
                    return 255;
                case E:
                case S:
                case SE:
                    return -255;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", MotorCommand.class.getSimpleName() + "[", "]")
                .add("direction=" + direction)
                .add("speed=" + speed)
                .add("tick=" + tick)
                .add("clock=" + clock)
                .toString();
    }

    public boolean isRunning() {
        return getLeftSpeed() != 0 || getRightSpeed() != 0;
    }

    /**
     *
     */
    public enum Direction {
        NONE,
        N,
        NE,
        E,
        SE,
        S,
        SW,
        W,
        NW
    }
}
