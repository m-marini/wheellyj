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

package org.mmarini.wheelly.apis;

import org.mmarini.Tuple2;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.agents.RLDatasetIterator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;

import java.util.Map;

/**
 * Trains the agent by batch
 */
public interface BatchAgent extends Agent, AgentConnector {

    /**
     * Returns the current average reward
     */
    float avgReward();

    /**
     * Returns the mini batch size
     */
    int batchSize();

    /**
     * Returns the multi dataset to train the agent and the final average reward from the given states, actionMasks,
     * rewards and initial average reward
     *
     * @param states      the states (n+1)
     * @param actionMasks the action masks (n)
     * @param rewards     the rewards (n)
     * @param avgReward   the initial average reward
     */
    Tuple2<MultiDataSet, Float> createDataSet(Map<String, INDArray> states, Map<String, INDArray> actionMasks, INDArray rewards, float avgReward);

    /**
     * Returns the number of training epochs
     */
    int numEpochs();

    /**
     * Returns the agent trained by dataset iterator
     *
     * @param datasetIterator the dataset iterator
     * @param numEpochs       the number of epochs@
     */
    BatchAgent train(RLDatasetIterator datasetIterator, int numEpochs);
}
