/*
 * Copyright (c) 2024-2025 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.RLDatasetIterator;
import org.mmarini.wheelly.apis.BatchAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Trains the network with data samples from files.
 * Use BatchTrainerBuilder to create the trainer from inference reader
 */
public class BatchTrainer {
    private static final Logger logger = LoggerFactory.getLogger(BatchTrainer.class);

    private final AtomicReference<BatchTrainerStatus> status;
    private final Map<String, BinArrayFile> states;
    private final Map<String, BinArrayFile> actions;
    private final BinArrayFile rewards;
    private final PublishProcessor<ProgressInfo> progressInfo;

    /**
     * Creates the batch trainer
     *
     * @param agent   the network to train
     * @param states  the states
     * @param actions the actions
     * @param rewards the rewards
     */
    public BatchTrainer(BatchAgent agent, Map<String, BinArrayFile> states, Map<String, BinArrayFile> actions, BinArrayFile rewards) {
        this.status = new AtomicReference<>(new BatchTrainerStatus(agent, null, false));
        this.states = requireNonNull(states);
        this.actions = requireNonNull(actions);
        this.rewards = requireNonNull(rewards);
        this.progressInfo = PublishProcessor.create();
        logger.atDebug().log("Created");
    }

    /**
     * Returns the progress info flow
     */
    public Flowable<ProgressInfo> readProgressInfo() {
        return progressInfo;
    }

    /**
     * Stops the batch trainer
     */
    public void stop() {
        BatchTrainerStatus st = status.updateAndGet(s -> s.stop(true));
        RLDatasetIterator iterator = st.datasetIterator();
        if (iterator != null) {
            iterator.stop();
        }
    }

    /**
     * Returns the trained agent
     */
    public BatchAgent train(int numEpochs) throws Exception {
        // Backup the agent
        BatchAgent agent = status.get().agent();
        agent.backup();
        // Creates the dataset iterator
        BinFilesDatasetIterator datasetIterator = new BinFilesDatasetIterator(states, actions, rewards,
                agent.batchSize(), agent.avgReward(),
                agent::createDataSet);
        // Registers for dataset iterator progress info
        datasetIterator.readProgressInfo()
                .subscribeOn(Schedulers.computation())
                .subscribe(progressInfo::onNext,
                        progressInfo::onError,
                        () -> {
                        }
                );
        // Sets the dataset iterator into the batch status
        this.status.updateAndGet(s -> {
            if (s.stop()) {
                datasetIterator.stop();
            }
            return s.datasetIterator(datasetIterator);
        });
        // Train agent
        BatchAgent trained = agent.train(datasetIterator, numEpochs);
        // Save agent
        trained.save();
        // Completes the progress info flow
        progressInfo.onComplete();
        // Sets the trained agent into the batch status
        return this.status.updateAndGet(s -> s.agent(trained)).agent();
    }
}