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
import org.mmarini.rl.envs.IntSignalSpec;
import org.mmarini.wheelly.apis.FuzzyFunctions;
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.RobotStatus;
import org.mmarini.wheelly.engines.ExploringState;
import org.mmarini.wheelly.envs.PolarRobotEnv;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.yaml.Locator;

import java.util.function.ToDoubleFunction;

import static org.mmarini.wheelly.apis.RobotApi.MAX_PPS;
import static org.mmarini.wheelly.apis.Utils.normalizeDegAngle;

/**
 * Explore objective
 */
public interface ExploreByImitation {
    /**
     * Returns the function that fuzzy rewards explore behavior from configuration
     *
     * @param root    the root json document
     * @param locator the locator
     */
    static ToDoubleFunction<RobotEnvironment> create(JsonNode root, Locator locator) {
        return explore(
                locator.path("sensorRange").getNode(root).asDouble(),
                locator.path("turnDirectionRange").getNode(root).asInt());
    }

    static ToDoubleFunction<RobotEnvironment> explore(double stopDistance, int turnDirectionRange) {
        return environment -> {

            PolarRobotEnv env = (PolarRobotEnv) environment;

            PolarRobotEnv.CompositeStatus compositeStatus = env.getCurrentStatus();
            RobotStatus status = compositeStatus.status;
            if (!status.canMoveBackward() || !status.canMoveForward()) {
                // Avoid contacts
                return -1;
            }
            boolean isHalt = env.isHalt(env.getPrevActions());
            int speedAction = env.speed(env.getPrevActions());
            int dirAction = env.moveDirection(env.getPrevActions(), status.getDirection());
            int sensorAction = env.sensorDir(env.getPrevActions());

            PolarMap polarMap = compositeStatus.polarMap;
            RobotCommands expActions = ExploringState.explore(status, polarMap, stopDistance, turnDirectionRange)._2;
            boolean expHalt = expActions.isHalt();
            int expMoveDir = expActions.moveDirection;
            int expScanDir = expActions.scanDirection;
            int expSpeed = expActions.speed;

            if (isHalt != expHalt) {
                return 0;
            }

            long speedNumber = ((IntSignalSpec) env.getActions().get("speed")).getNumValues() - 2;
            long dirNumber = ((IntSignalSpec) env.getActions().get("direction")).getNumValues() - 1;
            long sensDirNumber = ((IntSignalSpec) env.getActions().get("sensorAction")).getNumValues() - 1;
            double rightSpeed = FuzzyFunctions.equalZero(speedAction - expSpeed, MAX_PPS / speedNumber * 2);
            double rightDir = FuzzyFunctions.equalZero(normalizeDegAngle(dirAction - expMoveDir),
                    360D / dirNumber * 2);
            double rightSensor = FuzzyFunctions.equalZero(normalizeDegAngle(sensorAction - expScanDir),
                    180D / sensDirNumber * 2);
            /*
            double rightSpeed = FuzzyFunctions.equalZero(speedAction - expSpeed, MAX_PPS);
            double rightDir = FuzzyFunctions.equalZero(normalizeDegAngle(dirAction - expMoveDir),
                    45D);
            double rightSensor = FuzzyFunctions.equalZero(normalizeDegAngle(sensorAction - expScanDir),
                    45D);
             */

            double rightActions = FuzzyFunctions.and(rightSpeed, rightDir, rightSensor);
            return FuzzyFunctions.defuzzy(0, 1, rightActions);
        };
    }
}
