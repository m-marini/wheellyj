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

package org.mmarini.wheelly.model;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.lang.Math.round;
import static org.mmarini.wheelly.model.AbstractScannerMap.THRESHOLD_DISTANCE;

public class BehaviorEngine {
    private static final Logger logger = LoggerFactory.getLogger(BehaviorEngine.class);
    private static final double MOTOR_SCALE = 0.1;

    /**
     * Returns the behavior engine
     *
     * @param controller           the roboto controller
     * @param engine               the inference engine
     * @param motorCommandInterval the interval between motor commands
     * @param scanCommandInterval  the interval between scan commands
     */
    public static BehaviorEngine create(RobotController controller, InferenceEngine engine, long motorCommandInterval, long scanCommandInterval) {
        return new BehaviorEngine(controller, engine, motorCommandInterval, scanCommandInterval);
    }

    /**
     * @param configParams
     * @param engine       the inference engine
     */
    public static BehaviorEngine create(ConfigParameters configParams, InferenceEngine engine) {
        RobotController controller = RawController.create(configParams);
        return create(controller, engine, configParams.motorCommandInterval, configParams.scanCommandInterval);
    }

    private final RobotController controller;
    private final BehaviorProcessor<ScannerMap> mapFlow;

    /**
     * Creates the behavior engine
     *
     * @param controller           the roboto controller
     * @param engine               the inference engine
     * @param motorCommandInterval the interval between motor commands
     * @param scanCommandInterval  the interval between scan commands
     */
    protected BehaviorEngine(RobotController controller, InferenceEngine engine, long motorCommandInterval, long scanCommandInterval) {
        logger.debug("Created");
        this.controller = controller;
        this.mapFlow = BehaviorProcessor.create();


        // Creates status flow
        Flowable<Timed<WheellyStatus>> statusFlow = controller.readStatus();

        // Creates map flow
        controller.readStatus()
                .observeOn(Schedulers.computation())
                .map(x -> new Timed<>(x.value().sample, x.time(), x.unit()))
                .scanWith(() -> GridScannerMap.create(List.of(), THRESHOLD_DISTANCE),
                        ScannerMap::process)
                .subscribe(mapFlow);

        // Builds command flow by applying inference engine
        Flowable<Tuple2<MotionComand, Integer>> commands = combineLatest(statusFlow, mapFlow, Tuple2::of)
                .observeOn(Schedulers.computation())
                .map(engine::process)
                .publish()
                .autoConnect();

        // Splits the commands  flow in motor and scanner command flows
        // The motor command are sample every motorCommandInterval msec
        Flowable<MotionComand> motorCommandFlow = commands.map(Tuple2::getV1)
                .map(cmd -> {
                    if (cmd instanceof MoveCommand) {
                        MoveCommand moveCommand = (MoveCommand) cmd;
                        double speed = round(moveCommand.speed / MOTOR_SCALE) * MOTOR_SCALE;
                        return MoveCommand.create(moveCommand.direction, speed);
                    } else {
                        return cmd;
                    }
                })
                .sample(motorCommandInterval, TimeUnit.MILLISECONDS);

        // the distinct scan command
        Flowable<Integer> directionFlow = commands.map(Tuple2::getV2)
                .distinctUntilChanged();

        // the scan command every 100 msec filtered out for 0 deg commands
        Flowable<ScanCommand> scanCommandFlow = combineLatest(interval(scanCommandInterval, TimeUnit.MILLISECONDS),
                directionFlow,
                (t, a) -> a)
                .buffer(2, 1)
                .concatMap(list -> list.get(1) != 0 ? just(list.get(1))
                        : list.get(0) != 0 ? just(0) : empty())
                .map(ScanCommand::create);

        // Start the controller
        controller.action(scanCommandFlow);
        controller.action(motorCommandFlow);
    }

    public RobotController action(Flowable<? extends WheellyCommand> commands) {
        return controller.action(commands);
    }

    public RobotController close() {
        return controller.close();
    }

    public Flowable<Boolean> readConnection() {
        return controller.readConnection();
    }

    public Flowable<Timed<Integer>> readCps() {
        return controller.readCps();
    }

    public Flowable<Throwable> readErrors() {
        return controller.readErrors();
    }

    /**
     * Returns the map flow
     */
    public Flowable<ScannerMap> readMapFlow() {
        return mapFlow;
    }

    public Flowable<Timed<WheellyStatus>> readStatus() {
        return controller.readStatus();
    }

    /**
     * Starts the engine
     */
    public BehaviorEngine start() {
        controller.start();
        return this;
    }
}
