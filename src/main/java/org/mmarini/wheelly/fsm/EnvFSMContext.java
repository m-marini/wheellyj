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

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.mmarini.wheelly.apis.WorldModel;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * Defines the execution context for the environment Finite State Machine (FSM).
 * <p>
 * This interface serves as the central repository for the state machine's operational
 * data, maintaining the current map and sensory status via the {@link WorldModel}.
 * It coordinates the execution lifecycle and handles sensory events to drive
 * the robot's physical <b>behaviour</b>.
 * </p>
 */
public interface EnvFSMContext {

    Single<List<Point2D>> pathToNearestMarker();

    Single<List<Point2D>> pathToNearestUnknownArea();

    AgentAction nextAction();

    /**
     * Returns the current model of the world containing sensory data and map features.
     * <p>
     * Active states utilise this model to <b>analyse</b> environmental changes,
     * process spatial coordinates, and calculate obstacles.
     * </p>
     *
     * @return the current {@link WorldModel} instance
     */
    WorldModel worldModel();
}