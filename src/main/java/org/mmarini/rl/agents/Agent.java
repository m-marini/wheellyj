/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * The agent interface
 */
public interface Agent extends Closeable, WithSignalsSpec {

    /**
     * Returns the agent
     *
     * @param config      the json document
     * @param locator     the config locator
     * @param environment the environment
     */
    static Agent fromConfig(JsonNode config, Locator locator, WithSignalsSpec environment) {
        return Utils.createObject(config, locator, new Object[]{environment}, new Class[]{WithSignalsSpec.class});
    }

    /**
     * Returns the action generated by agent for the given state
     *
     * @param state the state
     */
    Map<String, Signal> act(Map<String, Signal> state);

    /**
     * Resets the inference engine
     */

    Agent init();

    /**
     * Returns true if the agent is read for train
     */
    boolean isReadyForTrain();

    /**
     * Returns the agent specification json node
     */
    JsonNode json();

    /**
     * Returns the number of steps for training
     */
    int numSteps();

    /**
     * Observes the execution result training the agent
     *
     * @param result the execution result
     */
    Agent observe(Environment.ExecutionResult result);

    /**
     * Returns the flowable kpis
     */
    Flowable<Map<String, INDArray>> readKpis();

    /**
     * Save the model
     *
     * @param path the path
     * @throws IOException in case of error
     */
    void save(File path) throws IOException;

    /**
     * Returns the trained agent for the given trajectory
     *
     * @param trajectory the trajectory
     */
    Agent trainByTrajectory(List<Environment.ExecutionResult> trajectory);

    /**
     * Returns the current trajectory
     */
    List<Environment.ExecutionResult> trajectory();

    /**
     * Returns the agent with a trajectory set
     *
     * @param trajectory the trajectory
     */
    Agent trajectory(List<Environment.ExecutionResult> trajectory);
}
