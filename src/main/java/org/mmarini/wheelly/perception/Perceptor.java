/*
 * Copyright (c) 2025-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.perception;

import org.mmarini.wheelly.control.ControllerApi;
import org.mmarini.wheelly.robots.RobotCommand;
import org.mmarini.wheelly.robots.RobotSpec;

/**
 * Defines the contract for an environmental perceptor that builds and maintains the world model.
 * <p>
 * This interface integrates sensory connector capabilities, map management functions, and
 * decision-making tracking by extending {@link PerceptionConnector}, {@link WithClearMap},
 * and {@link WithDecisionCallback}. It acts as the central processor within the cognitive stack,
 * interpreting continuous streams of event data to maintain an accurate estimation of the robot's
 * surroundings.
 * </p>
 */
public interface Perceptor extends PerceptionConnector, WithClearMap, WithDecisionCallback<WorldModel, RobotCommand> {

    /**
     * Binds a robot controller to this perceptor to establish the sensory and actuation telemetry loops.
     * <p>
     * Connecting the controller allows the perceptor to ingest raw feedback from physical or
     * simulated hardware devices, enabling continuous world model updates and timely action dispatching.
     * </p>
     *
     * @param controller The {@link ControllerApi} instance to be attached to the perception system.
     * @return A reference to this {@code Perceptor} instance to facilitate method chaining.
     */
    Perceptor connectController(ControllerApi controller);


    /**
     * Initializes the perceptor by configuring the robot physical specifications and
     * connecting the core execution controller.
     * <p>
     * This method prepares the perception system for operational loops, ensuring that sensory
     * thresholds, dimensions, and telemetric capabilities are aligned with the physical or
     * simulated profile of the robot.
     * </p>
     *
     * @param robotSpec  The {@link RobotSpec} containing physical and geometric configurations of the robot.
     * @param controller The {@link ControllerApi} instance handling the communication loop.
     */
    void init(RobotSpec robotSpec, ControllerApi controller);

    WorldModel updateForInference(WorldModel model);
}
