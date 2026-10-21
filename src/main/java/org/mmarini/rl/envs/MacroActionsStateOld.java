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

package org.mmarini.rl.envs;

import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.perception.WithWorldModel;
import org.mmarini.wheelly.perception.WorldModel;

import static java.util.Objects.requireNonNull;

/**
 * Represents an extended, immutable snapshot of the environment state enclosing the world model
 * alongside the concurrent high-level finite state machine macro-actions.
 * <p>
 * This record aggregates the spatial telemetry of the robot platform with the active identifiers
 * for locomotion and sensor head behaviors, providing a comprehensive context profile utilized
 * for state feature vector extraction and behavioral modelling.
 * </p>
 *
 * @param worldModel   the {@link WorldModel} tracking the robot telemetry and radar map data
 * @param moveActionId the active {@link MoveActionId} tracking the concurrent high-level locomotion macro-action
 * @param headActionId the active {@link HeadActionId} tracking the concurrent high-level sensor head macro-action
 */
public record MacroActionsStateOld(WorldModel worldModel, MoveActionId moveActionId,
                                   HeadActionId headActionId) implements BehaviourStateOld, WithWorldModel {

    /**
     * Constructs a {@code MacroEnvState} validating that all parameters are non-null.
     *
     * @param worldModel   the {@link WorldModel} tracking the robot telemetry and radar map data
     * @param moveActionId the active {@link MoveActionId} tracking the concurrent high-level locomotion macro-action
     * @param headActionId the active {@link HeadActionId} tracking the concurrent high-level sensor head macro-action
     * @throws NullPointerException if the {@code worldModel}, {@code moveActionId}, or {@code headActionId} is {@code null}
     */
    public MacroActionsStateOld {
        requireNonNull(worldModel);
        requireNonNull(moveActionId);
        requireNonNull(headActionId);
    }
}
