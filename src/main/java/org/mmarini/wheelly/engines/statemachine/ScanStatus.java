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
import org.mmarini.wheelly.model.AltCommand;
import org.mmarini.wheelly.model.InferenceMonitor;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ScanStatus implements EngineStatus {
    public static final String INDEX_KEY = "ScanStatus.index";
    public static final String INTERVAL_KEY = "ScanStatus.interval";
    public static final String TIMER_KEY = "ScanStatus.timer";
    public static final int DEFAULT_INTERVAL = 200;
    private static final double RANGE = 15;
    private static final int[] DIRECTIONS = {
            -30, -60, -90, -75, -45, -15, 15, 45, 75, 90, 60, 30
    };
    private static final Logger logger = LoggerFactory.getLogger(NextSequenceStatus.class);
    private static final ScanStatus SINGLETON = new ScanStatus();

    /**
     *
     */
    public static ScanStatus create() {
        return SINGLETON;
    }


    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        return scan(context, 0);
    }


    @Override
    public StateTransition process(Tuple2<Timed<WheellyStatus>, ? extends ScannerMap> data, StateMachineContext context, InferenceMonitor monitor) {
        logger.debug("{}", context);
        int index = context.<Number>get(INDEX_KEY).orElse(0).intValue();
        Optional<Number> timer = context.get(TIMER_KEY);
        if (timer.filter(t -> System.currentTimeMillis() >= t.longValue()).isPresent()) {
            if (index >= DIRECTIONS.length - 1) {
                // Exit
                logger.debug("Scan completed");
                context.remove(INDEX_KEY);
                context.remove(TIMER_KEY);
                return StateTransition.create(COMPLETED_EXIT, context, ALT_COMMAND);
            } else {
                scan(context, index + 1);
            }
        }
        return StateTransition.create(STAY_EXIT, context, Tuple2.of(AltCommand.create(), DIRECTIONS[index]));
    }

    private ScanStatus scan(StateMachineContext context, int i) {
        long interval = context.<Number>get(INTERVAL_KEY).orElse(DEFAULT_INTERVAL).longValue();
        long timer = System.currentTimeMillis() + interval;
        context.put(INDEX_KEY, i)
                .put(TIMER_KEY, timer);
        return this;
    }
}
