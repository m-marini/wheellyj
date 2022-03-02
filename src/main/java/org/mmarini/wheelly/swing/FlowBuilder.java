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

package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.MotorCommand;
import org.mmarini.wheelly.model.RobotController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.combineLatest;
import static io.reactivex.rxjava3.core.Flowable.interval;
import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

/**
 * The builder of process flowables
 */
public class FlowBuilder {
    private static final long MOTOR_INTERVAL = 1000;
    private static final Logger logger = LoggerFactory.getLogger(FlowBuilder.class);
    private static final int MAX_MOTOR_SPEED = 255;
    private static final int NUM_MOTOR_SPEED = 11;
    private static final long MIN_MOTOR_INTERVAL = 200;

    /**
     * Returns a flow builder
     *
     * @param controller the controller
     * @param joystick   the joystick
     */
    public static FlowBuilder create(RobotController controller, RxJoystick joystick) {
        return new FlowBuilder(controller, joystick);
    }

    /**
     * Returns true if Wheelly is moving
     *
     * @param tuples the tuple list with speeds
     */
    private static boolean isMoving(List<Tuple2<Integer, Integer>> tuples) {
        Tuple2<Integer, Integer> m0 = tuples.get(0);
        Tuple2<Integer, Integer> m1 = tuples.get(1);
        return !(m0._1 == 0 && m0._2 == 0 && m1._1 == 0 && m1._2 == 0);
    }

    /**
     * Returns true if the speed is changing
     *
     * @param tuples the tuple list with speeds
     */
    private static boolean isSpeedChanged(List<Tuple2<Integer, Integer>> tuples) {
        Tuple2<Integer, Integer> m0 = tuples.get(0);
        Tuple2<Integer, Integer> m1 = tuples.get(1);
        return !m0.equals(m1);
    }

    static Tuple2<Integer, Integer> speedFromAxis(Tuple2<Float, Float> xy) {
        int x = round(xy._1 * (NUM_MOTOR_SPEED - 1)) * MAX_MOTOR_SPEED / (NUM_MOTOR_SPEED - 1);
        int y = round(xy._2 * (NUM_MOTOR_SPEED - 1)) * MAX_MOTOR_SPEED / (NUM_MOTOR_SPEED - 1);
        int ax = abs(x);
        int ay = abs(y);
        int value = max(ax, ay);
        if (value > 0) {
            if (ay >= ax) {
                if (y < 0) {
                    if (x >= 0) {
                        // y < 0, x >= 0 |y| >= |x|
                        // NNE
                        return Tuple2.of(-y, -y - x);
                    } else {
                        // y < 0, x < 0 |y| >= |x|
                        // NNW
                        return Tuple2.of(-y + x, -y);
                    }
                } else if (x >= 0) {
                    // y > 0, x >= 0 |y| >= |x|
                    // SSE
                    return Tuple2.of(x - y, -y);
                } else {
                    // y > 0, x < 0 |y| >= |x|
                    // SSW
                    return Tuple2.of(-y, -y - x);
                }
            } else if (x > 0) {
                if (y <= 0) {
                    // x > 0, y <= 0 |x| >= |y|
                    // ENE
                    return Tuple2.of(x, -x - y);
                } else {
                    // x > 0, y > 0 |x| >= |y|
                    // ESE
                    return Tuple2.of(x - y, -x);
                }
            } else {
                if (y <= 0) {
                    // x < 0, y <= 0 |x| >= |y|
                    // WNW
                    return Tuple2.of(x - y, -x);
                } else {
                    // x < 0, y > 0 |x| >= |y|
                    // WSW
                    return Tuple2.of(x, -x - y);
                }
            }
        }
        return Tuple2.of(0, 0);
    }

    private final RobotController controller;
    private final RxJoystick joystick;

    /**
     * Creates a flow builder
     *
     * @param controller the controller
     * @param joystick   the joystick
     */
    protected FlowBuilder(RobotController controller, RxJoystick joystick) {
        this.controller = requireNonNull(controller);
        this.joystick = requireNonNull(joystick);
    }

    /**
     * Returns this flow builder with the build flowables
     */
    public FlowBuilder build() {
        // Motor command
        Flowable<MotorCommand> moveToFlow = createMotorCommand()
                .debounce(MIN_MOTOR_INTERVAL, TimeUnit.MILLISECONDS);
        controller.activateMotors(moveToFlow);

        // Scan command
        Flowable<Float> scanCommand = createScannerCommand();
        controller.scan(scanCommand);

        return this;
    }

    /**
     * Creates a flowable of motor speeds from joystick events
     */
    private Flowable<Tuple2<Integer, Integer>> createJoystickDirCommand() {
        logger.debug("Creating joystick command ...");
        return joystick.readXY()
                .map(FlowBuilder::speedFromAxis)
                .buffer(2, 1)
                .filter(FlowBuilder::isSpeedChanged)
                .map(x -> x.get(1));
    }

    /**
     * Returns the flowable of motor commands
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
                .map(t -> MotorCommand.create(t._1, t._2));
    }

    /**
     * Create the flow of scan command results
     */
    private Flowable<Float> createScannerCommand() {
        //noinspection unchecked
        return Flowable.mergeArray(
                        joystick.readValues(RxJoystick.BUTTON_0),
                        joystick.readValues(RxJoystick.BUTTON_1),
                        joystick.readValues(RxJoystick.BUTTON_2),
                        joystick.readValues(RxJoystick.BUTTON_3))
                .filter(x -> x > 0f);
    }

    /**
     * Returns this flow builder with detached flowables
     */
    public FlowBuilder detach() {
        return this;
    }
}
