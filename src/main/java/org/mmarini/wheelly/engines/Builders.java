/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import com.fasterxml.jackson.databind.JsonNode;
import org.mmarini.wheelly.engines.statemachine.*;
import org.mmarini.wheelly.model.InferenceEngine;
import org.mmarini.wheelly.swing.RxJoystick;
import org.mmarini.wheelly.swing.RxJoystickImpl;
import org.mmarini.yaml.schema.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.Random;

import static org.mmarini.wheelly.engines.statemachine.ContextOperator.*;
import static org.mmarini.wheelly.engines.statemachine.EngineStatus.*;
import static org.mmarini.wheelly.engines.statemachine.FindPathStatus.*;
import static org.mmarini.wheelly.engines.statemachine.GotoStatus.UNREACHABLE_EXIT;
import static org.mmarini.wheelly.engines.statemachine.NextSequenceStatus.TARGET_SELECTED_EXIT;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TARGET_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TIMEOUT_KEY;
import static org.mmarini.wheelly.engines.statemachine.Yaml.*;

public interface Builders {

    Logger logger = LoggerFactory.getLogger(Builders.class);

    static InferenceEngine avoidObstacle(JsonNode config, Locator locator) {
        return StateMachineBuilder.create()
                .setParams("init.timeout", 2000)
                .addState(StopStatus.create("init"))
                .addState(RandomScanStatus.create("scan"))
                .addState(SecureStatus.create("avoid"))
                .addState(WaitForUnblockedStatus.create("unblock"))
                .addTransition("init", TIMEOUT_EXIT, "scan")
                .addTransition("scan", OBSTACLE_EXIT, "avoid")
                .addTransition("scan", COMPLETED_EXIT, "avoid")
                .addTransition("avoid", COMPLETED_EXIT, "scan")
                .addTransition("avoid", BLOCKED_EXIT, "unblock")
                .addTransition("unblock", COMPLETED_EXIT, "scan")

                .build("init");
    }

    static InferenceEngine findPath(JsonNode config, Locator locator) {
        pathEngine().apply(locator).accept(config);
        List<Point2D> targets = points(config.path("targets"));
        double safeDistance = config.path("safeDistance").asDouble(FindPathStatus.DEFAULT_SAFE_DISTANCE);
        double thresholdDistance = config.path("thresholdDistance").asDouble(GotoStatus.DEFAULT_DISTANCE);
        return StateMachineBuilder.create()
                .setParams("initial.timeout", 2000)
                .setParams("nextTarget.list", targets)
                .setParams("findPath.safeDistance", safeDistance)
                .setParams("goto.distance", thresholdDistance)
                .setParams("goto.timeout", 30000)
                .addState(StopStatus.create("initial"))
                .addState(NextSequenceStatus.create("nextTarget"))
                .addState(SingleScanStatus.create("scan"))
                .addState(FindPathStatus.create("findPath"))
                .addState(NextSequenceStatus.create("nextPoint"))
                .addState(GotoStatus.create("goto"))

                .addState(SecureStatus.create("initialAvoid"))

                .addState(SecureStatus.create("avoid"))

                .addTransition("initial", TIMEOUT_EXIT, "nextTarget")
                .addTransition("nextTarget", TARGET_SELECTED_EXIT, "scan",
                        assign("nextTarget.target", TARGET_KEY))
                .addTransition("scan", COMPLETED_EXIT, "findPath",
                        assign("findPath.target", "nextTarget.target"))
                .addTransition("findPath", COMPLETED_EXIT, "nextPoint",
                        ContextOperator.sequence(
                                assign("nextPoint.list", PATH_KEY),
                                assignValue("nextPoint.index", 0)
                        ))
                .addTransition("nextPoint", TARGET_SELECTED_EXIT, "goto")
                .addTransition("goto", COMPLETED_EXIT, "nextPoint")

                .addTransition("scan", OBSTACLE_EXIT, "initialAvoid")
                .addTransition("initialAvoid", COMPLETED_EXIT, "scan")

                .addTransition("findPath", TARGET_REACHED_EXIT, "nextTarget")
                .addTransition("findPath", NO_PATH_EXIT, "nextTarget")

                .addTransition("nextPoint", COMPLETED_EXIT, "nextTarget")

                .addTransition("goto", OBSTACLE_EXIT, "avoid")
                .addTransition("avoid", COMPLETED_EXIT, "scan")

                .addTransition("goto", UNREACHABLE_EXIT, "scan")

                .build("initial");
//        throw new Error("Not implemented");
    }

