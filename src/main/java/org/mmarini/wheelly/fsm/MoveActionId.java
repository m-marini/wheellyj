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

package org.mmarini.wheelly.fsm;

/**
 * Defines the unique identifiers for movement actions within the robotic finite state machine.
 * <p>
 * This enumeration characterises the low-level reactive behaviours, structural overrides, and
 * high-level inferential navigational routines executed by the robot.
 * </p>
 */
public enum MoveActionId {
    /**
     * Maintains the current movement behaviour without altering velocity or direction parameters.
     */
    CONTINUE_MOVE_ACTION,

    /**
     * Halts the robot's movement.
     * Depending on the operational context, this action may carry a zero commitment value for forced safety
     * overrides, or a real commitment value for planified inferential halts.
     */
    HALT_ACTION,

    /**
     * Executes a micro-incremental translation forward along the robot's heading vector.
     */
    MICRO_FORWARD_ACTION,

    /**
     * Executes a micro-incremental translation backward opposite to the robot's heading vector.
     */
    MICRO_BACKWARD_ACTION,

    /**
     * Executes a sharp, minor rotational change to the left.
     */
    MICRO_LEFT_ACTION,

    /**
     * Executes a sharp, minor rotational change to the right.
     */
    MICRO_RIGHT_ACTION,

    /**
     * Rotates the robot's primary facade or sensor array to orient towards the nearest detected obstacle.
     */
    TURN_FACE_NEAREST_OBSTACLE_ACTION,

    /**
     * Rotates the robot's rear chassis to directly face away from the closest detected obstacle.
     */
    TURN_REAR_NEAREST_OBSTACLE_ACTION,

    /**
     * Aligns the robot's orientation to face the nearest active environmental marker.
     */
    TURN_FACE_NEAREST_MARKER_ACTION,

    /**
     * Aligns the robot's rear section to face the nearest active environmental marker.
     */
    TURN_REAR_NEAREST_MARKER_ACTION,

    /**
     * Initiates a leftward sweeping rotation to perform active scanning operations.
     */
    TURN_LEFT_SCAN_ACTION,

    /**
     * Initiates a rightward sweeping rotation to perform active scanning operations.
     */
    TURN_RIGHT_SCAN_ACTION,

    /**
     * Triggers an immediate safety disengagement and backing routine upon physical collision or contact.
     */
    DISENGAGE_ON_CONTACT_ACTION,

    /**
     * Actively pursues and monitors the proximity relative to the nearest identified marker.
     */
    TRACK_NEAREST_MARKER,

    /**
     * Coordinates exploration strategies to navigate towards the nearest unmapped or unknown geographic area.
     * This behaviour typically relies on RRT pathfinding or grid-based analysis.
     */
    EXPLORE_NEAREST_UNKNOWN_AREA
}