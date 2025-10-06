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

import io.reactivex.rxjava3.core.Maybe;
import org.mmarini.rl.envs.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Records the status of online training agent
 *
 * @param onlineAgent    the online agent used to generate actions
 * @param trainingAgent  the training agent used during the training
 * @param trainer        the trainer processor
 * @param discardTrained true if the trained agent should be discarded
 * @param merger         the merger operator for the trained agent
 */
public record OnlineAgentStatus(Agent onlineAgent, Agent trainingAgent, Maybe<Agent> trainer, boolean discardTrained,
                                BinaryOperator<Agent> merger) {
    private static final Logger logger = LoggerFactory.getLogger(OnlineAgentStatus.class);

    /**
     * Returns the initial status
     *
     * @param initialAgent the initial agent
     */
    public static OnlineAgentStatus create(Agent initialAgent) {
        return new OnlineAgentStatus(initialAgent, null, null, false, (trained, online) -> trained);
    }

    /**
     * Returns the trained agent
     *
     * @param initialAgent the initial training agent
     */
    private static Agent train(Agent initialAgent) {
        logger.atDebug().log("Training ...");
        // Trains the agent
        long t0 = System.currentTimeMillis();
        Agent trained = initialAgent.trainByTrajectory();
        long now = System.currentTimeMillis();
        long elapsed = now - t0;
        logger.atInfo().log("Trained in {} ms.", elapsed);
        return trained;
    }

    /**
     * Creates the on-line agent status
     *
     * @param onlineAgent    the online agent used to generate actions
     * @param trainingAgent  the training agent used during the training
     * @param trainer        the trainer processor
     * @param discardTrained true if the trained agent should be discarded
     * @param merger         the merger operator for the trained agent
     */
    public OnlineAgentStatus(Agent onlineAgent, Agent trainingAgent, Maybe<Agent> trainer, boolean discardTrained, BinaryOperator<Agent> merger) {
        this.onlineAgent = requireNonNull(onlineAgent);
        this.trainingAgent = trainingAgent;
        this.trainer = trainer;
        this.discardTrained = discardTrained;
        this.merger = requireNonNull(merger);
    }

    /**
     * Changes the online agent
     *
     * @param operator the changer operator
     */
    public OnlineAgentStatus changeAgent(UnaryOperator<Agent> operator) {
        Agent agent = operator.apply(onlineAgent);
        return onlineAgent(agent);
    }

    /**
     * Sets the discard trained agent flag
     *
     * @param discardTrained true if the trained agent must be discarded
     */
    private OnlineAgentStatus discardTrained(boolean discardTrained) {
        return this.discardTrained != discardTrained
                ? new OnlineAgentStatus(onlineAgent, trainingAgent, trainer, discardTrained, merger)
                : this;
    }

    /**
     * Resets the current agent
     */
    public OnlineAgentStatus init() {
        Agent onlineAgent = this.onlineAgent.init();
        return onlineAgent(onlineAgent).discardTrained(true);
    }

    /**
     * Sets the merger operator
     *
     * @param merger the merger operator for the trained agent
     */
    public OnlineAgentStatus merger(BinaryOperator<Agent> merger) {
        return !Objects.equals(this.merger, requireNonNull(merger))
                ? new OnlineAgentStatus(onlineAgent, trainingAgent, trainer, discardTrained, merger)
                : this;
    }

    /**
     * Observes the result of agent-environment interaction
     * The resulting state can have the trainer process generated.
     * The client should test and subscribe the trainer()
     *
     * @param result the result
     */
    public OnlineAgentStatus observe(ExecutionResult result) {
        // Let the agent observe the result
        Agent currentAgent = onlineAgent.observe(result);
        if (!currentAgent.isReadyForTrain() || trainingAgent != null) {
            // Training skipped because is not ready or is already training
            return onlineAgent(currentAgent).trainer(null);
        }

        // Agent ready to train
        Agent trainingAgent = currentAgent.dup();
        Agent clearedAgent = currentAgent.clearTrajectory();
        // Create the training process
        Maybe<Agent> trainer = Maybe.fromSupplier(() ->
                train(trainingAgent));

        return onlineAgent(clearedAgent)
                .trainingAgent(trainingAgent)
                .trainer(trainer)
                .discardTrained(false);
    }

    /**
     * Sets the online agent
     *
     * @param onlineAgent the online agent
     */
    OnlineAgentStatus onlineAgent(Agent onlineAgent) {
        return !Objects.equals(this.onlineAgent, onlineAgent)
                ? new OnlineAgentStatus(onlineAgent, trainingAgent, trainer, discardTrained, merger)
                : this;
    }

    /**
     * Notifies the result of the training process
     *
     * @param trained the trained agent
     */
    public OnlineAgentStatus trained(Agent trained) {
        OnlineAgentStatus status1 = trainingAgent(null)
                .trainer(null)
                .discardTrained(false);
        if (discardTrained) {
            return status1;
        } else {
            trained = merger.apply(trained, onlineAgent);
        /* TODO
        long nextSave = this.nextSave;

        if (now >= nextSave) {
            trained.save();
            nextSave = now + saveInterval;
        }
        // Concurrent train the cleared agent
        return agent(trained).nextSave(nextSave);

         */
            return status1.onlineAgent(trained);
        }
    }

    /**
     * Sets the trainer process
     *
     * @param trainer the trainer process
     */
    private OnlineAgentStatus trainer(Maybe<Agent> trainer) {
        return !Objects.equals(this.trainer, trainer)
                ? new OnlineAgentStatus(onlineAgent, trainingAgent, trainer, discardTrained, merger)
                : this;
    }

    /**
     * Sets the training agent
     *
     * @param trainingAgent the training agent
     */
    private OnlineAgentStatus trainingAgent(Agent trainingAgent) {
        return !Objects.equals(this.trainingAgent, trainingAgent)
                ? new OnlineAgentStatus(onlineAgent, trainingAgent, trainer, discardTrained, merger)
                : this;
    }
}
