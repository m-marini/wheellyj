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

package org.mmarini.wheelly.behaviours;

import org.mmarini.wheelly.envs.WorldModel;

import static java.util.Objects.requireNonNull;

/**
 * Represents an immutable snapshot of the environment state enclosing a single world model.
 * <p>
 * This record serves as a fundamental data container within the reinforcement learning or
 * simulation loop, modelling the spatial and kinematic context of the robot platform
 * at a specific point in time.
 * </p>
 *
 * @param worldModel the {@link WorldModel} tracking the robot telemetry and radar map data
 */
public record BasicEnvState(WorldModel worldModel) implements EnvState {

    /**
     * Constructs a {@code BasicEnvState} ensuring that the encapsulated world model is non-null.
     *
     * @param worldModel the {@link WorldModel} tracking the robot telemetry and radar map data
     * @throws NullPointerException if the provided world model is {@code null}
     */
    public BasicEnvState {
        requireNonNull(worldModel);
    }
}
