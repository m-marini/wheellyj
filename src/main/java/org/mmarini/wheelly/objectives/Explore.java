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
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.mmarini.wheelly.apis.MapSector;
import org.mmarini.wheelly.apis.WheellyStatus;
import org.mmarini.yaml.schema.Locator;

import java.util.Arrays;

import static org.mmarini.wheelly.apis.FuzzyFunctions.and;
import static org.mmarini.wheelly.apis.FuzzyFunctions.defuzzy;
import static org.mmarini.wheelly.apis.SimRobot.MAX_VELOCITY;
import static org.mmarini.wheelly.apis.Utils.clip;

/**
 * Explore objective
 */
public interface Explore {
    /**
     * Returns the function that fuzzy rewards explore behavior from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static FloatFunction<WheellyStatus> create(JsonNode root, Locator locator) {
        return Explore::explore;
    }

    /**
     * Returns the function that fuzzy rewards explore behavior from configuration
     *
     * @param status the status
     */
    static float explore(WheellyStatus status) {
        if (!status.getCanMoveBackward() || !status.getCanMoveForward()) {
            // Avoid contacts
            return -1;
        }
        MapSector[] map = status.getRadarMap().getMap();
        long knownSectorsNumber = Arrays.stream(map).filter(MapSector::isKnown).count();
        // encourages the exploration of unfamiliar areas
        double isKnown = ((double) knownSectorsNumber) / map.length;
        // encourages the linear speed
        double linSpeed = (status.getRightSpeed() + status.getLeftSpeed()) / MAX_VELOCITY / 2;
        double isLinearSpeed = clip(linSpeed, 0, 1);
        return (float) defuzzy(0, 1, and(isLinearSpeed, isKnown));
    }
}
