/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.envs;

import io.reactivex.rxjava3.core.Completable;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.apis.WithRobotStatus;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Implements general functionalities of RobotEnvironment
 * <p>
 * The inference is composed by the sequence
 * <ul>
 *     <li><code>latchStatus</code> stores the eventually composed status for inference processing</li>
 *     <li><code>getSignals</code> retrieves the signals of latched status</li>
 *     <li><code>onAct</code> asks for generation of action
 *     <li><code>processActions</code> processes the result actions
 *     <li><code>getReward</code> generates the result of environment/agent interaction from previous state and current
 *         state (may generate null)
 *     <li><code>onResult</code> observer the result
 *     <li><code>splitStatus</code> split the current status to previous status
 * </ul>
 */
public abstract class AbstractRobotEnv implements RobotEnvironment, WithRobotStatus {

    private final RobotControllerApi controller;
    private final ToDoubleFunction<RobotEnvironment> rewardFunc;
    private UnaryOperator<Map<String, Signal>> onAct;
    private Consumer<Environment.ExecutionResult> onResult;
    private Map<String, Signal> actions;
    private Map<String, Signal> signals0;
    private Consumer<RobotStatus> onInference;

    /**
     * Creates the abstract environment
     *
     * @param controller the controller
     * @param rewardFunc the reward function
     */
    protected AbstractRobotEnv(RobotControllerApi controller, ToDoubleFunction<RobotEnvironment> rewardFunc) {
        this.controller = requireNonNull(controller);
        this.rewardFunc = requireNonNull(rewardFunc);
        controller.setOnInference(this::handleInference);
        controller.setOnLatch(this::latchStatus);
    }

    /**
     * Returns the controller
     */
    @Override
    public RobotControllerApi getController() {
        return controller;
    }

    /**
     * Returns the signals from current status
     */
    protected abstract Map<String, Signal> getSignals();

    /**
     * Processes the inference to produce the behaviour
     *
     * @param status the current status
     */
    private void handleInference(RobotStatus status) {
        if (onInference != null) {
            onInference.accept(status);
        }
        Map<String, Signal> signals1 = getSignals();
        Map<String, Signal> actions1 = onAct.apply(signals1);
        processActions(actions1);
        if (signals0 != null) {
            double reward = rewardFunc.applyAsDouble(this);
            Environment.ExecutionResult result = new Environment.ExecutionResult(
                    signals0, actions, reward, signals1, false
            );
            if (onResult != null) {
                onResult.accept(result);
            }
        }
        // Split status
        signals0 = signals1;
        actions = actions1;
    }

    /**
     * Latches the eventually composed current status for inference processing
     *
     * @param status the current status
     */
    protected abstract void latchStatus(RobotStatus status);

    /**
     * Processes actions
     *
     * @param actions the actions
     */
    protected abstract void processActions(Map<String, Signal> actions);

    @Override
    public Completable readShutdown() {
        return controller.readShutdown();
    }

    @Override
    public void setOnAct(UnaryOperator<Map<String, Signal>> callback) {
        onAct = callback;
    }

    @Override
    public void setOnInference(Consumer<RobotStatus> callback) {
        onInference = callback;
    }

    @Override
    public void setOnResult(Consumer<Environment.ExecutionResult> callback) {
        onResult = callback;
    }

    @Override
    public void shutdown() {
        controller.shutdown();
    }

    @Override
    public void start() {
        controller.start();
    }
}
