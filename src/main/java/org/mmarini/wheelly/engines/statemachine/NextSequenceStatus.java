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
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.MapStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.List;

import static org.mmarini.wheelly.engines.statemachine.StateMachineContext.TARGET_KEY;
import static org.mmarini.wheelly.engines.statemachine.StateTransition.COMPLETED_TRANSITION;

public class NextSequenceStatus extends AbstractEngineStatus {
    public static final String TARGET_SELECTED_EXIT = "TargetSelected";
    public static final StateTransition TARGET_SELECTED_TRANSITION = StateTransition.create(TARGET_SELECTED_EXIT, HALT_COMMAND);
    public static final String MISSING_LIST_EXIT = "MissingList";
    public static final StateTransition MISSING_LIST_TRANSITION = StateTransition.create(MISSING_LIST_EXIT, HALT_COMMAND);
    public static final String INDEX_KEY = "index";
    public static final String LIST_KEY = "list";
    private static final Logger logger = LoggerFactory.getLogger(NextSequenceStatus.class);

    /**
     *
     */
    public static EngineStatus create(String name) {
        return new NextSequenceStatus(name);
    }

    private List<Point2D> list;
    private int index;

    protected NextSequenceStatus(String name) {
        super(name);
    }

    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        context.remove(TARGET_KEY);
        getInt(context, INDEX_KEY).ifPresent(i -> {
            this.index = i;
            context.remove(name + "." + INDEX_KEY);
        });
        list = this.<List<Point2D>>get(context, LIST_KEY).orElseGet(List::of);
        return this;
    }

    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        if (list.isEmpty()) {
            logger.warn("Target list empty");
            return MISSING_LIST_TRANSITION;
        }
        if (index >= list.size()) {
            return COMPLETED_TRANSITION;
        }
        Point2D target = list.get(index);
        context.setTarget(target);
        logger.debug("Selected target {} {}", index, target);
        index++;
        return TARGET_SELECTED_TRANSITION;
    }
}
