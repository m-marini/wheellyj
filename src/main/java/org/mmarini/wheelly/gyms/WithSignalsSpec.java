/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.gyms;

import java.util.Map;

/**
 * Defines the contract for components that expose structural tensor specifications for
 * both environmental states and agent actions.
 * <p>
 * This interface is implemented by environment bridges, execution gyms, or agent structures
 * to declare the expected multidimensional layouts of their interaction data. It provides
 * the necessary structural meta-information required to validate and align network architectures
 * with the target agent-environment loop bounds.
 * </p>
 */
public interface WithSignalsSpec {

    /**
     * Returns the comprehensive dictionary mapping action identifiers to their dimensional layout specifications.
     * <p>
     * The returned configuration maps explicitly define the structural limits and geometric bounds
     * of the tensors produced by the agent during policy evaluation steps.
     * </p>
     *
     * @return A {@link Map} containing the structural action keys and their corresponding {@link SignalSpec} rules.
     */
    Map<String, SignalSpec> actionSpec();

    /**
     * Returns the comprehensive dictionary mapping state identifiers to their dimensional layout specifications.
     * <p>
     * The returned configuration maps explicitly define the structural limits and geometric bounds
     * of the telemetry vectors or environment matrices ingested by the agent as observations.
     * </p>
     *
     * @return A {@link Map} containing the structural state keys and their corresponding {@link SignalSpec} rules.
     */
    Map<String, SignalSpec> stateSpec();

}
