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

package org.mmarini.wheelly.storage.serde;

import org.mmarini.Tuple2;
import org.mmarini.wheelly.behaviours.BehaviourAction;
import org.mmarini.wheelly.behaviours.BehaviourState;

import java.io.IOException;


/**
 * Defines a reader for deserialising recorded AI inference or training steps
 * from an underlying data source.
 * <p>
 * Each step consists of an observed {@link BehaviourState} paired with the
 * corresponding {@link BehaviourAction} executed by the robot.
 * </p>
 */
public interface InferenceReader extends AutoCloseable {

    /**
     * Reads the next macro-action step from the input stream.
     *
     * @return a {@link Tuple2} containing the observed {@link BehaviourState} and
     * the executed {@link BehaviourAction}, or {@code null} if the end of
     * the stream has been reached
     * @throws IOException if an I/O error occurs whilst reading or deserialising
     *                     the record
     */
    Tuple2<BehaviourState, BehaviourAction> readMacroActionStep() throws IOException;

    /**
     * Resets the reader back to the beginning of the inference stream, allowing
     * the recorded steps to be replayed from the start.
     *
     * @throws Exception if an error occurs whilst resetting the underlying stream
     *                   or source
     */
    void reset() throws Exception;
}