/*
 *
 * Copyright (c) )2022 Marco Marini, marco.marini@mmarini.org
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
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.swing.MotorCommand;
import org.mmarini.wheelly.swing.RxJoystick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class FlowBuilder {
    static final long REMOTE_CLOCK_PERIOD = 60;   // Seconds
    private static final int REMOTE_CLOCK_SAMPLES = 10;
    private static final long STATUS_INTERVAL = 200; // Millis
    private static final long MOTOR_INTERVAL = 2000;
    private static final long MOTOR_VALIDITY = 2500;
    private static final Logger logger = LoggerFactory.getLogger(FlowBuilder.class);
    private static final int MAX_MOTOR_SPEED = 4;

    /**
     * @param controller the controller
     * @param joystick   the joystick
     */
    public static FlowBuilder create(RxController controller, RxJoystick joystick) {
        return new FlowBuilder(controller, joystick);
    }

    /**
     * @param speed     the speed
     * @param direction the direction
     */
    private static int getLeftSpeed(int speed, Direction direction) {
        switch (direction) {
            case N:
            case NE:
            case E:
                return speed;
            case W:
            case S:
            case SW:
                return -speed;
        }
        return 0;
    }

    private static int getRightSpeed(int speed, Direction direction) {
        switch (direction) {
            case N:
            case NW:
            case W:
                return speed;
            case E:
            case S:
            case SE:
                return -speed;
        }
        return 0;
    }

    private static boolean isMoving(List<Tuple2<Integer, Integer>> tuples) {
        Tuple2<Integer, Integer> m0 = tuples.get(0);
        Tuple2<Integer, Integer> m1 = tuples.get(1);
        return !(m0._1 == 0 && m0._2 == 0 && m1._1 == 0 && m1._2 == 0);
    }

    private static boolean isSpeedChanged(List<Tuple2<Integer, Integer>> tuples) {
        Tuple2<Integer, Integer> m0 = tuples.get(0);
        Tuple2<Integer, Integer> m1 = tuples.get(1);
        return !m0.equals(m1);
    }

    /**
     * @param x the joystick x-axis
     * @param y the joystick y-axis
     */
    static Direction toDir(float x, float y) {
        double dir = atan2(y, x) + Math.PI;
        int i = ((int) round(dir * 8 / Math.PI)) % 16;
        int d = ((i + 13) / 2) % 8;
        return Direction.values()[d + 1];
    }

    /**
     * @param xy the joystick axis
     */
    private static Tuple2<Integer, Integer> toMotorSpeed(Tuple2<Float, Float> xy) {
        float x = xy._1;
        float y = xy._2;
        int speed = min(round((float) sqrt(x * x + y * y) * MAX_MOTOR_SPEED), MAX_MOTOR_SPEED);
        Direction direction = Direction.NONE;
        if (speed >= 1) {
            direction = toDir(x, y);
        }
        int left = getLeftSpeed(speed, direction);
        int right = getRightSpeed(speed, direction);
        return Tuple2.of(left, right);
    }

    private final RxController controller;
    private final RxJoystick joystick;
    private final PublishProcessor<Throwable> errors;
    private final PublishProcessor<WheellyStatus> status;
    private final BehaviorProcessor<Boolean> connection;
    private final BehaviorProcessor<RemoteClock> remoteClock;
    private final Scheduler scheduler;

    /**
     * @param controller the controller
     * @param joystick   the joystick
     */
    protected FlowBuilder(RxController controller, RxJoystick joystick) {
        this.controller = requireNonNull(controller);
        this.joystick = requireNonNull(joystick);
        this.connection = BehaviorProcessor.createDefault(false);
        this.errors = PublishProcessor.create();
        this.status = PublishProcessor.create();
        this.remoteClock = BehaviorProcessor.create();
        ExecutorService exec = Executors.newSingleThreadExecutor();
        this.scheduler = Schedulers.from(exec);
    }

    /**
     *
     */
    public FlowBuilder build() {
        toRemoteClock(() ->
                toRemoteClock(REMOTE_CLOCK_SAMPLES,
                        () -> toClockSync(controller.clock())))
                .subscribe(remoteClock);

        // Status polling
        Flowable<Tuple2<StatusBody, RemoteClock>> statusFlow = createStatusPoller();

        // Scan command
        Flowable<Tuple2<StatusBody, RemoteClock>> scanCommand = createScannerCommand();

        // Motor command
        Flowable<Tuple2<StatusBody, RemoteClock>> statusByMotors = createMotorCommand()
                .concatMap(this::sendMotorCommand);
        status.window(STATUS_INTERVAL * 2, TimeUnit.MILLISECONDS)
                .concatMap(f -> f.isEmpty().toFlowable())
                .map(x -> !x)
                .subscribe(connection);
        Flowable.merge(statusFlow, scanCommand, statusByMotors).map(RxController::toStatus).subscribe(status);
        return this;
    }

    /**
     *
     */
    private Flowable<Tuple2<Integer, Integer>> createJoystickDirCommand() {
        logger.debug("Creating joystick command ...");
        return joystick.getXY()
                .map(FlowBuilder::toMotorSpeed)
                .buffer(2, 1)
                .filter(FlowBuilder::isSpeedChanged)
                .map(x -> x.get(1));
    }

    /**
     *
     */
    private Flowable<MotorCommand> createMotorCommand() {
        return combineLatest(
                interval(0, MOTOR_INTERVAL, TimeUnit.MILLISECONDS),
                createJoystickDirCommand(),
                (i, cmd) -> cmd)
                .onBackpressureDrop()
                .buffer(2, 1)
                .filter(FlowBuilder::isMoving)
                .map(x -> x.get(1))
                .map(t -> MotorCommand.empty().setLeft(t._1).setRight(t._2))
                .withLatestFrom(remoteClock, MotorCommand::setClock);
    }

    /**
     *
     */
    private Flowable<Tuple2<StatusBody, RemoteClock>> createScannerCommand() {
        return Flowable.mergeArray(
                        joystick.getValues(RxJoystick.BUTTON_0),
                        joystick.getValues(RxJoystick.BUTTON_1),
                        joystick.getValues(RxJoystick.BUTTON_2),
                        joystick.getValues(RxJoystick.BUTTON_3))
                .filter(x -> x > 0f)
                .withLatestFrom(remoteClock, Tuple2::of)
                .doOnNext(x -> logger.debug("Posting scan command ..."))
                .concatMap(t -> controller.scan()
                        .doOnError(errors::onNext)
                        .map(t::setV1))
                .onErrorResumeWith(empty());
    }

    /**
     *
     */
    Flowable<Tuple2<StatusBody, RemoteClock>> createStatusPoller() {
        return interval(0, STATUS_INTERVAL, TimeUnit.MILLISECONDS)
                .onBackpressureDrop()
                .withLatestFrom(remoteClock, Tuple2::of)
                .concatMap(t -> controller.status()
                        .doOnError(errors::onNext)
                        .map(t::setV1)
                        .onErrorResumeWith(empty())
                );
    }

    /**
     *
     */
    public FlowBuilder detach() {
        return this;
    }

    /**
     * Returns the wheelly connection status
     */
    public Flowable<Boolean> getConnection() {
        return connection;
    }

    /**
     *
     */
    public Flowable<Throwable> getErrors() {
        return errors;
    }

    /**
     * Returns the status events
     */
    public Flowable<WheellyStatus> getStatus() {
        return status;
    }

    /**
     * @param cmd the motor command
     */
    private Flowable<Tuple2<StatusBody, RemoteClock>> sendMotorCommand(MotorCommand cmd) {
        return controller.moveTo(
                        cmd.left,
                        cmd.right,
                        cmd.clock.toRemote(Instant.now().plusMillis(MOTOR_VALIDITY)))
                .doOnError(errors::onNext)
                .onErrorResumeWith(empty())
                .map(body -> Tuple2.of(body, cmd.clock));
    }

    /**
     * Returns the clock synchronization event
     * In case of error it signals the connection flowable
     *
     * @param flow the clock body flow
     */
    Flowable<ClockSyncEvent> toClockSync(Flowable<ClockBody> flow) {
        return flow
                .doOnError(errors::onNext)
                .map(body -> {
                    long destinationTimestamp = Instant.now().toEpochMilli();
                    String data = body.getClock();
                    return ClockSyncEvent.from(data, destinationTimestamp);
                });
    }

    /**
     * Returns the remote clock by synchronizing the clocks every REMOTE_CLOCK_PERIOD
     */
    Flowable<RemoteClock> toRemoteClock(Supplier<Flowable<RemoteClock>> builder) {
        logger.debug("Creating remote clock ...");
        return interval(0, REMOTE_CLOCK_PERIOD, TimeUnit.SECONDS)
                .onBackpressureDrop()
                .concatMap(x -> builder.get());
    }

    /**
     * Returns the remote clock by averaging the synchronization of local clock with remote clock
     *
     * @param noSamples number of samples
     */
    Flowable<RemoteClock> toRemoteClock(int noSamples, Supplier<Flowable<ClockSyncEvent>> builder) {
        return Flowable.range(1, noSamples)
                .concatMap(x -> builder.get())
                .map(ClockSyncEvent::getRemoteOffset)
                .reduce(Long::sum)
                .map(t -> RemoteClock.create((t + noSamples / 2) / noSamples))
                .toFlowable()
                .onErrorResumeWith(Flowable.empty());
    }

    /**
     *
     */
    public enum Direction {
        NONE,
        N,
        NE,
        E,
        SE,
        S,
        SW,
        W,
        NW
    }
}
