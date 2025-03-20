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

package org.mmarini.wheelly.envs;

import org.mmarini.rl.agents.AgentConnector;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.apis.WorldModellerConnector;

import java.util.Map;

/**
 *
 */
public interface EnvironmentConnector {


    /**
     * Returns the robot commands for the chosen actions
     *
     * @param state   the current state
     * @param actions the chosen actions
     */
    RobotCommands command(State state, Map<String, Signal> actions);

    /**
     * Connects the word modeller connector
     *
     * @param connector the connector;
     */
    void connect(WorldModellerConnector connector);

    /**
     * Connects the RL agent
     *
     * @param agent the RL agent
     */
    void connect(AgentConnector agent);

    /**
     * Returns the reward for the initial state, the chosen action and the final state
     *
     * @param state0  the initial state
     * @param actions the chosen actions
     * @param state1  the final state
     */
    double reward(State state0, Map<String, Signal> actions, State state1);

    /**
     * Returns the state for the given world model
     *
     * @param model the world model
     */
    State state(WorldModel model);
}
