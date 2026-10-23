/*
 * Copyright (c) 2022-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.objectives;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mmarini.wheelly.TestFunctions;
import org.mmarini.wheelly.behaviours.BehaviourState;
import org.mmarini.wheelly.behaviours.HeadActionId;
import org.mmarini.wheelly.behaviours.MacroActionsState;
import org.mmarini.wheelly.behaviours.MoveActionId;
import org.mmarini.wheelly.gyms.RewardFunction;
import org.mmarini.wheelly.perception.WorldModelBuilder;
import org.mmarini.wheelly.utils.Angle;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;

import java.awt.geom.Point2D;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;

class SensorLabelTest {

    static BehaviourState createState(int headDeg, double leftPps, double rightPps, double distance, int markerId) {
        WorldModelBuilder builder = new WorldModelBuilder()
                .headAngle(headDeg)
                .frontDistance(distance)
                .robotSpeed(leftPps, rightPps);
        if (markerId >= 0) {
            Point2D markerLocation = Angle.fromDeg(headDeg).at(new Point2D.Double(), distance);
            builder = builder.addMarker(markerId, markerLocation);
        }
        return new MacroActionsState(builder.build(),
                MoveActionId.HALT_ACTION,
                HeadActionId.LOOK_STRAIGHT_ACTION);
    }

    @ParameterizedTest(
            name = "[{index}] head {1} DEG, pps={2},{3}, lidar at {4} m, markerId={5}")
    @CsvSource({
            "2, 0, 0,0, 0.8, 0",
            "2, 29, 0,0, 0.8, 0", // sensor in range
            "2, 331, 0,0, 0.8, 0", // sensor in range
            "2, 0, 4.9,4.9, 0.8, 0", // power in range
            "2, 0, -4.9,-4.9, 0.8, 0", // power in range
            "2, 0, 0,0, 0.51, 0", // distance in range
            "2, 0, 0,0, 0.99, 0", // distance in range

            "0, 0, 0,0, 0.8, -1", // unknown camera signal
            "0, 31, 0,0, 0.8, 0", // sensor out of range
            "0, 329, 0,0, 0.8, 0", // sensor out of range
            "0, 0, 0,0, 0.4, 0", // too near
            "0, 0, 0,0, 1.1, 0", // too far
            "0, 0, 5.1,0, 0.8, 0", // power out of range
            "0, 0, -5.1,0, 0.8, 0", // power out of range
            "0, 0, 0,5.1, 0.8, 0", // power out of range
            "0, 0, 0,-5.1, 0.8, 0", // power out of range
    })
    void create(double expectedReward,
                int headDir,
                double leftPps, double rightPps,
                double distance,
                int markerId) throws IOException {
        JsonNode root = Utils.fromText(TestFunctions.text("---",
                "$schema: " + SensorLabel.SCHEMA_NAME,
                "class: " + SensorLabel.class.getName(),
                "minDistance: 0.5",
                "maxDistance: 1",
                "velocityThreshold: 5",
                "sensorRange: 30",
                "reward: 2"
        ));
        BehaviourState state = createState(headDir, leftPps, rightPps,
                distance, markerId);
        RewardFunction f = SensorLabel.create(root, Locator.root());

        double result = f.reward(null, null, state);

        assertThat(result, closeTo(expectedReward, 1e-4));
    }
}