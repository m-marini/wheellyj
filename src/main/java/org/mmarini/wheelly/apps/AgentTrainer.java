/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.core.Maybe;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.envs.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Tracks the immutable process state of training
 *
 * @param agent                the agent
 * @param trainer              the agent trainer
 * @param training             true if training in progress
 * @param skipNextTrainedAgent true if the next trained agent has to be skipped
 * @param synchTraining        true if training must run synchronously
 * @param nextSave             the next save instant (ms)
 * @param saveInterval         the save interval (ms)
 */
public record AgentTrainer(Agent agent,
                           Maybe<AgentTrainer> trainer, boolean training,
                           boolean skipNextTrainedAgent,
                           boolean synchTraining,
                           long nextSave,
                           long saveInterval
) {
    private static final Logger logger = LoggerFactory.getLogger(AgentTrainer.class);

    /**
     * Returns the trainer for the initial agent
     *
     * @param initialAgent  the initial agent
     * @param synchTraining true if training must run synchronously
     * @param saveInterval  the save interval (ms)
     */
    public static AgentTrainer create(Agent initialAgent, boolean synchTraining, long saveInterval) {
        return new AgentTrainer(initialAgent, null, false, false, synchTraining,
                System.currentTimeMillis() + saveInterval, saveInterval);
    }

    /**
     * Returns the trainer by setting the agent
     *
     * @param agent the agent
     */
    AgentTrainer agent(Agent agent) {
        return agent != this.agent
                ? new AgentTrainer(agent, trainer, training, skipNextTrainedAgent, synchTraining, nextSave, saveInterval)
                : this;
    }

    /**
     * Returns the agent
     */
    public Agent agent() {
        return agent;
    }

    /**
     * Returns the trainer by changing the alpha parameters
     *
     * @param alphas the alpha parameters
     */
    public AgentTrainer alphas(Map<String, Float> alphas) {
        return agent(agent.alphas(alphas));
    }

    /**
     * Returns the trainer by changing the eta parameter
     *
     * @param eta the eta parameter
     */
    public AgentTrainer eta(float eta) {
        return agent(agent.eta(eta));
    }

    /**
     * Returns the trainer by setting the next save instant
     *
     * @param nextSave the next save instant (ms)
     */
    AgentTrainer nextSave(long nextSave) {
        return nextSave != this.nextSave
                ? new AgentTrainer(agent, trainer, training, skipNextTrainedAgent, synchTraining, nextSave, saveInterval)
                : this;
    }

    /**
     * Returns the agent trainer after the observation of the result,
     * The resulting tuple is the immediate result trainer and the future trained agent at completion of asynchronous training if any
     *
     * @param result the observed result
     */
    public AgentTrainer observeResult(Environment.ExecutionResult result) {
        // Let the agent observe the result
        Agent trainingAgent = agent.observe(result);
        if (!trainingAgent.isReadyForTrain() || training) {
            // Training skipped because is not ready or is already training
            return agent(trainingAgent).trainer(null);
        }

        // Agent ready to train
        List<Environment.ExecutionResult> trajectory = trainingAgent.trajectory();
        // Extract clipped trajectory
        List<Environment.ExecutionResult> clippedTrajectory = trajectory.size() > trainingAgent.numSteps()
                ? trajectory.stream().skip(trajectory.size() - trainingAgent.numSteps()).toList()
                : trajectory;
        // Generate new agent with cleared trajectory
        Agent clearedAgent = trainingAgent.trajectory(List.of());
        if (synchTraining) {
            // Synchronously runs the train process
            return train(clearedAgent, clippedTrajectory, trajectory.size())
                    .trainer(null);
        }

        // Runs asynchronously the train process
        AgentTrainer nextTrainer = agent(clearedAgent).training(true);
        return nextTrainer.eta(clearedAgent.eta())
                .alphas(clearedAgent.alphas())
                .trainer(
                        Maybe.fromSupplier(() ->
                                train(clearedAgent, clippedTrajectory, trajectory.size())));
    }

    /**
     * Returns the agent trainer by setting the trained agent
     *
     * @param trainer the agent trainer
     */
    public AgentTrainer setTrainedAgent(AgentTrainer trainer) {
        if (skipNextTrainedAgent) {
            return skipNextTrainedAgent(false)
                    .training(false);
        }
        Agent trainedAgent = trainer.agent()
                .eta(agent.eta())
                .alphas(agent.alphas())
                .trajectory(agent.trajectory());
        return agent(trainedAgent)
                .nextSave(trainer.nextSave)
                .training(false);
    }

    /**
     * Returns the trainer with reset agent
     */
    public AgentTrainer resetAgent() {
        return training ?
                agent(agent.init()).skipNextTrainedAgent(true)
                : agent(agent.init());
    }

    /**
     * Returns the trainer by setting the reset flag
     *
     * @param skipNextTrainedAgent true if it will skip the next trained agent
     */
    private AgentTrainer skipNextTrainedAgent(boolean skipNextTrainedAgent) {
        return skipNextTrainedAgent != this.skipNextTrainedAgent
                ? new AgentTrainer(agent, trainer, training, skipNextTrainedAgent, synchTraining, nextSave, saveInterval)
                : this;
    }

    private AgentTrainer train(Agent agent, List<Environment.ExecutionResult> trajectory, int size) {
        logger.atDebug().log("Training ...");
        // Trains the agent
        long t0 = System.currentTimeMillis();
        Agent trained = agent.trainByTrajectory(trajectory);
        long now = System.currentTimeMillis();
        long elapsed = now - t0;
        logger.atInfo().log("Trained {}/{} steps in {} ms.", trajectory.size(), size, elapsed);
        long nextSave = this.nextSave;
        if (now >= nextSave) {
            trained.save();
            nextSave = now + saveInterval;
        }
        // Concurrent train the cleared agent
        return agent(trained).nextSave(nextSave);
    }

    /**
     * Returns the trainer by setting the trainer
     *
     * @param trainer the trainer
     */
    AgentTrainer trainer(Maybe<AgentTrainer> trainer) {
        return trainer != this.trainer
                ? new AgentTrainer(agent, trainer, training, skipNextTrainedAgent, synchTraining, nextSave, saveInterval)
                : this;
    }

    /**
     * Returns the trainer by setting the training flag
     *
     * @param training true if agent is training
     */
    private AgentTrainer training(boolean training) {
        return training != this.training
                ? new AgentTrainer(agent, trainer, training, skipNextTrainedAgent, synchTraining, nextSave, saveInterval)
                : this;
    }
}
