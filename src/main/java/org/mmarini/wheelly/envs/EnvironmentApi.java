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

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.wheelly.apis.InferenceConnector;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.io.File;
import java.io.IOException;

/**
 * Manages the interaction between robot controller and TD agent
 */
public interface EnvironmentApi extends EnvironmentConnector, InferenceConnector, WithSignalsSpec {

    /**
     * Returns the robot environment from configuration
     *
     * @param config     the json document
     * @param locator    the configuration locator
     * @param controller the controller
     */
    static EnvironmentApi fromConfig(JsonNode config, Locator locator, RobotControllerApi controller) {
        return Utils.createObject(config, locator, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * Returns the robot environment from configuration
     *
     * @param file       the configuration file
     * @param controller the controller
     * @throws IOException in case of error
     */
    static EnvironmentApi fromFile(File file, RobotControllerApi controller) throws Throwable {
        return Utils.createObject(file, new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    /**
     * @param file the configuration file
     * @throws IOException in case of error
     */
    static EnvironmentApi fromFile(File file) throws Throwable {
        return Utils.createObject(file);
    }


    /**
     * Sets the reward function
     *
     * @param rewardFunc the reward function
     */
    void setRewardFunc(RewardFunction rewardFunc);
}
