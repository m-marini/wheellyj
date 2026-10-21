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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * An immutable record representing a composite decision tuple dispatched to the robot hardware.
 * <p>
 * This class standardises the multitask dual-branch action space by combining a strategic
 * base movement command alongside a high-level perception head command. It ensures type safety
 * and immutable state transition tracking to stabilise the robot's physical behaviour.
 * </p>
 *
 * @param moveActionId the unique identifier for the base locomotion and alignment action branch
 * @param headActionId the unique identifier for the high-level semantic perception head action branch
 */
public record MacroAction(MoveActionId moveActionId, HeadActionId headActionId) implements BehaviourAction {

    /**
     * Constructs an {@code AgentAction} record instance, validating that both action identifiers
     * are strictly non-null.
     *
     * @param moveActionId the unique identifier for the base locomotion branch; must not be null
     * @param headActionId the unique identifier for the perception head branch; must not be null
     * @throws NullPointerException if either {@code moveActionId} or {@code headActionId} is null
     */
    public MacroAction(MoveActionId moveActionId, HeadActionId headActionId) {
        this.moveActionId = requireNonNull(moveActionId);
        this.headActionId = requireNonNull(headActionId);
    }

    /**
     * Returns a new {@code AgentAction} instance with the specified head action identifier,
     * or returns this instance if the identifier matches the current one to optimise memory allocation.
     *
     * @param headId the new unique identifier for the perception head branch; must not be null
     * @return an {@code AgentAction} instance with the updated head identifier
     * @throws NullPointerException if the provided {@code headActionId} is null
     */
    public MacroAction headId(HeadActionId headId) {
        return Objects.equals(headId, this.headActionId)
                ? this
                : new MacroAction(moveActionId, headId);
    }

    /**
     * Returns a new {@code AgentAction} instance with the specified base movement action identifier,
     * or returns this instance if the identifier matches the current one to optimise memory allocation.
     *
     * @param moveId the new unique identifier for the base locomotion branch; must not be null
     * @return an {@code AgentAction} instance with the updated movement identifier
     * @throws NullPointerException if the provided {@code moveActionId} is null
     */
    public MacroAction moveId(MoveActionId moveId) {
        return Objects.equals(moveId, this.moveActionId)
                ? this
                : new MacroAction(moveId, headActionId);
    }
}