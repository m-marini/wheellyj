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
import org.mmarini.wheelly.apis.NullController;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.yaml.Locator;

import java.io.IOException;

import static java.lang.String.format;

public interface AppYaml {


    /**
     * Returns the controller from configuration file
     *
     * @param config  the root node
     * @param locator the configuration locator
     * @param schema  the schema
     */
    static RobotControllerApi controllerFromJson(JsonNode config, Locator locator, String schema) {
        JsonSchemas.instance().validateOrThrow(config, schema);
        Locator robotLocator = locator.path(locator.path("robot").getNode(config).asText());
        if (robotLocator.getNode(config).isMissingNode()) {
            throw new IllegalArgumentException(format("Missing node %s", robotLocator));
        }
        Locator controllerLocator = locator.path(locator.path("controller").getNode(config).asText());
        if (controllerLocator.getNode(config).isMissingNode()) {
            throw new IllegalArgumentException(format("Missing node %s", controllerLocator));
        }
        RobotApi robot = RobotApi.fromConfig(config, robotLocator);
        return RobotControllerApi.fromConfig(config, controllerLocator, robot);
    }

    /**
     * Returns the environment
     *
     * @param config  the json document
     * @param locator the config locator
     * @param schema  the schema
     */
    static RobotEnvironment envFromJson(JsonNode config, Locator locator, String schema) {
        RobotControllerApi controller = controllerFromJson(config, locator, schema);
        Locator envLocator = locator.path(locator.path("environment").getNode(config).asText());
        if (envLocator.getNode(config).isMissingNode()) {
            throw new IllegalArgumentException(format("Missing node %s", envLocator));
        }
        return RobotEnvironment.fromConfig(config, envLocator, controller);
    }

    /**
     * Returns the environment
     *
     * @param config  the json document
     * @param locator the config locator
     * @param schema  the schema
     */
    static RobotEnvironment envNullControllerFromJson(JsonNode config, Locator locator, String schema) {
        JsonSchemas.instance().validateOrThrow(config, schema);
        Locator envLocator = locator.path(locator.path("environment").getNode(config).asText());
        if (envLocator.getNode(config).isMissingNode()) {
            throw new IllegalArgumentException(format("Missing node %s", envLocator));
        }
        return RobotEnvironment.fromConfig(config, envLocator, new NullController());
    }

    /**
     * Returns the controller from configuration file
     *
     * @param file the file
     */
    static RobotControllerApi fromFile(String file, String schema) {
        try {
            JsonNode config = org.mmarini.yaml.Utils.fromFile(file);
            return controllerFromJson(config, Locator.root(), schema);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the double arrays from json document
     *
     * @param root    the json root
     * @param locator the array locator
     */
    static double[] loadDoubleArray(JsonNode root, Locator locator) {
        return locator.elements(root)
                .mapToDouble(l -> l.getNode(root).asDouble())
                .toArray();
    }

    /**
     * Returns the int arrays from json document
     *
     * @param root    the json root
     * @param locator the array locator
     */
    static int[] loadIntArray(JsonNode root, Locator locator) {
        return locator.elements(root)
                .mapToInt(l -> l.getNode(root).asInt())
                .toArray();
    }
}
