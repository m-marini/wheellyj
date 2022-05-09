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
import org.mmarini.wheelly.swing.Yaml;
import org.mmarini.yaml.schema.Locator;

import java.awt.geom.Point2D;
import java.util.List;

import static org.mmarini.wheelly.engines.statemachine.ContextOperator.assign;
import static org.mmarini.wheelly.engines.statemachine.ContextOperator.remove;
import static org.mmarini.wheelly.engines.statemachine.EngineStatus.*;
import static org.mmarini.wheelly.engines.statemachine.FindPathStatus.NO_PATH_EXIT;
import static org.mmarini.wheelly.engines.statemachine.FindPathStatus.TARGET_REACHED_EXIT;
import static org.mmarini.wheelly.engines.statemachine.GotoStatus.UNREACHABLE_EXIT;
import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TIMEOUT_KEY;
import static org.mmarini.wheelly.swing.Yaml.point;
import static org.mmarini.wheelly.swing.Yaml.points;

public interface Builders {

    static InferenceEngine avoidObstacle(JsonNode config) {
        return StateMachineBuilder.create()
                .addState("scan", ScanStatus.create())
                .addState("avoid", AvoidObstacleStatus.create())
                .addState("safest", NearestSafeStatus.create())
                .addState("goto", GotoStatus.create())
                .addTransition("scan", OBSTACLE_EXIT, "avoid")
                .addTransition("avoid", COMPLETED_EXIT, "safest")
                .addTransition("safest", COMPLETED_EXIT, "goto",
                        ContextOperator.assign(GotoStatus.TARGET_KEY, NearestSafeStatus.TARGET_KEY))
                .addTransition("goto", OBSTACLE_EXIT, "avoid")
                .addTransition("goto", UNREACHABLE_EXIT, "safest")
                .build("scan");
    }

    static InferenceEngine findPath(JsonNode config) {
        point().apply(Locator.root().path("target")).accept(config);
        Point2D target = point(config.path("target")).orElseThrow();
        return StateMachineBuilder.create()
                .addState("initial", StopStatus.create())
                .addState("scan", ScanStatus.create())
                .addState("findPath", FindPathStatus.create())
                .addState("next", NextSequenceStatus.create())
                .addState("goto", GotoStatus.create())
                .addState("avoid", AvoidObstacleStatus.create())
                .addState("safe", NearestSafeStatus.create())
                .addState("gotoSafe", GotoStatus.create())
                .addTransition("initial", TIMEOUT_EXIT, "scan",
                        ContextOperator.assignValue(TIMEOUT_KEY, 10000))
                .addTransition("scan", COMPLETED_EXIT, "findPath")
                .addTransition("scan", OBSTACLE_EXIT, "avoid")
                .addTransition("findPath", COMPLETED_EXIT, "next",
                        ContextOperator.sequence(
                                assign(NextSequenceStatus.LIST_KEY, FindPathStatus.PATH_KEY),
                                remove(NextSequenceStatus.INDEX_KEY)))
                .addTransition("next", NextSequenceStatus.TARGET_SELECTED_EXIT, "goto",
                        assign(GotoStatus.TARGET_KEY, NextSequenceStatus.TARGET_KEY))
                .addTransition("goto", COMPLETED_EXIT, "next")
                .addTransition("goto", OBSTACLE_EXIT, "avoid")
                .addTransition("goto", UNREACHABLE_EXIT, "findPath")
                .addTransition("avoid", COMPLETED_EXIT, "safe")
                .addTransition("safe", COMPLETED_EXIT, "gotoSafe",
                        assign(GotoStatus.TARGET_KEY, NearestSafeStatus.TARGET_KEY))
                .addTransition("gotoSafe", COMPLETED_EXIT, "scan")
                .addTransition("gotoSafe", OBSTACLE_EXIT, "avoid")
                .addTransition("gotoSafe", UNREACHABLE_EXIT, "safe")
                .setParams(FindPathStatus.TARGET_KEY, target)
                .setParams(TIMEOUT_KEY, 2000)
                .build("initial");
    }

    static InferenceEngine gotoTest(JsonNode config) {
        point().apply(Locator.root().path("target")).accept(config);
        Point2D target = point(config.path("target")).orElseThrow();
        return StateMachineBuilder.create()
                .addState("initial", StopStatus.create())
                .addState("goto", GotoStatus.create())
                .addTransition("initial", TIMEOUT_EXIT, "goto")
                .setParams(GotoStatus.TARGET_KEY, target)
                .setParams(TIMEOUT_KEY, 4000)
                .build("initial");
    }

