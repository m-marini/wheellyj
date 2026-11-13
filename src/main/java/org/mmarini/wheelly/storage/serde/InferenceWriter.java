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

package org.mmarini.wheelly.storage.serde;

import org.mmarini.wheelly.behaviours.BehaviourAction;
import org.mmarini.wheelly.behaviours.BehaviourState;

import java.io.IOException;

/**
 * Defines a writer for serialising AI inference or training steps to an
 * underlying output destination.
 * <p>
 * Each recorded step persists an observed {@link BehaviourState} together with
 * the corresponding {@link BehaviourAction} selected or executed by the robot.
 * </p>
 */
public interface InferenceWriter extends AutoCloseable {

    /**
     * Serialises and writes a single state-action inference step to the
     * underlying output stream.
     *
     * @param <T>    the concrete type of this {@code InferenceWriter} to support
     *               fluent method chaining
     * @param state  the observed {@link BehaviourState} representing the world
     *               model and current robot status
     * @param action the {@link BehaviourAction} executed in response to the
     *               observed state
     * @return this writer instance for fluent method chaining
     * @throws IOException if an I/O error occurs whilst serialising or writing
     *                     the record
     */
    <T extends InferenceWriter> T write(BehaviourState state, BehaviourAction action) throws IOException;
}
