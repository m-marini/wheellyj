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

import io.reactivex.Flowable;
import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.schedulers.Schedulers;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.swing.MotorCommand;
import org.mmarini.wheelly.swing.RxJoystick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.reactivex.Flowable.*;
import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

/**
 *
 */
public class FlowBuilder {
    private static final long REMOTE_CLOCK_PERIOD = 60;   // Seconds
    private static final int REMOTE_CLOCK_SAMPLES = 10;
    private static final long STATUS_INTERVAL = 200; // Millis
    private static final long MOTOR_INTERVAL = 1000;
    private static final long MOTOR_VALIDITY = 1500;
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
    public static Direction toDir(float x, float y) {
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
    private final Flowable<WheellyStatus> status;
    private final BehaviorProcessor<Boolean> connection;

    /**
     * @param controller the controller
     * @param joystick   the joystick
     */
    protected FlowBuilder(RxController controller, RxJoystick joystick) {
        this.controller = requireNonNull(controller);
        this.joystick = requireNonNull(joystick);
        this.connection = BehaviorProcessor.createDefault(false);
        BehaviorProcessor<RemoteClock> remoteClock = BehaviorProcessor.create();

        createRemoteClock().subscribe(remoteClock);

        // Status polling
        Flowable<Tuple2<StatusBody, RemoteClock>> statusFlow = interval(0, STATUS_INTERVAL, TimeUnit.MILLISECONDS)
                .withLatestFrom(remoteClock, Tuple2::of)
                .flatMap(t -> controller.status()
                        .doOnError(ex -> {
                            logger.error(ex.getMessage(), ex);
                            connection.onNext(false);
                        })
                        .doOnNext(x -> connection.onNext(true))
                        .map(t::setV1)
                        .onErrorResumeNext(empty())
                );

        // Scan command
        //noinspection unchecked
        Flowable<Tuple2<StatusBody, RemoteClock>> scanCommand = Flowable.mergeArray(
                        joystick.getValues(RxJoystick.BUTTON_0),
                        joystick.getValues(RxJoystick.BUTTON_1),
                        joystick.getValues(RxJoystick.BUTTON_2),
                        joystick.getValues(RxJoystick.BUTTON_3))
                .filter(x -> x > 0f)
                .withLatestFrom(remoteClock, Tuple2::of)
                .flatMap(t -> controller.scan()
                        .doOnError(ex -> {
                            logger.error(ex.getMessage(), ex);
                            connection.onNext(false);
                        })
                        .doOnNext(x -> connection.onNext(true))
                        .map(t::setV1))
                .onErrorResumeNext(empty());

        // Motor command
        Flowable<Tuple2<Integer, Integer>> joystickCommand = joystick.getXY()
                .map(FlowBuilder::toMotorSpeed)
                .buffer(2, 1)
                .filter(FlowBuilder::isSpeedChanged)
                .map(x -> x.get(1));
        Flowable<MotorCommand> motorCommand = combineLatest(
                interval(0, MOTOR_INTERVAL, TimeUnit.MILLISECONDS),
                joystickCommand,
                (i, cmd) -> cmd)
                .buffer(2, 1)
                .filter(FlowBuilder::isMoving)
                .map(x -> x.get(1))
                .map(t -> MotorCommand.empty().setRight(t._1).setLeft(t._2))
                .withLatestFrom(remoteClock, MotorCommand::setClock);

        Flowable<Tuple2<StatusBody, RemoteClock>> statusByMotors = motorCommand
                .flatMap(this::sendMotorCommand);
        this.status = Flowable.merge(statusFlow, scanCommand, statusByMotors).map(RxController::toStatus);
    }

    /**
     * Returns the clock synchronization event
     * In case of error it signals the connection flowable
     */
    Flowable<ClockSyncEvent> clockSync() {
        return controller.clock()
                .doOnError(ex -> {
                    logger.error(ex.getMessage(), ex);
                    connection.onNext(false);
                })
                .doOnNext(x -> connection.onNext(true))
                .map(body -> {
                    long destinationTimestamp = Instant.now().toEpochMilli();
                    String data = body.getClock();
                    return ClockSyncEvent.from(data, destinationTimestamp);
                });
    }

    /**
     * Returns the remote clock by synchronizing the clocks every REMOTE_CLOCK_PERIOD
     */
    Flowable<RemoteClock> createRemoteClock() {
        return interval(0, REMOTE_CLOCK_PERIOD, TimeUnit.SECONDS)
                .flatMap(x -> createRemoteClock(REMOTE_CLOCK_SAMPLES));
    }

    /**
     * Returns the remote clock by averaging the synchronization of local clock with remote clock
     *
     * @param noSamples number of samples
     */
    Flowable<RemoteClock> createRemoteClock(int noSamples) {
        return Flowable.range(1, noSamples)
                .observeOn(Schedulers.io())
                .flatMap(i -> clockSync())
                .map(ClockSyncEvent::getRemoteOffset)
                .reduce(Long::sum)
                .map(t -> RemoteClock.create((t + noSamples / 2) / noSamples))
                .toFlowable()
                .onErrorResumeNext(empty());
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
                .doOnError(ex -> {
                    logger.error(ex.getMessage(), ex);
                    connection.onNext(false);
                })
                .onErrorResumeNext(empty())
                .map(body -> Tuple2.of(body, cmd.clock));
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
