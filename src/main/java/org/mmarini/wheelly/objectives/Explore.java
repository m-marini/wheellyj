/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.objectives;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.apis.MapCell;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apps.JsonSchemas;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WithRadarMap;
import org.mmarini.yaml.Locator;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * The explore objective returns
 * a negative reward (-1) if the robot comes into contact with obstacles
 * otherwise returns a positive reward (0 ... 1)
 * the higher the number of known cells of the radar view
 * and whether it is moving forward
 * and whether the position of the proximity sensor is frontal in a given angle range
 */
public interface Explore {

    String SCHEMA_NAME = "https://mmarini.org/wheelly/objective-explore-schema-0.2";

    /**
     * Returns the function that fuzzy rewards explore behavior from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static RewardFunction create(JsonNode root, Locator locator) {
        JsonSchemas.instance().validateOrThrow(locator.getNode(root), SCHEMA_NAME);
        double reward = locator.path("reward").getNode(root).asDouble(1d);
        return explore(reward);
    }

    /**
     * Returns the function of reward for the given environment
     */
    static RewardFunction explore(double reward) {
        return (s0, a, s1) -> {
            if (s0 instanceof WithRadarMap withRadar0
                    && s1 instanceof WithRadarMap withRadar1) {
                RadarMap radarMap0 = withRadar0.getRadarMap();
                RadarMap radarMap1 = withRadar1.getRadarMap();
                long knownSectors0Number = Arrays.stream(radarMap0.cells())
                        .filter(Predicate.not(MapCell::unknown))
                        .count();
                long knownSector10Number = Arrays.stream(radarMap1.cells())
                        .filter(Predicate.not(MapCell::unknown))
                        .count();
                if (knownSector10Number > knownSectors0Number) {
                    return reward;
                }
            }
            return 0;
        };
    }

}
