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

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.Collectors;

import static org.mmarini.Utils.stream;
import static org.mmarini.wheelly.engines.statemachine.ContextOperator.assign;
import static org.mmarini.wheelly.engines.statemachine.ContextOperator.remove;

public interface Builders {

    static InferenceEngine findPath(JsonNode config) {
        JsonNode targetsNode = config.path("targets");
        if (targetsNode.isMissingNode()) {
            throw new IllegalArgumentException("Missing targets");
        }
        if (!targetsNode.isArray()) {
            throw new IllegalArgumentException("targets must be an array");
        }
        List<Point2D> targets = stream(targetsNode.elements())
                .map(node -> {
                    JsonNode xNode = node.path("x");
                    JsonNode yNode = node.path("y");
                    if (xNode.isMissingNode()) {
                        throw new IllegalArgumentException("Missing x");
                    }
                    if (yNode.isMissingNode()) {
                        throw new IllegalArgumentException("Missing y");
                    }
                    return new Point2D.Double(xNode.asDouble(), yNode.asDouble());
                }).collect(Collectors.toList());
        return StateMachineBuilder.create()
                .addState("initial", StopStatus.create())
                .addState("scan", ScanStatus.create())
                .addState("findPath", FindPathStatus.create())
                .addState("next", NextSequenceStatus.create())
                .addState("goto", GotoStatus.create())
                .addState("avoid", AvoidObstacleStatus.create())
                .addTransition("initial", StopStatus.TIMEOUT_EXIT, "scan")
                .addTransition("scan", ScanStatus.COMPLETED_EXIT, "findPath")
                .addTransition("findPath", FindPathStatus.PATH_EXIT, "next",
                        ContextOperator.sequence(
                                assign(NextSequenceStatus.LIST_KEY, FindPathStatus.PATH_KEY),
                                remove(NextSequenceStatus.INDEX_KEY)))
                .addTransition("next", NextSequenceStatus.TARGET_SELECTED_EXIT, "goto",
                        assign(GotoStatus.TARGET_KEY, NextSequenceStatus.TARGET_KEY))
                .addTransition("goto", GotoStatus.TARGET_REACHED_EXIT, "next")
                .addTransition("goto", GotoStatus.OBSTACLE_EXIT, "avoid")
                .addTransition("avoid", AvoidObstacleStatus.COMPLETED_EXIT, "scan")
                .setParams(FindPathStatus.TARGET_KEY, targets.get(0))
                .setParams(StopStatus.TIMEOUT_KEY, 4000)
                .build("initial");
    }

    static InferenceEngine gotoTest(JsonNode config) {
        JsonNode targetsNode = config.path("targets");
        if (targetsNode.isMissingNode()) {
            throw new IllegalArgumentException("Missing targets");
        }
        if (!targetsNode.isArray()) {
            throw new IllegalArgumentException("targets must be an array");
        }
        List<Point2D> targets = stream(targetsNode.elements())
                .map(node -> {
                    JsonNode xNode = node.path("x");
                    JsonNode yNode = node.path("y");
                    if (xNode.isMissingNode()) {
                        throw new IllegalArgumentException("Missing x");
                    }
                    if (yNode.isMissingNode()) {
                        throw new IllegalArgumentException("Missing y");
                    }
                    return new Point2D.Double(xNode.asDouble(), yNode.asDouble());
                }).collect(Collectors.toList());
        return StateMachineBuilder.create()
                .addState("initial", StopStatus.create())
                .addState("goto", GotoStatus.create())
                .addTransition("initial", StopStatus.TIMEOUT_EXIT, "goto")
                .setParams(GotoStatus.TARGET_KEY, targets.get(0))
                .setParams(StopStatus.TIMEOUT_KEY, 4000)
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

    static InferenceEngine sequence(JsonNode config) {
        JsonNode targetsNode = config.path("targets");
        if (targetsNode.isMissingNode()) {
            throw new IllegalArgumentException("Missing targets");
        }
        if (!targetsNode.isArray()) {
            throw new IllegalArgumentException("targets must be an array");
        }
        List<Point2D> targets = stream(targetsNode.elements())
                .map(node -> {
                    JsonNode xNode = node.path("x");
                    JsonNode yNode = node.path("y");
                    if (xNode.isMissingNode()) {
                        throw new IllegalArgumentException("Missing x");
                    }
                    if (yNode.isMissingNode()) {
                        throw new IllegalArgumentException("Missing y");
                    }
                    return new Point2D.Double(xNode.asDouble(), yNode.asDouble());
                }).collect(Collectors.toList());
        return StateMachineBuilder.create()
                .addState("nextTarget", NextSequenceStatus.create())
                .addState("goto", GotoStatus.create())
                .addState("scan", ScanStatus.create())
                .addTransition("scan", ScanStatus.COMPLETED_EXIT, "nextTarget")
                .addTransition("nextTarget", NextSequenceStatus.TARGET_SELECTED_EXIT, "goto",
                        assign(GotoStatus.TARGET_KEY, NextSequenceStatus.TARGET_KEY)
                )
                .addTransition("goto", GotoStatus.TARGET_REACHED_EXIT, "nextTarget")
                .addTransition("goto", GotoStatus.OBSTACLE_EXIT, "scan")
                .setParams(NextSequenceStatus.LIST_KEY, targets)
                .build("goto");

    }

    static InferenceEngine stop(JsonNode config) {
        return StateMachineBuilder.create()
                .addState("stop", StopStatus.finalStatus())
                .build("stop");

    }
}
