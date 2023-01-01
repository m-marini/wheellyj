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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.apis.MapSector;
import org.mmarini.wheelly.apis.RadarMap;
import org.mmarini.wheelly.apis.WheellyStatus;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;

import java.awt.geom.Point2D;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.mmarini.wheelly.apis.SimRobot.MAX_VELOCITY;

class ExploreTest {

    static WheellyStatus createStatus(int sensorDir, double leftSpeed, double rightSpeed, int known, boolean canMoveForward, boolean canMoveBackward) {
        RadarMap radarMap = RadarMap.create(10, 10, new Point2D.Float(), 0.2F);
        MapSector[] map = radarMap.getSectors();
        long timestamp = System.currentTimeMillis();
        for (int i = 0; i < known; i++) {
            map[i].setTimestamp(timestamp);
        }

        return new WheellyStatus(0, new Point2D.Float(), 0,
                sensorDir, 0,
                leftSpeed * MAX_VELOCITY, rightSpeed * MAX_VELOCITY, 0, 0,
                canMoveForward, canMoveBackward, 0, false,
                0, radarMap);
    }

    @ParameterizedTest
    @CsvSource({
            "-1,0,0,0,0,0,1",
            "-1,0,0,0,0,1,0",
            "-1,0,0,0,0,0,0",
            "0,0,0,0,0,1,1",
            "0,0,0,0,100,1,1",
            "0,0,-1,1,100,1,1",
            "0,0,1,-1,100,1,1",
            "1,0,1,1,100,1,1",
            "0,0,-1,-1,100,1,1",
            "0.5,0,1,1,50,1,1",
            "0.5,0,1,0,100,1,1",
            "0.5,0,0,1,100,1,1",
            "0,0,0,-1,100,1,1",
            "0,60,1,1,100,1,1",
            "0.5,30,1,1,100,1,1",
    })
    void create(float expected,
                int sensorDir,
                double leftSpeed,
                double rightSpeed,
                int knownCount,
                int canMoveForward,
                int canMoveBackward) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "sensorRange: 60"));
        FloatFunction<WheellyStatus> f = Explore.create(root, Locator.root());
        WheellyStatus status = createStatus(sensorDir, leftSpeed, rightSpeed, knownCount, canMoveForward != 0, canMoveBackward != 0);
        float result = f.floatValueOf(status);
        assertThat((double) result, closeTo(expected, 1e-6));
    }
}