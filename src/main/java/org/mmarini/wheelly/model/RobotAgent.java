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
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.lang.Math.round;
import static java.lang.String.format;
import static org.mmarini.wheelly.model.GridScannerMap.THRESHOLD_DISTANCE;
import static org.mmarini.wheelly.model.HaltCommand.ALT_COMMAND;

public class RobotAgent implements InferenceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(RobotAgent.class);
    private static final double MOTOR_SCALE = 0.1;

    /**
     * Returns the behavior engine
     *
     * @param controller           the roboto controller
     * @param engine               the inference engine
     * @param motorCommandInterval the interval between motor commands
     * @param scanCommandInterval  the interval between scan commands
     */
    public static RobotAgent create(RobotController controller, InferenceEngine engine, long motorCommandInterval, long scanCommandInterval) {
        return new RobotAgent(controller, engine, motorCommandInterval, scanCommandInterval);
    }

    /**
     * @param configParams the configuration parameters
     * @param engine       the inference engine
     */
    public static RobotAgent create(ConfigParameters configParams, InferenceEngine engine) {
        RobotController controller = RawController.create(configParams);
        return create(controller, engine, configParams.motorCommandInterval, configParams.scanCommandInterval);
    }

    private final RobotController controller;
    private final BehaviorProcessor<Timed<MapStatus>> mapFlow;
    private final PublishProcessor<String> inferenceMessages;
    private final PublishProcessor<Tuple2<String, Optional<?>>> inferenceData;
    private final InferenceEngine engine;

    /**
     * Creates the behavior engine
     *
     * @param controller           the roboto controller
     * @param engine               the inference engine
     * @param motorCommandInterval the interval between motor commands
     * @param scanCommandInterval  the interval between scan commands
     */
    protected RobotAgent(RobotController controller, InferenceEngine engine, long motorCommandInterval, long scanCommandInterval) {
        logger.debug("Created");
        this.controller = controller;
        this.mapFlow = BehaviorProcessor.create();
        this.inferenceData = PublishProcessor.create();
        this.inferenceMessages = PublishProcessor.create();
        this.engine = engine;
        createMapFlow();
        createActionFlow(motorCommandInterval, scanCommandInterval);
    }

    public RobotController action(Flowable<? extends WheellyCommand> commands) {
        return controller.action(commands);
    }

    public RobotController close() {
        return controller.close();
    }

    private void createActionFlow(long motorCommandInterval, long scanCommandInterval) {
        Flowable<Tuple2<MotionCommand, Integer>> commands = createCommandFlow();
        // Splits the commands  flow in motor and scanner command flows
        createMotionFlow(commands.map(Tuple2::getV1), motorCommandInterval);
        createScanFlow(commands.map(Tuple2::getV2), scanCommandInterval);
    }

    private Flowable<Tuple2<MotionCommand, Integer>> createCommandFlow() {
        // Builds command flow by applying inference engine
        return mapFlow.observeOn(Schedulers.computation())
                .map(t -> engine.process(t, this))
                .publish()
                .autoConnect();
    }

    private void createMapFlow() {
        // Creates map flow
        controller.readStatus()
                .observeOn(Schedulers.computation())
                .scanWith(() -> Tuple2.of(Optional.<Timed<WheellyStatus>>empty(), GridScannerMap.create(List.of(), THRESHOLD_DISTANCE, THRESHOLD_DISTANCE, 0)),
                        (t, status) -> Tuple2.of(Optional.of(status), t._2.process(status)))
                .concatMap(t -> t._1.map(tt -> just(
                                new Timed<>(MapStatus.create(tt.value(), t._2), tt.time(), tt.unit())
                        ))
                        .orElse(empty()))
                .subscribe(mapFlow);
    }

    private void createMotionFlow(Flowable<MotionCommand> commands, long motorCommandInterval) {
        // The motor command are sample every motorCommandInterval msec
        Flowable<MotionCommand> motorCommandFlow1 = commands.map(cmd -> {
                    if (cmd instanceof MoveCommand) {
                        MoveCommand moveCommand = (MoveCommand) cmd;
                        double speed = round(moveCommand.speed / MOTOR_SCALE) * MOTOR_SCALE;
                        return MoveCommand.create(moveCommand.direction, speed);
                    } else {
                        return cmd;
                    }
                })
                .distinctUntilChanged();
        Flowable<MotionCommand> motorCommandFlow = combineLatest(interval(motorCommandInterval, TimeUnit.MILLISECONDS),
                motorCommandFlow1,
                Tuple2::of)
                .distinctUntilChanged()
                .map(Tuple2::getV2)
                .buffer(2, 1)
                .filter(cmds -> !(cmds.get(0) == ALT_COMMAND
                        && cmds.get(1) == ALT_COMMAND))
                .map(cmds -> cmds.get(1))
                .throttleLatest(100, TimeUnit.MILLISECONDS);

        controller.action(motorCommandFlow);
    }

    private void createScanFlow(Flowable<Integer> commands, long scanCommandInterval) {
        // the distinct scan command
        Flowable<ScanCommand> scanCommandFlow = Flowable.combineLatest(
                        interval(scanCommandInterval, TimeUnit.MILLISECONDS),
                        commands.distinctUntilChanged(),
                        Tuple2::of)
                .distinctUntilChanged()
                .map(Tuple2::getV2)
                .buffer(2, 1)
                .filter((list) -> !(list.get(0) == 0 && list.get(1) == 0))
                .map(list -> list.get(1))
                .throttleLatest(50, TimeUnit.MILLISECONDS)
                .map(ScanCommand::create);
        controller.action(scanCommandFlow);
    }

    @Override
    public <T> InferenceMonitor put(String key, T value) {
        inferenceData.onNext(Tuple2.of(key, Optional.of(value)));
        return this;
    }

    @Override
    public <T> InferenceMonitor put(String key, Optional<T> value) {
        inferenceData.onNext(Tuple2.of(key, value));
        return this;
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

    public Flowable<Tuple2<String, Optional<?>>> readInferenceData() {
        return inferenceData;
    }

    public Flowable<String> readInferenceMessages() {
        return inferenceMessages;
    }

    /**
     * Returns the map flow
     */
    public BehaviorProcessor<Timed<MapStatus>> readMapFlow() {
        return mapFlow;
    }

    public Flowable<Timed<WheellyStatus>> readStatus() {
        return controller.readStatus();
    }

    @Override
    public InferenceMonitor remove(String key) {
        inferenceData.onNext(Tuple2.of(key, Optional.empty()));
        return this;
    }

    @Override
    public InferenceMonitor show(String text, Object... params) {
        inferenceMessages.onNext(format(text, params));
        return this;
    }

    /**
     * Starts the agent
     */
    public RobotAgent start() {
        engine.init(this);
        controller.start();
        return this;
    }
}
