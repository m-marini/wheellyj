/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apis;

import java.awt.geom.Point2D;

import static java.util.Objects.requireNonNull;

/**
 * Store the command parameters for the required robot state
 *
 * @param status            the status
 * @param scanDirection     the scan direction (DEG)
 * @param rotationDirection the rotation direction (DEG)
 * @param target            the target location
 */
public record RobotCommands(StatusCommand status, int scanDirection, int rotationDirection,
                            Point2D target) {

    static RobotCommands HALT = new RobotCommands(StatusCommand.HALT, 0, 0, null);

    /**
     * Returns the goto backward command
     *
     * @param target the target location
     */
    public static RobotCommands backward(Point2D target) {
        return new RobotCommands(StatusCommand.BACKWARD, 0, 0, target);
    }

    /**
     * Returns the goto backward command
     *
     * @param scanDirection the scan direction (DEG)
     * @param target        the target location
     */
    public static RobotCommands backward(int scanDirection, Point2D target) {
        return new RobotCommands(StatusCommand.BACKWARD, scanDirection, 0, target);
    }

    /**
     * Returns the goto backward command
     *
     * @param scanDirection the scan direction
     * @param target        the target location
     */
    public static RobotCommands backward(Complex scanDirection, Point2D target) {
        return backward(scanDirection.toIntDeg(), target);
    }

    /**
     * Returns the goto forward command with frontal head
     *
     * @param target the target location
     */
    public static RobotCommands forward(Point2D target) {
        return forward(0, target);
    }

    /**
     * Returns the goto forward command
     *
     * @param scanDirection the scan direction (DEG)
     * @param target        the target location
     */
    public static RobotCommands forward(int scanDirection, Point2D target) {
        return new RobotCommands(StatusCommand.FORWARD, scanDirection, 0, target);
    }

    /**
     * Returns the goto forward command
     *
     * @param scanDirection the scan direction
     * @param target        the target location
     */
    public static RobotCommands forward(Complex scanDirection, Point2D target) {
        return forward(scanDirection.toIntDeg(), target);
    }

    /**
     * Returns the halt command
     *
     * @param scanDirection the scan direction
     */
    public static RobotCommands halt(Complex scanDirection) {
        return halt(scanDirection.toIntDeg());
    }

    /**
     * Returns the halt command
     *
     * @param scanDirection the scan direction (DEG)
     */
    public static RobotCommands halt(int scanDirection) {
        return scanDirection == 0
                ? HALT
                : new RobotCommands(StatusCommand.HALT, scanDirection, 0, null);
    }

    /**
     * Returns the halt command with frontal head
     */
    public static RobotCommands halt() {
        return HALT;
    }

    /**
     * Returns the rotate command
     *
     * @param scanDirection     the scan direction
     * @param rotationDirection the rotation direction
     */
    public static RobotCommands rotate(Complex scanDirection, Complex rotationDirection) {
        return rotate(scanDirection.toIntDeg(), rotationDirection.toIntDeg());
    }

    /**
     * Returns the rotate command
     *
     * @param scanDirection     the scan direction (DEG)
     * @param rotationDirection the rotation direction (DEG)
     */
    public static RobotCommands rotate(int scanDirection, int rotationDirection) {
        return new RobotCommands(StatusCommand.ROTATE, scanDirection, rotationDirection, null);
    }

    /**
     * Returns the rotate command with frontal head
     *
     * @param rotationDirection the rotation direction (DEG)
     */
    public static RobotCommands rotate(int rotationDirection) {
        return rotate(0, rotationDirection);
    }

    /**
     * Returns the rotate command with frontal head
     *
     * @param rotationDirection the rotation direction
     */
    public static RobotCommands rotate(Complex rotationDirection) {
        return rotate(rotationDirection.toIntDeg());
    }

    /**
     * Creates the robot status command
     *
     * @param status            the status
     * @param scanDirection     the scan direction
     * @param rotationDirection the rotation direction
     * @param target            the target location
     */
    public RobotCommands(StatusCommand status, int scanDirection, int rotationDirection, Point2D target) {
        this.status = requireNonNull(status);
        this.scanDirection = scanDirection;
        this.rotationDirection = rotationDirection;
        this.target = target;
    }

    /**
     * Returns true if halt command
     */
    public boolean isHalt() {
        return StatusCommand.HALT.equals(status);
    }

    /**
     * Returns true if rotate command
     */
    public boolean isRotate() {
        return StatusCommand.ROTATE.equals(status);
    }

    /**
     * Sets the scan direction
     *
     * @param scanDirection scan direction
     */
    public RobotCommands scanDirection(Complex scanDirection) {
        return scanDirection(scanDirection.toIntDeg());
    }

    /**
     * Sets the scan direction
     *
     * @param scanDirection scan direction (DEG)
     */
    public RobotCommands scanDirection(int scanDirection) {
        return scanDirection == this.scanDirection ? this
                : new RobotCommands(status, scanDirection, rotationDirection, target);
    }

    /**
     * The required status command
     */
    public enum StatusCommand {
        HALT,
        FORWARD,
        BACKWARD,
        ROTATE
    }
}
