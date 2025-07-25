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

package org.mmarini.wheelly.apps;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.WorldModeller;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;

import java.io.File;
import java.io.IOException;

public interface AppYaml {

    /**
     * Returns the controller from configuration file reading "robot" and "controller" properties
     *
     * @param config the root node
     */
    static RobotControllerApi controllerFromJson(JsonNode config) throws IOException {
        return RobotControllerApi.fromFile(new File(config.path("controller").asText()));
    }

    /**
     * Returns the environment reading the file of "environment" property
     *
     * @param config the json document
     */
    static EnvironmentApi envFromJson(JsonNode config) throws IOException {
        return EnvironmentApi.fromFile(new File(config.path("environment").asText()));
    }

    /**
     * Returns the double arrays from json document
     *
     * @param root    the JSON root
     * @param locator the array locator
     */
    static double[] loadDoubleArray(JsonNode root, Locator locator) {
        return locator.elements(root)
                .mapToDouble(l -> l.getNode(root).asDouble())
                .toArray();
    }

    /**
     * Returns the int arrays from JSON document
     *
     * @param root    the JSON root
     * @param locator the array locator
     */
    static int[] loadIntArray(JsonNode root, Locator locator) {
        return locator.elements(root)
                .mapToInt(l -> l.getNode(root).asInt())
                .toArray();
    }

    /**
     * Returns the world modeller reading the file of "modeller" property
     *
     * @param config the JSON configuration
     * @throws IOException in case of error
     */
    static WorldModeller modellerFromJson(JsonNode config) throws IOException {
        return WorldModeller.fromFile(new File(config.path("modeller").asText()));
    }

    /**
     * Returns the objective reading the file of "objective" property
     *
     * @param config the JSON configuration
     * @throws IOException in case of error
     */
    static RewardFunction rewardFromJson(JsonNode config) throws IOException {
        return RewardFunction.fromFile(new File(config.path("rewardFunction").asText()));
    }

    /**
     * Returns the world modeller reading the file of "robot" property
     *
     * @param config the JSON configuration
     * @throws IOException in case of error
     */
    static RobotApi robotFromJson(JsonNode config) throws IOException {
        return RobotApi.fromFile(new File(config.path("robot").asText()));
    }

}
