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

package org.mmarini.wheelly.engines.statemachine;

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;

public class NextSequenceStatus implements EngineStatus {
    public static final String TARGET_SELECTED_EXIT = "TargetSelected";
    public static final String MISSING_TARGET_EXIT = "MissingTarget";
    public static final String TARGET_KEY = "NextSequenceStatus.target";
    public static final String INDEX_KEY = "NextSequenceStatus.index";
    public static final String LIST_KEY = "NextSequenceStatus.list";
    private static final Logger logger = LoggerFactory.getLogger(NextSequenceStatus.class);
    private static final NextSequenceStatus SINGLETON = new NextSequenceStatus();

    /**
     *
     */
    public static EngineStatus create() {
        return SINGLETON;
    }

    protected NextSequenceStatus() {
    }

    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        List<Point2D> list = context.<List<Point2D>>get(LIST_KEY).orElseGet(List::of);
        if (list.isEmpty()) {
            logger.warn("Target list empty");
            context.remove(TARGET_KEY);
            context.remove(INDEX_KEY);
            return StateTransition.create(MISSING_TARGET_EXIT, context, HALT_COMMAND);
        }
        int nextIndex = context.getInt(INDEX_KEY, -1) + 1;
        if (nextIndex >= list.size()) {
            context.remove(INDEX_KEY);
            return StateTransition.create(COMPLETED_EXIT, context, HALT_COMMAND);
        }
        Point2D target = list.get(nextIndex);
        context.put(INDEX_KEY, nextIndex);
        context.put(TARGET_KEY, target);
        logger.debug("Selected target {} {}", nextIndex, target);
        return StateTransition.create(TARGET_SELECTED_EXIT, context, HALT_COMMAND);
    }
}
