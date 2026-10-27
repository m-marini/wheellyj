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

import org.mmarini.wheelly.robots.RobotCommand;

/**
 * Defines the contract for connecting external subsystems to the environment perceptor.
 * <p>
 * This interface extends the decision-making API by binding it to a {@link WorldModel}
 * observation space and a {@link RobotCommand} action space. It functions as the core
 * integration link within the perception stack, mapping raw sensory interpretations
 * into structured environment models used by the agent to determine the next locomotion tasks.
 * </p>
 */
public interface PerceptionConnector extends WithDecisionMaker<WorldModel, RobotCommand> {

    /**
     * Returns the structural and dimensional specification of the world model.
     * <p>
     * The returned specification delineates the spatial boundaries, grid layouts,
     * or architectural parameters characterising the environment map and state vectors.
     * </p>
     *
     * @return The {@link WorldModelSpec} describing the constraints and layout of the world model.
     */
    WorldModelSpec worldModelSpec();
}
