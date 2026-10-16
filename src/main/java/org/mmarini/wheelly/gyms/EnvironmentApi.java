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

package org.mmarini.wheelly.gyms;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.wheelly.controllers.RobotControllerApi;
import org.mmarini.wheelly.envs.InferenceConnector;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;

/**
 * Manages the interaction bridge between the underlying robot controller and the TD learning agent.
 * <p>
 * This interface unifies operational telemetry streaming, macro-action dispatching, inference mapping,
 * and multi-input signal specification definitions into a centralized execution lifecycle container
 * for autonomous robotic behaviors.
 * </p>
 */
public interface EnvironmentApi extends EnvironmentConnector, InferenceConnector, WithSignalsSpec {

    /**
     * Parses and instantiates a concrete {@code EnvironmentApi} implementation from a JSON document configuration block.
     * <p>
     * This utility utilises reflection mapping to inject the active physical or simulated controller api dependency
     * directly into the newly generated environment context block.
     * </p>
     *
     * @param config     the root JSON node containing the environment property tree schema
     * @param locator    the contextual locator pattern targeting the relative configuration scope
     * @param controller the active {@link RobotControllerApi} mapping the hardware layer
     * @return a fully initialised {@code EnvironmentApi} instance
     */
    static EnvironmentApi fromConfig(JsonNode config, Locator locator, RobotControllerApi controller) {
        return Utils.createObject(config, locator, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * Parses and instantiates a concrete {@code EnvironmentApi} implementation from a physical configuration file.
     * <p>
     * This utility reads structural parameters from the designated file path, injecting the provided
     * controller api dependency into the target constructor layout.
     * </p>
     *
     * @param file       the physical configuration file instance (e.g., YAML or JSON source)
     * @param controller the active {@link RobotControllerApi} mapping the hardware layer
     * @return a fully initialised {@code EnvironmentApi} instance
     * @throws Throwable if any instantiation, validation, or reflection processing errors occur
     */
    static EnvironmentApi fromFile(File file, RobotControllerApi controller) throws Throwable {
        return Utils.createObject(file, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * Parses and instantiates a concrete {@code EnvironmentApi} implementation from a physical configuration file
     * utilizing a default or parameterless constructor configuration sequence.
     *
     * @param file the physical configuration file instance (e.g., YAML or JSON source)
     * @return a fully initialised {@code EnvironmentApi} instance
     * @throws Throwable if any instantiation, validation, or reflection processing errors occur
     */
    static EnvironmentApi fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }

    /**
     * Registers or overrides the dynamic reward function responsible for calculating performance scalars
     * within the reinforcement learning execution loop.
     *
     * @param rewardFunc the targeted {@link RewardFunction} component to attach to this environment context
     */
    void setRewardFunc(RewardFunction rewardFunc);
}
