/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Store the possible commands combination halt, move, scan
 */
public class RobotCommands {

    private static final RobotCommands NONE = new RobotCommands(false, 0, false, false, 0, 0);
    private static final RobotCommands HALT = new RobotCommands(false, 0, false, true, 0, 0);
    private static final RobotCommands IDLE = new RobotCommands(true, 0, false, true, 0, 0);

    /**
     * Returns the concatenation of commands
     * The last scan with last move or halt command is returned
     *
     * @param commands the commands
     */
    public static RobotCommands concat(RobotCommands... commands) {
        Optional<RobotCommands> scanCmd = Arrays.stream(commands).filter(RobotCommands::isScan).reduce((a, b) -> b);
        Optional<RobotCommands> movementCmd = Arrays.stream(commands).filter(RobotCommands::isMovement).reduce((a, b) -> b);
        return movementCmd.map(mv ->
                scanCmd.map(sc -> mv.setScan(sc.getScanDirection())).orElse(mv)
        ).orElse(
                scanCmd.orElse(RobotCommands.none()));
    }

    /**
     * Returns the halt command
     */
    public static RobotCommands halt() {
        return HALT;
    }

    /**
     * Returns the only scan command and halt
     *
     * @param direction the scanner direction
     */
    public static RobotCommands haltAndScan(int direction) {
        return new RobotCommands(true, direction, false, true, 0, 0);
    }

    /**
     * Returns the idle command
     */
    public static RobotCommands idle() {
        return IDLE;
    }

    /**
     * Returns the only move command
     *
     * @param direction the move direction (DEG)
     * @param speed     the speed (pps)
     */
    public static RobotCommands move(int direction, int speed) {
        return new RobotCommands(false, 0, true, false, direction, speed);
    }

    /**
     * Returns the move command and front scan
     *
     * @param direction the move direction (DEG)
     * @param speed     the speed (pps)
     */
    public static RobotCommands moveAndFrontScan(int direction, int speed) {
        return new RobotCommands(true, 0, true, false, direction, speed);
    }

    /**
     * Returns the move command and scan
     *
     * @param direction     the move direction (DEG)
     * @param speed         the speed (pps)
     * @param scanDirection the scan direction (DEG)
     */
    public static RobotCommands moveAndScan(int direction, int speed, int scanDirection) {
        return new RobotCommands(true, scanDirection, true, false, direction, speed);
    }

    /**
     * Returns the none command
     */
    public static RobotCommands none() {
        return NONE;
    }

    /**
     * Returns the only scan command
     *
     * @param direction the scanner direction
     */
    public static RobotCommands scan(int direction) {
        return new RobotCommands(true, direction, false, false, 0, 0);
    }

    public final boolean halt;
    public final boolean move;
    public final int moveDirection;
    public final boolean scan;
    public final int scanDirection;
    public final int speed;

    /**
     * Create the command
     *
     * @param scan          true if scan command
     * @param scanDirection the scan direction (DEG)
     * @param move          true if movement command
     * @param halt          true if halt command
     * @param moveDirection the move direction (DEG)
     * @param speed         the speed (pps)
     */
    protected RobotCommands(boolean scan, int scanDirection, boolean move, boolean halt, int moveDirection, int speed) {
        this.halt = halt;
        this.moveDirection = moveDirection;
        this.move = move;
        this.scan = scan;
        this.scanDirection = scanDirection;
        this.speed = speed;
    }

    /**
     * Returns the command with cleared movement
     */
    public RobotCommands clearMovement() {
        return new RobotCommands(scan, scanDirection, false, false, 0, 0);
    }

    /**
     * Returns the command with cleared scan
     */
    public RobotCommands clearScan() {
        return new RobotCommands(false, 0, move, halt, moveDirection, speed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RobotCommands that = (RobotCommands) o;
        return halt == that.halt && moveDirection == that.moveDirection && move == that.move && scan == that.scan && scanDirection == that.scanDirection && speed == that.speed;
    }

    /**
     * Returns the move direction (DEG)
     */
    public int getMoveDirection() {
        return moveDirection;
    }

    /**
     * Return the scan direction (DEG)
     */
    public int getScanDirection() {
        return scanDirection;
    }

    /**
     * Returns the speed (pps)
     */
    public int getSpeed() {
        return speed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(halt, moveDirection, move, scan, scanDirection, speed);
    }

    /**
     * Returns true if halt command
     */
    public boolean isHalt() {
        return halt;
    }

    /**
     * Returns true if move command
     */
    public boolean isMove() {
        return move;
    }

    /**
     * Returns true if movement command (move o halt)
     */
    public boolean isMovement() {
        return halt || move;
    }

    /**
     * Returns true if scan command
     */
    public boolean isScan() {
        return scan;
    }

    /**
     * Returns the command with set scan
     *
     * @param scanDirection the scan direction (DEG)
     */
    public RobotCommands setScan(int scanDirection) {
        return new RobotCommands(true, scanDirection, move, halt, moveDirection, speed);
    }

    /**
     * Returns the command with halt command
     */
    public RobotCommands setHalt() {
        return new RobotCommands(scan, scanDirection, false, true, 0, 0);
    }

    /**
     * Returns the command with move command
     *
     * @param direction the move direction (DEG)
     * @param speed     the speed (pps)
     */
    public RobotCommands setMove(int direction, int speed) {
        return new RobotCommands(scan, scanDirection, true, false, direction, speed);
    }

    /**
     * Returns the concatenation of this command with another
     *
     * @param other the other command
     */
    public RobotCommands then(RobotCommands other) {
        return concat(this, other);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RobotCommands.class.getSimpleName() + "[", "]")
                .add("scan=" + scan)
                .add("scanDirection=" + scanDirection)
                .add("halt=" + halt)
                .add("move=" + move)
                .add("moveDirection=" + moveDirection)
                .add("speed=" + speed)
                .toString();
    }
}
