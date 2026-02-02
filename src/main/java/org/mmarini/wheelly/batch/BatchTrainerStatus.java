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

package org.mmarini.wheelly.batch;

import org.mmarini.rl.agents.RLDatasetIterator;
import org.mmarini.wheelly.apis.BatchAgent;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * @param agent           the agent
 * @param datasetIterator dataset iterator
 * @param stop
 */
public record BatchTrainerStatus(BatchAgent agent, RLDatasetIterator datasetIterator, boolean stop) {
    /**
     * Creates the batch trainer status
     *
     * @param agent           the agent
     * @param datasetIterator dataset iterator
     * @param stop            true if stop requested
     */
    public BatchTrainerStatus(BatchAgent agent, RLDatasetIterator datasetIterator, boolean stop) {
        this.agent = requireNonNull(agent);
        this.datasetIterator = datasetIterator;
        this.stop = stop;
    }

    /**
     * Sets the agent
     *
     * @param agent the agent
     */
    BatchTrainerStatus agent(BatchAgent agent) {
        return !Objects.equals(agent, this.agent)
                ? new BatchTrainerStatus(agent, datasetIterator, stop)
                : this;
    }

    /**
     * Sets the dataset iterator
     *
     * @param datasetIterator the dataset iterator
     */
    public BatchTrainerStatus datasetIterator(RLDatasetIterator datasetIterator) {
        return !Objects.equals(datasetIterator, this.datasetIterator)
                ? new BatchTrainerStatus(agent, datasetIterator, stop)
                : this;
    }

    /**
     * Sets the stop request
     *
     * @param stop true if stop request
     */
    public BatchTrainerStatus stop(boolean stop) {
        return stop != this.stop
                ? new BatchTrainerStatus(agent, datasetIterator, stop)
                : this;
    }
}