    static InferenceEngine follow(JsonNode config, Locator locator) {
        return StateMachineBuilder.create()
                .setParams("init.timeout", 2000)
                .addState(StopStatus.create("init"))
                .addState(SingleScanStatus.create("scan"))
                .addState(NearestObstacleStatus.create("nearest"))
                .addState(FollowStatus.create("follow"))

                .addState(SecureStatus.create("secure"))

                .addState(WaitForUnblockedStatus.create("unblock"))

                .addTransition("init", TIMEOUT_EXIT, "scan")
                .addTransition("scan", COMPLETED_EXIT, "nearest")
                .addTransition("nearest", COMPLETED_EXIT, "follow")

                .addTransition("scan", BLOCKED_EXIT, "unblock")

                .addTransition("scan", OBSTACLE_EXIT, "secure")
                .addTransition("secure", COMPLETED_EXIT, "nearest")

                .addTransition("follow", BLOCKED_EXIT, "unblock")
                .addTransition("follow", OBSTACLE_EXIT, "secure")

                .addTransition("secure", BLOCKED_EXIT, "unblock")

                .addTransition("unblock", COMPLETED_EXIT, "scan")

                .build("init");
    }

    static InferenceEngine gotoTest(JsonNode config, Locator locator) {
        point().apply(locator.path("target")).accept(config);
        Point2D target = point(config.path("target")).orElseThrow();
        return StateMachineBuilder.create()
                .setParams(TARGET_KEY, target)
                .setParams("initial.timeout", 2000)
                .addState(StopStatus.create("initial"))
                .addState(GotoStatus.create("goto"))
                .addTransition("initial", TIMEOUT_EXIT, "goto")
                .build("initial");
    }

    static InferenceEngine manual(JsonNode config, Locator locator) {
        String port = config.path("joystickPort").asText();
        if (port.isEmpty()) {
            throw new IllegalArgumentException("Missing joystickPort");
        }
        RxJoystick joystick = RxJoystickImpl.create(port);
        return new ManualEngine(joystick);
    }

    static InferenceEngine randomPath(JsonNode config, Locator locator) {
        Yaml.randomPath().apply(locator).accept(config);

        StateMachineBuilder builder1 = StateMachineBuilder.create()
                .addState(StopStatus.create("initial"))
                .addState(SingleScanStatus.create("scanForTarget"))
                .addState(SecureStatus.create("avoid1"))
                .addState(RandomTargetStatus.create("random", new Random()))
                .addState(FindPathStatus.create("findPath"))
                .addState(NextSequenceStatus.create("next"))
                .addState(GotoStatus.create("goto"))
                .addState(SecureStatus.create("avoid2"))
                .addState(SingleScanStatus.create("scanForPath"))

                .addTransition("initial", TIMEOUT_EXIT, "scanForTarget",
                        assignValue(TIMEOUT_KEY, 10000))

                .addTransition("scanForTarget", COMPLETED_EXIT, "random")
                .addTransition("scanForTarget", OBSTACLE_EXIT, "avoid1")

                .addTransition("avoid1", COMPLETED_EXIT, "scanForPath")

                .addTransition("random", COMPLETED_EXIT, "findPath")

                .addTransition("findPath", COMPLETED_EXIT, "next",
                        ContextOperator.sequence(
                                assign(NextSequenceStatus.LIST_KEY, FindPathStatus.PATH_KEY),
                                remove(NextSequenceStatus.INDEX_KEY)))
                .addTransition("findPath", NO_PATH_EXIT, "random")
                .addTransition("findPath", TARGET_REACHED_EXIT, "random")

                .addTransition("next", TARGET_SELECTED_EXIT, "goto")
                .addTransition("next", COMPLETED_EXIT, "scanForTarget")

                .addTransition("goto", COMPLETED_EXIT, "next")
                .addTransition("goto", OBSTACLE_EXIT, "avoid2")
                .addTransition("goto", UNREACHABLE_EXIT, "findPath")

                .addTransition("avoid2", COMPLETED_EXIT, "scanForPath")

                .addTransition("scanForPath", COMPLETED_EXIT, "findPath")
                .addTransition("scanForPath", OBSTACLE_EXIT, "avoid2")

                .setParams(TIMEOUT_KEY, 2000);

        JsonNode rangeNode = config.path("maxDistance");
        StateMachineBuilder builder2 = rangeNode.isMissingNode()
                ? builder1
                : builder1.setParams(RandomTargetStatus.MAX_DISTANCE_KEY, rangeNode.asDouble());
        StateMachineBuilder builder3 = point(config.path("center"))
                .map(center -> builder2.setParams(RandomTargetStatus.CENTER_KEY, center))
                .orElse(builder2);
        return builder3.build("initial");
    }

    static InferenceEngine sequence(JsonNode config, Locator locator) {
        points().apply(locator.path("targets")).accept(config);
        List<Point2D> targets = points(config.path("targets"));
        return StateMachineBuilder.create()
                .setParams(NextSequenceStatus.LIST_KEY, targets)
                .addState(SingleScanStatus.create("scan"))
                .addState(NextSequenceStatus.create("nextTarget"))
                .addState(GotoStatus.create("goto"))
                .addTransition("scan", COMPLETED_EXIT, "nextTarget")
                .addTransition("nextTarget", TARGET_SELECTED_EXIT, "goto")
                .addTransition("goto", COMPLETED_EXIT, "nextTarget")
                .build("goto");

    }

    static InferenceEngine stop(JsonNode config, Locator locator) {
        return StateMachineBuilder.create()
                .addState(StopStatus.finalStatus())
                .build("stop");
    }
}
