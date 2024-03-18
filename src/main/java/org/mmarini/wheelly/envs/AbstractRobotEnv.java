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
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.*;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * Implements general functionalities of RobotEnvironment
 * <p>
 * The environment should implements
 * <ul>
 *     <li><code>onStatus</code> to process the status event</li>
 *     <li><code>latchStatus</code> to generate eventually composed status and store as current status</li>
 *     <li><code>getSignals</code> to generate the signals from current composed status</li>
 *     <li><code>processActions</code> to process the generated actions</li>
 *     <li><code>splitStatus</code> to split current composed status to previous status for next inference process</li>
 * </ul>
 */
public abstract class AbstractRobotEnv implements RobotEnvironment, WithRobotStatus {
    public static final int MIN_DIRECTION_ACTION = -180;
    public static final int MAX_DIRECTION_ACTION = 180;
    public static final int MIN_SENSOR_DIR = -90;
    public static final int MAX_SENSOR_DIR = 90;
    private final RobotControllerApi controller;
    private final ToDoubleFunction<RobotEnvironment> rewardFunc;
    private UnaryOperator<Map<String, Signal>> onAct;
    private Consumer<RobotStatus> onInference;
    private Consumer<Environment.ExecutionResult> onResult;
    private Map<String, Signal> prevActions;
    private Map<String, Signal> signals0;

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
        readRobotStatus().doOnNext(this::handleStatus).subscribe();
    }

    /**
     * Returns the delta direction
     *
     * @param actions the actions
     */
    Complex deltaDir(Map<String, Signal> actions) {
        int action = actions.get("direction").getInt(0);
        int n = ((IntSignalSpec) getActions().get("direction")).numValues();
        return Complex.fromDeg(linear(action,
                0, n - 1,
                MIN_DIRECTION_ACTION, MAX_DIRECTION_ACTION));
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
     * The inference is composed by the sequence
     * <ul>
     *     <li><code>getSignals</code> retrieves the signals of latched status</li>
     *     <li><code>onAct</code> asks the agent for generation of actions for current state</li>
     *     <li><code>processActions</code> processes the result actions</li>
     *     <li><code>getReward</code> generates the rewards from previous state, previous actions and current state</li>
     *     <li><code>onResult</code> invokes the agent observer for agent training</li>
     *     <li><code>splitStatus</code> split the current status to previous status</li>
     * </ul>
     *
     * @param status the current status
     */
    protected void handleInference(RobotStatus status) {
        if (onInference != null) {
            onInference.accept(status);
        }
        Map<String, Signal> signals1 = getSignals();
        Map<String, Signal> actions1 = onAct.apply(signals1);
        processActions(actions1);
        if (signals0 != null) {
            double reward = rewardFunc.applyAsDouble(this);
            Environment.ExecutionResult result = new Environment.ExecutionResult(
                    signals0, prevActions, reward, signals1, false
            );
            if (onResult != null) {
                onResult.accept(result);
            }
        }
        // Split status
        signals0 = signals1;
        prevActions = actions1;
        splitStatus();
    }

    /**
     * Handles status event.
     * Invokes the <code>onStatus</code> method and then the eventually <code>onStatusReady</code> registered call back
     *
     * @param status the roboto status
     */
    protected void handleStatus(RobotStatus status) {
        onStatus(status);
    }

    public boolean isHalt(Map<String, Signal> actions) {
        int speedAction = actions.get("speed").getInt(0);
        int n = ((IntSignalSpec) getActions().get("speed")).numValues();
        return speedAction == n - 1;
    }

    /**
     * Latches the eventually composed current status for inference processing
     *
     * @param status the current status
     */
    protected void latchStatus(RobotStatus status) {
    }

    public Complex moveDirection(Map<String, Signal> actions, Complex currentDirection) {
        Complex dDir = deltaDir(actions);
        return currentDirection.add(dDir);
    }

    /**
     * Process status event
     *
     * @param status the robot status
     */
    protected void onStatus(RobotStatus status) {
    }

    /**
     * Processes actions generated by agent
     *
     * @param actions the actions
     */
    protected void processActions(Map<String, Signal> actions) {
        RobotControllerApi controller = getController();
        Complex sensorDirection = sensorDir(actions);
        RobotCommands command = isHalt(actions)
                ? RobotCommands.haltAndScan(sensorDirection)
                : RobotCommands.moveAndScan(moveDirection(actions, getRobotStatus().direction()),
                speed(actions),
                sensorDirection);
        controller.execute(command);
    }

    @Override
    public Completable readShutdown() {
        return controller.readShutdown();
    }

    /**
     * Returns the sensor direction from actions
     *
     * @param actions the actions
     */
    public Complex sensorDir(Map<String, Signal> actions) {
        int action = actions.get("sensorAction").getInt(0);
        int n = ((IntSignalSpec) getActions().get("sensorAction")).numValues();
        return Complex.fromDeg(linear(action,
                0, n - 1,
                MIN_SENSOR_DIR, MAX_SENSOR_DIR));
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

    public int speed(Map<String, Signal> actions) {
        int speedAction = actions.get("speed").getInt(0);
        int n = ((IntSignalSpec) getActions().get("speed")).numValues();
        return round(linear(speedAction,
                0, n - 2,
                -MAX_PPS, MAX_PPS));
    }

    /**
     * Splits current composed status to previous status for next inference process
     */
    protected void splitStatus() {
    }

    @Override
    public void start() {
        controller.start();
    }
}
