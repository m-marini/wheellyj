/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.agents;

import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.util.function.Function;

public interface Agent extends AgentConnector, AutoCloseable {

    /**
     * Returns the agent from agent
     *
     * @param file the configuration file
     */
    static Function<WithSignalsSpec, Agent> fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }

    /**
     * Backs up the model
     */
    void backup();

    /**
     * Clears the trajectory
     */
    Agent clearTrajectory();

    @Override
    void close();

    /**
     * Resets the inference engine
     */
    Agent init();

    /**
     * Returns true if the agent is read for train
     */
    boolean isReadyForTrain();

    Agent dup();

    /**
     * Saves the model
     */
    void save();

    /**
     * Returns the trained agent for the actual trajectory
     */
    Agent trainByTrajectory();
}
