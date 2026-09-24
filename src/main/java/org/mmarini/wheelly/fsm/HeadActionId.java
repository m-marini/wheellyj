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
 * Defines the unique identifiers for the actions available to the robot's sensor head.
 * <p>
 * These constants represent specific visual and scanning behaviours executed by the head
 * subsystem within the environmental Finite State Machine (FSM). They allow the agent to
 * coordinate directional orientation, panoramic environmental scans, and targeted object
 * tracking independently of the motile base's movement.
 * </p>
 */
public enum HeadActionId {

    /**
     * Instructs the sensor head to persist with its currently active state or behaviour,
     * bypassing any immediate directional changes or state transitions.
     */
    CONTINUE_HEAD_ACTION,

    /**
     * Directs the sensor head to re-align itself forward, locking into a straight
     * position parallel to the robot's primary forward driving axis.
     */
    LOOK_STRIGHT_ACTION,

    /**
     * Initiates a periodic panoramic sensory sweep, driving the head to perform continuous
     * scanning intervals to map the surrounding environment.
     */
    SCAN_ACTION,

    /**
     * Commands the sensor head to actively track and face the closest identified marker
     * using a standard forward-looking orientation.
     */
    LOOK_FACE_AT_NEAREST_MARKER_ACTION,

    /**
     * Commands the sensor head to track the closest identified marker by reversing its
     * primary gaze vector, resulting in a rear-looking alignment profile.
     */
    LOOK_REAR_AT_NEAREST_MARKER_ACTION,

    /**
     * Directs the sensor head to lock its focus directly onto the nearest detected physical
     * obstacle to ensure real-time distance assessment.
     */
    LOOK_FACE_AT_NEAREST_OBSTACLE_ACTION,

    /**
     * Directs the sensor head to monitor the nearest detected physical obstacle from a
     * reversed perspective, using a rear-facing alignment.
     */
    LOOK_REAR_AT_NEAREST_OBSTACLE_ACTION
}