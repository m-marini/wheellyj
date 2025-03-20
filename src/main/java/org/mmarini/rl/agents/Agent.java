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
import org.mmarini.MapStream;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.rl.nets.TDNetworkState;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The agent interface
 */
public interface Agent extends Closeable, AgentConnector, WithSignalsSpec, Serializable {

    /**
     * Returns the agent
     *
     * @param file        the configuration file
     */
    static Function<WithSignalsSpec, Agent> fromFile(File file) throws IOException {
        return Utils.createObject(file);
    }

    /**
     * Sets the alpha parameters
     *
     * @param alphas the alpha parameters
     */
    Agent alphas(Map<String, Float> alphas);

    /**
     * Returns the alpha parameters
     */
    Map<String, Float> alphas();

    /**
     * Backup the current model file
     */
    Agent backup();

    /**
     * Returns the batch size for training
     */
    int batchSize();

    /**
     * Returns the learning rate hyperparameter
     */
    float eta();

    /**
     * Returns the learning rate hyperparameter
     *
     * @param eta the learning rate hyperparameter
     */
    Agent eta(float eta);

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
     * Returns the neural network
     */
    TDNetwork network();

    /**
     * Returns the number of epochs for training
     */
    int numEpochs();

    /**
     * Returns the number of steps for training
     */
    int numSteps();

    /**
     * Returns the policy for a network state
     *
     * @param state the network state
     */
    default Map<String, INDArray> policy(TDNetworkState state) {
        return MapStream.of(actionSpec())
                .mapValues((key, value) -> state.getValues(key))
                .toMap();
    }

    /**
     * Returns the flowable kpis
     */
    Flowable<Map<String, INDArray>> readKpis();

    /**
     * Save the model
     */
    void save();

    /**
     * Returns the trained agent for the given trajectory
     *
     * @param trajectory the trajectory
     */
    Agent trainByTrajectory(List<ExecutionResult> trajectory);

    /**
     * Returns the average step rewards after training a mini batch
     *
     * @param epoch        the current epoch number
     * @param startStep    the current start step number
     * @param numStepsParm the number of steps
     * @param states       the states (size=n+1)
     * @param actionMasks  the action masks (size=n)
     * @param rewards      the rewards (size=n)
     * @param actionProb0  the probabilities before train of action a_t (size=n)
     */
    Agent trainMiniBatch(long epoch, long startStep, long numStepsParm, Map<String, INDArray> states,
                         Map<String, INDArray> actionMasks, INDArray rewards, Map<String, INDArray> actionProb0);

    /**
     * Returns the current trajectory
     */
    List<ExecutionResult> trajectory();

    /**
     * Returns the agent with a trajectory set
     *
     * @param trajectory the trajectory
     */
    Agent trajectory(List<ExecutionResult> trajectory);
}
