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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * An immutable record representing a composite decision tuple dispatched to the robot hardware.
 * <p>
 * This class standardises the multi-task dual-branch action space by combining a strategic
 * base movement command alongside a high-level perception head command. It ensures type safety
 * and immutable state transition tracking to stabilise the robot's physical behaviour.
 * </p>
 *
 * @param moveId the unique identifier for the base locomotion and alignment action branch
 * @param headId the unique identifier for the high-level semantic perception head action branch
 */
public record AgentAction(MoveActionId moveId, HeadActionId headId) {

    /**
     * Constructs an {@code AgentAction} record instance, validating that both action identifiers
     * are strictly non-null.
     *
     * @param moveId the unique identifier for the base locomotion branch; must not be null
     * @param headId the unique identifier for the perception head branch; must not be null
     * @throws NullPointerException if either {@code moveId} or {@code headId} is null
     */
    public AgentAction(MoveActionId moveId, HeadActionId headId) {
        this.moveId = requireNonNull(moveId);
        this.headId = requireNonNull(headId);
    }

    /**
     * Returns a new {@code AgentAction} instance with the specified head action identifier,
     * or returns this instance if the identifier matches the current one to optimise memory allocation.
     *
     * @param headId the new unique identifier for the perception head branch; must not be null
     * @return an {@code AgentAction} instance with the updated head identifier
     * @throws NullPointerException if the provided {@code headId} is null
     */
    public AgentAction headId(HeadActionId headId) {
        return Objects.equals(headId, this.headId)
                ? this
                : new AgentAction(moveId, headId);
    }

    /**
     * Returns a new {@code AgentAction} instance with the specified base movement action identifier,
     * or returns this instance if the identifier matches the current one to optimise memory allocation.
     *
     * @param moveId the new unique identifier for the base locomotion branch; must not be null
     * @return an {@code AgentAction} instance with the updated movement identifier
     * @throws NullPointerException if the provided {@code moveId} is null
     */
    public AgentAction moveId(MoveActionId moveId) {
        return Objects.equals(moveId, this.moveId)
                ? this
                : new AgentAction(moveId, headId);
    }
}