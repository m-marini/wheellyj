/*
 * Copyright (c) 2023-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.envs;

import org.mmarini.wheelly.gyms.GymAdapter;
import org.mmarini.wheelly.gyms.RewardFunction;
import org.mmarini.wheelly.gyms.SignalSpec;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.util.Map;

/**
 * Manages the interaction bridge between the underlying robot controller and the TD learning agent.
 * <p>
 * This interface unifies operational telemetry streaming, macro-action dispatching, inference mapping,
 * and multi-input signal specification definitions into a centralized execution lifecycle container
 * for autonomous robotic behaviors.
 * </p>
 */
public interface GymApi extends GymAdapter {

    /**
     * Parses and instantiates a concrete {@code EnvironmentApi} implementation from a physical configuration file
     * utilizing a default or parameterless constructor configuration sequence.
     *
     * @param file the physical configuration file instance (e.g., YAML or JSON source)
     * @return a fully initialised {@code EnvironmentApi} instance
     * @throws Throwable if any instantiation, validation, or reflection processing errors occur
     */
    static GymApi fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }

    /**
     * Sets the learning mode
     *
     * @param learning true if learning active
     */
    void learning(boolean learning);

    /**
     * Registers or overrides the dynamic reward function responsible for calculating performance scalars
     * within the reinforcement learning execution loop.
     *
     * @param rewardFunc the targeted {@link RewardFunctionOld} component to attach to this environment context
     */
    void setRewardFunc(RewardFunction rewardFunc);

    void validate(Map<String, SignalSpec> stateSpec, Map<String, SignalSpec> actionSpec);
}
