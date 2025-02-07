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
import org.mmarini.rl.envs.*;
import org.mmarini.wheelly.apis.*;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.lang.Math.round;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.linear;

/**
 * Implements general functionalities of RobotEnvironment
 * <p>
 * The environment should implement
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
    private final RewardFunction rewardFunc;
    private final int numSpeeds;
    private final int numDirections;
    private final Map<String, SignalSpec> actionsSpec;
    private UnaryOperator<Map<String, Signal>> onAct;
    private Consumer<RobotStatus> onInference;
    private Consumer<Environment.ExecutionResult> onResult;
    private Map<String, Signal> signals0;
    private State prevState;
    private State currentState;
    private Map<String, Signal> prevActions;


    /**
     * Creates the abstract environment
     *
     * @param controller          the controller
     * @param rewardFunc          the reward function
     * @param numSpeeds           the number of move action speeds
     * @param numDirections       the number of move action directions
     * @param numSensorDirections the number of sensor directions
     */
    protected AbstractRobotEnv(RobotControllerApi controller, RewardFunction rewardFunc, int numSpeeds, int numDirections, int numSensorDirections) {
        this.controller = requireNonNull(controller);
        this.rewardFunc = requireNonNull(rewardFunc);
        this.numSpeeds = numSpeeds;
        this.numDirections = numDirections;
        this.actionsSpec = Map.of(
                "move", new IntSignalSpec(new long[]{1}, numDirections * numSpeeds),
                "sensorAction", new IntSignalSpec(new long[]{1}, numSensorDirections)
        );
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
        int moveAction = actions.get("move").getInt(0);
        int dirAction = moveAction / numSpeeds;
        return Complex.fromDeg(linear(dirAction,
                0, numDirections,
                MIN_DIRECTION_ACTION, MAX_DIRECTION_ACTION));
    }

    @Override
    public Map<String, SignalSpec> getActions() {
        return this.actionsSpec;
    }

    /**
     * Returns the controller
     */
    @Override
    public RobotControllerApi getController() {
        return controller;
    }

    /**
     * Returns the current state
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * Sets the current state
     *
     * @param currentState the current state
     */
    protected void setCurrentState(State currentState) {
        this.currentState = currentState;
    }

    @Override
    public RobotStatus getRobotStatus() {
        return ((WithRobotStatus) currentState).getRobotStatus();
    }

    /**
     * Returns the halt action
     */
    public Map<String, Signal> haltActions() {
        Signal speedSignal = IntSignal.create(((IntSignalSpec) getActions().get("speed")).numValues() - 1);
        Signal sensorAction = IntSignal.create(
                ((IntSignalSpec) getActions().get("sensorAction")).numValues() / 2);
        Signal directionAction = IntSignal.create(
                ((IntSignalSpec) getActions().get("direction")).numValues() / 2);
        return Map.of("speed", speedSignal,
                "direction", directionAction,
                "sensorAction", sensorAction);
    }

    /**
     * Processes the inference to produce the behavior
     * The inference is composed by the sequence
     * <ul>
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

        Map<String, Signal> signals1 = currentState.signals();
        Map<String, Signal> actions1 = onAct.apply(signals1);
/*
        if (signals0 != null) {
            int canMoveState = signals0.get("canMoveStates").getInt(0);
            if ((canMoveState == 0 || canMoveState == 2 || canMoveState == 4)
                    && speed(actions1) > 0 && deltaDir(actions1).isFront(0.999)) {
                int speed = speed(actions1);
                Complex dir = deltaDir(actions1);
                int speed1 = speed;
            }
        }


 */
        processActions(actions1);
        if (prevState != null) {
            double reward = rewardFunc.apply(prevState, prevActions, currentState);
            Environment.ExecutionResult result = new Environment.ExecutionResult(
                    signals0, prevActions, reward, signals1
            );
            if (onResult != null) {
                onResult.accept(result);
            }
        }
        // Split status
        prevState = currentState;
        signals0 = signals1;
        prevActions = actions1;
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

    /**
     * Returns true if the actions in halt
     *
     * @param actions the actions
     */
    public boolean isHalt(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0);
        // (numSpeeds / 2 + (numDirections * numSpeeds) / 2);
        int haltAction = (numSpeeds * (numDirections + 1)) / 2;
        return moveAction == haltAction;
    }

    /**
     * Latches the eventually composed current status for inference processing
     *
     * @param status the current status
     */
    protected void latchStatus(RobotStatus status) {
    }

    /**
     * Returns the robot direction from action signals
     *
     * @param actions          the action signals
     * @param currentDirection the current robot direction
     */
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

    /**
     * Returns the speed (pps) from the action signals
     *
     * @param actions the action signals
     */
    public int speed(Map<String, Signal> actions) {
        int moveAction = actions.get("move").getInt(0);
        int actionSpeed = moveAction % numSpeeds;
        return round(linear(actionSpeed,
                0, numSpeeds - 1,
                -MAX_PPS, MAX_PPS));
    }

    @Override
    public void start() {
        controller.start();
    }
}
