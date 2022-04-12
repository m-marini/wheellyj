package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.MotorCommand;
import org.mmarini.wheelly.model.ScannerMap;
import org.mmarini.wheelly.model.WheellyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.lang.Math.*;

/**
 * The manual engine manage the input of joystick and produce the commands
 */
public class ManualEngine implements InferenceEngine {
    private static final long MOTOR_INTERVAL = 500;
    private static final Logger logger = LoggerFactory.getLogger(ManualEngine.class);
    private static final int NUM_MOTOR_SPEED = 11;
    private static final long MIN_MOTOR_INTERVAL = 200;

    public static ManualEngine create(RxJoystick joystick) {
        return new ManualEngine(joystick);
    }

    static <T> Flowable<T> intervalItems(long time, T... data) {
        return intervalRange(0, data.length, 0, time, TimeUnit.MILLISECONDS)
                .map(i -> data[i.intValue()]);
    }

    /**
     * Returns true if Wheelly is moving
     *
     * @param tuples the tuple list with speeds
     */
    private static boolean isMoving(List<Tuple2<Double, Double>> tuples) {
        Tuple2<Double, Double> m0 = tuples.get(0);
        Tuple2<Double, Double> m1 = tuples.get(1);
        return !(m0._1 == 0 && m0._2 == 0 && m1._1 == 0 && m1._2 == 0);
    }

    /**
     * Returns true if the speed is changing
     *
     * @param tuples the tuple list with speeds
     */
    private static boolean isSpeedChanged(List<Tuple2<Double, Double>> tuples) {
        Tuple2<Double, Double> m0 = tuples.get(0);
        Tuple2<Double, Double> m1 = tuples.get(1);
        return !m0.equals(m1);
    }

    static Tuple2<Double, Double> speedFromAxis(Tuple2<Float, Float> xy) {
        double x = (double) round(xy._1 * (NUM_MOTOR_SPEED - 1)) / (NUM_MOTOR_SPEED - 1);
        double y = (double) round(xy._2 * (NUM_MOTOR_SPEED - 1)) / (NUM_MOTOR_SPEED - 1);
        double ax = abs(x);
        double ay = abs(y);
        double value = max(ax, ay);
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
        return Tuple2.of(0d, 0d);
    }

    private final RxJoystick joystick;
    private MotorCommand command;
    private int scannerDirection;

    protected ManualEngine(RxJoystick joystick) {
        this.joystick = joystick;
        command = MotorCommand.create(0, 0);
        scannerDirection = 0;
        createScannerCommand()
                .doOnNext(deg -> scannerDirection = deg)
                .subscribe();
        createMotorCommand()
                .doOnNext(cmd -> this.command = cmd)
                .subscribe();
    }

    /**
     * Returns the flowable of motor commands
     */
    private Flowable<MotorCommand> createMotorCommand() {
        logger.debug("Creating joystick command ...");
        return joystick.readXY()
                .onBackpressureDrop()
                .map(ManualEngine::speedFromAxis)
                .map(MotorCommand::create);
    }

    /**
     * Create the flow of scan command results
     */
    private Flowable<Integer> createScannerCommand() {
        //noinspection unchecked
        Flowable<Integer> povAngle = joystick.readValues(RxJoystick.POV)
                .map(x -> {
                    switch ((int) (x * 8)) {
                        case 1:
                            return -45;
                        case 3:
                            return 45;
                        case 4:
                            return 90;
                        case 8:
                            return -90;
                        default:
                            return 0;
                    }
                })
                .distinctUntilChanged();

        Flowable<Integer> fullScann = mergeArray(
                joystick.readValues(RxJoystick.THUMB),
                joystick.readValues(RxJoystick.THUMB_2),
                joystick.readValues(RxJoystick.TOP),
                joystick.readValues(RxJoystick.TRIGGER),
                joystick.readValues(RxJoystick.BUTTON_0),
                joystick.readValues(RxJoystick.BUTTON_1),
                joystick.readValues(RxJoystick.BUTTON_2),
                joystick.readValues(RxJoystick.BUTTON_3))
                .filter(x -> x > 0f)
                .concatMap(x -> intervalItems(500, -90, -60, -30, 0, 30, 60, 90, 0));
        return merge(povAngle, fullScann);
    }

    @Override
    public Tuple2<MotorCommand, Integer> process(Tuple2<WheellyStatus, ScannerMap> data) {
        return Tuple2.of(command, scannerDirection);
    }
}
