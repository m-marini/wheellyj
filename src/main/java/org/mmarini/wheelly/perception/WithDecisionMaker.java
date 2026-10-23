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

/**
 * Defines the contract for components that accept the injection of a decision-making entity.
 * <p>
 * This interface establishes a standard mechanism to bind an environmental processing layer
 * or perception pipeline to a specific policy engine. It decouples state estimation routines
 * from the actual action selection logic, facilitating interchangeable strategies for agent behaviour.
 * </p>
 *
 * @param <S> The type representing the state or observation space ingested by the decision maker.
 * @param <A> The type representing the action or command space produced by the decision maker.
 */
public interface WithDecisionMaker<S, A> {

    /**
     * Injects the decision-making engine responsible for mapping environmental states into actions.
     * <p>
     * The attached decision maker is invoked within the control loop to evaluate incoming state snapshots
     * and select the corresponding target behaviours or commands.
     * </p>
     *
     * @param decisionMaker The {@link DecisionMaker} instance to be assigned to this component.
     */
    void decisionMaker(DecisionMaker<S, A> decisionMaker);
}