    static InferenceEngine manual(JsonNode config) {
        String port = config.path("joystickPort").asText();
        if (port.isEmpty()) {
            throw new IllegalArgumentException("Missing joystickPort");
        }
        RxJoystick joystick = RxJoystickImpl.create(port);
        return new ManualEngine(joystick);
    }

    static InferenceEngine randomPath(JsonNode config) {
        Yaml.randomPath().apply(Locator.root()).accept(config);

        StateMachineBuilder builder1 = StateMachineBuilder.create()
                .addState("initial", StopStatus.create())
                .addState("scanForTarget", ScanStatus.create())
                .addState("avoid1", AvoidObstacleStatus.create())
                .addState("safe1", NearestSafeStatus.create())
                .addState("gotoSafe1", GotoStatus.create())
                .addState("random", RandomTargetStatus.create())
                .addState("findPath", FindPathStatus.create())
                .addState("next", NextSequenceStatus.create())
                .addState("goto", GotoStatus.create())
                .addState("avoid2", AvoidObstacleStatus.create())
                .addState("safe2", NearestSafeStatus.create())
                .addState("gotoSafe2", GotoStatus.create())
                .addState("scanForPath", ScanStatus.create())

                .addTransition("initial", TIMEOUT_EXIT, "scanForTarget",
                        ContextOperator.assignValue(TIMEOUT_KEY, 10000))

                .addTransition("scanForTarget", COMPLETED_EXIT, "random")
                .addTransition("scanForTarget", OBSTACLE_EXIT, "avoid1")

                .addTransition("avoid1", COMPLETED_EXIT, "safe1")
                .addTransition("safe1", COMPLETED_EXIT, "gotoSafe1",
                        assign(GotoStatus.TARGET_KEY, NearestSafeStatus.TARGET_KEY))
                .addTransition("gotoSafe1", COMPLETED_EXIT, "scanForPath")
                .addTransition("gotoSafe1", OBSTACLE_EXIT, "avoid1")
                .addTransition("gotoSafe1", UNREACHABLE_EXIT, "safe1")

                .addTransition("random", COMPLETED_EXIT, "findPath",
                        assign(FindPathStatus.TARGET_KEY, RandomTargetStatus.TARGET_KEY))

                .addTransition("findPath", COMPLETED_EXIT, "next",
                        ContextOperator.sequence(
                                assign(NextSequenceStatus.LIST_KEY, FindPathStatus.PATH_KEY),
                                remove(NextSequenceStatus.INDEX_KEY)))
                .addTransition("findPath", NO_PATH_EXIT, "random")
                .addTransition("findPath", TARGET_REACHED_EXIT, "random")

                .addTransition("next", NextSequenceStatus.TARGET_SELECTED_EXIT, "goto",
                        assign(GotoStatus.TARGET_KEY, NextSequenceStatus.TARGET_KEY))
                .addTransition("next", COMPLETED_EXIT, "scanForTarget")

                .addTransition("goto", COMPLETED_EXIT, "next")
                .addTransition("goto", OBSTACLE_EXIT, "avoid2")
                .addTransition("goto", UNREACHABLE_EXIT, "findPath")

                .addTransition("avoid2", COMPLETED_EXIT, "safe2")

                .addTransition("safe2", COMPLETED_EXIT, "gotoSafe2",
                        assign(GotoStatus.TARGET_KEY, NearestSafeStatus.TARGET_KEY))
                .addTransition("gotoSafe2", COMPLETED_EXIT, "scanForPath")
                .addTransition("gotoSafe2", OBSTACLE_EXIT, "avoid2")
                .addTransition("gotoSafe2", UNREACHABLE_EXIT, "safe2")

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

    static InferenceEngine sequence(JsonNode config) {
        points().apply(Locator.root().path("targets")).accept(config);
        List<Point2D> targets = points(config.path("targets"));
        return StateMachineBuilder.create()
                .addState("nextTarget", NextSequenceStatus.create())
                .addState("goto", GotoStatus.create())
                .addState("scan", ScanStatus.create())
                .addTransition("scan", COMPLETED_EXIT, "nextTarget")
                .addTransition("nextTarget", NextSequenceStatus.TARGET_SELECTED_EXIT, "goto",
                        assign(GotoStatus.TARGET_KEY, NextSequenceStatus.TARGET_KEY))
                .addTransition("goto", COMPLETED_EXIT, "nextTarget")
                .addTransition("goto", OBSTACLE_EXIT, "scan")
                .setParams(NextSequenceStatus.LIST_KEY, targets)
                .build("goto");

    }

    static InferenceEngine stop(JsonNode config) {
        return StateMachineBuilder.create()
                .addState("stop", StopStatus.finalStatus())
                .build("stop");

    }
}
