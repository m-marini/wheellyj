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

package org.mmarini.wheelly.rlagents;

import org.mmarini.wheelly.gyms.AdapterConnector;
import org.mmarini.wheelly.gyms.WithSignalsSpec;
import org.mmarini.wheelly.utils.WithShutdownCompletable;
import org.mmarini.yaml.Utils;

import java.io.File;

public interface Agent extends AdapterConnector, AutoCloseable, WithShutdownCompletable {

    /**
     * Returns the agent from agent
     *
     * @param file the configuration file
     */
    static Agent fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }

    /**
     * Backs up the model
     */
    void backup();

    @Override
    void close();

    /**
     * Resets the inference engine
     */
    Agent init();

    /**
     * Sets the learning state of the agent's underlying cognitive or neural model.
     * <p>
     * Enabling learning allows the agent to continuously adapt its policy, value functions,
     * or internal parameters based on environmental rewards and sensory feedback. Freezing
     * learning configures the agent to run strictly in inference mode, executing the
     * currently optimal policy without further exploration or weight updates.
     * </p>
     *
     * @param learning {@code true} to enable reinforcement learning and policy adaptation,
     *                 {@code false} to lock the agent's behavior to execution-only mode.
     */
    void learning(boolean learning);

    void validate(WithSignalsSpec adapter) throws Throwable;

    /**
     * Saves the model
     */
    void save();
}
