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
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.rl.envs.ExecutionResult;
import org.mmarini.rl.envs.Signal;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * Mediates the act and training for online training (parallel processing)
 */
public class OnLineAgent implements Agent, AutoCloseable {
    private final AtomicReference<OnlineAgentStatus> status;
    private final long saveInterval;
    private long lastSaveTime;

    /**
     * Creates the mediator
     *
     * @param initialAgent the initial agent
     */
    public OnLineAgent(Agent initialAgent, long saveInterval) {
        this.status = new AtomicReference<>(OnlineAgentStatus.create(initialAgent));
        this.saveInterval = saveInterval;
        this.lastSaveTime = System.currentTimeMillis();
    }

    @Override
    public Map<String, Signal> act(Map<String, Signal> state) {
        return status.get().onlineAgent().act(state);
    }

    @Override
    public void backup() {
    }

    /**
     * Changes the online agent
     *
     * @param changer the changer
     */
    public void changeAgent(UnaryOperator<Agent> changer) {
        status.updateAndGet(s -> s.changeAgent(changer));
    }

    @Override
    public Agent clearTrajectory() {
        status.updateAndGet(s -> s.onlineAgent(s.onlineAgent().clearTrajectory()));
        return this;
    }

    @Override
    public void close() {
        status.get().onlineAgent().close();
    }

    @Override
    public OnLineAgent dup() {
        return this;
    }

    /**
     * Resets the current online agent
     */
    public Agent init() {
        status.updateAndGet(OnlineAgentStatus::init);
        return this;
    }

    @Override
    public boolean isReadyForTrain() {
        return status.get().onlineAgent().isReadyForTrain();
    }

    /**
     * Sets the merger operator
     *
     * @param merger the merger operator for the trained agent
     */
    public OnLineAgent merger(BinaryOperator<Agent> merger) {
        status.updateAndGet(s -> s.merger(merger));
        return this;
    }

    @Override
    public Agent observe(ExecutionResult result) {
        // Observe the result
        Maybe<Agent> tr = status.updateAndGet(s -> s.observe(result)).trainer();
        if (tr != null) {
            // Runs the training task and record the result
            tr.subscribeOn(Schedulers.computation())
                    .subscribe(trained -> {
                        Agent agent = status.updateAndGet(s -> s.trained(trained)).onlineAgent();
                        if (agent instanceof AgentRL agentRl) {
                            long t0 = System.currentTimeMillis();
                            if (t0 >= lastSaveTime + saveInterval) {
                                agentRl.save();
                                lastSaveTime = t0;
                            }
                        }
                    });
        }
        return this;
    }

    /**
     * Returns the online agent
     */
    public Agent onlineAgent() {
        return status.get().onlineAgent();
    }

    @Override
    public void save() {
    }

    @Override
    public Agent trainByTrajectory() {
        return this;
    }
}
