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

import java.util.Random;

/**
 * Status where the robot scan and check for obstacles
 */
public class RandomScanStatus extends AbstractEngineStatus {
    public static final String INTERVAL_KEY = "interval";
    public static final String SCAN_TIME_KEY = "scanTime";
    public static final long DEFAULT_INTERVAL = 5000;
    public static final long DEFAULT_SCAN_TIME = 1000;
    private static final Logger logger = LoggerFactory.getLogger(NextSequenceStatus.class);

    /**
     *
     */
    public static RandomScanStatus create(String name) {
        return new RandomScanStatus(name, new Random());
    }

    public static RandomScanStatus create(String name, Random random) {
        return new RandomScanStatus(name, random);
    }

    private final Random random;
    private long interval;
    private long timer;
    private int direction;
    private long scanTime;

    /**
     * Creates named engine status
     *
     * @param name   the name
     * @param random the random generator
     */
    protected RandomScanStatus(String name, Random random) {
        super(name);
        this.random = random;
    }


    @Override
    public EngineStatus activate(StateMachineContext context, InferenceMonitor monitor) {
        super.activate(context, monitor);
        this.interval = getLong(context, INTERVAL_KEY, DEFAULT_INTERVAL);
        this.scanTime = getLong(context, SCAN_TIME_KEY, DEFAULT_SCAN_TIME);
        this.timer = System.currentTimeMillis() + interval;
        this.direction = 0;
        return this;
    }


    @Override
    public StateTransition process(Timed<MapStatus> data, StateMachineContext context, InferenceMonitor monitor) {
        return this.safetyCheck(data, context, monitor).orElseGet(() -> {
            if (System.currentTimeMillis() >= timer) {
                direction = random.nextInt(181) - 90;
                this.timer = System.currentTimeMillis() + interval;
            }

            long elapsedTime = getElapsedTime();
            long scanFrameTime = elapsedTime % interval;
            int dir = scanFrameTime > scanTime ? 0 : direction;
            return StateTransition.createHalt(STAY_EXIT, dir);
        });
    }
}
