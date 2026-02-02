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
import java.util.Optional;

/**
 * Store the possible commands combination haltCommand, move, scan
 *
 * @param scan          true if scan command
 * @param scanDirection the scan direction
 * @param move          true if movement command
 * @param halt          true if haltCommand command
 * @param moveDirection the move direction
 * @param speed         the power (pps)
 */
public record RobotCommands(boolean scan, Complex scanDirection, boolean move, boolean halt, Complex moveDirection,
                            int speed) {

    private static final RobotCommands NONE = new RobotCommands(false, Complex.DEG0, false, false, Complex.DEG0, 0);
    private static final RobotCommands HALT_MOVE = new RobotCommands(false, Complex.DEG0, false, true, Complex.DEG0, 0);
    private static final RobotCommands HALT = new RobotCommands(true, Complex.DEG0, false, true, Complex.DEG0, 0);

    /**
     * Returns the concatenation of commands
     * The last scan with last move or haltCommand command is returned
     *
     * @param commands the commands
     */
    public static RobotCommands concat(RobotCommands... commands) {
        Optional<RobotCommands> scanCmd = Arrays.stream(commands).filter(RobotCommands::scan).reduce((a, b) -> b);
        Optional<RobotCommands> movementCmd = Arrays.stream(commands).filter(RobotCommands::isMovement).reduce((a, b) -> b);
        return movementCmd.map(mv ->
                scanCmd.map(sc -> mv.setScan(sc.scanDirection())).orElse(mv)
        ).orElse(
                scanCmd.orElse(RobotCommands.none()));
    }

    /**
     * Returns the idle command
     */
    public static RobotCommands haltCommand() {
        return HALT;
    }

    /**
     * Returns the haltCommand command
     */
    public static RobotCommands haltMove() {
        return HALT_MOVE;
    }

    /**
     * Returns the only move command
     *
     * @param direction the move direction
     * @param speed     the power (pps)
     */
    public static RobotCommands move(Complex direction, int speed) {
        return new RobotCommands(false, Complex.DEG0, true, false, direction, speed);
    }

    /**
     * Returns the move command and front scan
     *
     * @param direction the move direction
     * @param speed     the power (pps)
     */
    public static RobotCommands moveAndFrontScan(Complex direction, int speed) {
        return new RobotCommands(true, Complex.DEG0, true, false, direction, speed);
    }

    /**
     * Returns the move command and scan
     *
     * @param direction     the move direction
     * @param speed         the power (pps)
     * @param scanDirection the scan direction
     */
    public static RobotCommands moveAndScan(Complex direction, int speed, Complex scanDirection) {
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
    public static RobotCommands scan(Complex direction) {
        return new RobotCommands(true, direction, false, false, Complex.DEG0, 0);
    }

    /**
     * Returns the command with cleared scan
     */
    public RobotCommands clearScan() {
        return new RobotCommands(false, Complex.DEG0, move, halt, moveDirection, speed);
    }

    /**
     * Returns true if movement command (move o haltCommand)
     */
    public boolean isMovement() {
        return halt || move;
    }

    /**
     * Returns the command with haltCommand command
     */
    public RobotCommands setHalt() {
        return new RobotCommands(scan, scanDirection, false, true, Complex.DEG0, 0);
    }

    /**
     * Returns the command with move command
     *
     * @param direction the move direction
     * @param speed     the power (pps)
     */
    public RobotCommands setMove(Complex direction, int speed) {
        return new RobotCommands(scan, scanDirection, true, false, direction, speed);
    }

    /**
     * Returns the command with set scan
     *
     * @param scanDirection the scan direction (DEG)
     */
    public RobotCommands setScan(Complex scanDirection) {
        return new RobotCommands(true, scanDirection, move, halt, moveDirection, speed);
    }

    /**
     * Returns the concatenation of this command with another
     *
     * @param other the other command
     */
    public RobotCommands then(RobotCommands other) {
        return concat(this, other);
    }

}
