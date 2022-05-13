package org.mmarini.wheelly.engines;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.*;
import org.mmarini.wheelly.swing.RxJoystick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static io.reactivex.rxjava3.core.Flowable.*;
import static java.lang.Math.*;
import static org.mmarini.wheelly.model.Utils.normalizeAngle;

/**
 * The manual engine manage the input of joystick and produce the commands
 */
public class ManualEngine implements InferenceEngine {
    private static final Logger logger = LoggerFactory.getLogger(ManualEngine.class);
    private static final double MAX_ROT_SPEED = 2 * PI / 2;

    public static ManualEngine create(RxJoystick joystick) {
        return new ManualEngine(joystick);
    }

    @SafeVarargs
    static <T> Flowable<T> intervalItems(long time, T... data) {
        return intervalRange(0, data.length, 0, time, TimeUnit.MILLISECONDS)
                .map(i -> data[i.intValue()]);
    }

    private final RxJoystick joystick;
    private double direction;
    private double speed;
    private int scannerDirection;
    private boolean alt;

    protected ManualEngine(RxJoystick joystick) {
        this.joystick = joystick;
        alt = true;
        scannerDirection = 0;
        createScannerCommand()
                .observeOn(Schedulers.computation())
                .subscribe(deg -> {
                    scannerDirection = deg;
                    logger.debug("scan angle {}", deg);
                });
        createJoystickFlow()
                .observeOn(Schedulers.computation())
                .subscribe(this::handleJoystickEvent);
    }

    /**
     * Returns the flowable of motor commands
     */
    private Flowable<Timed<Tuple2<Float, Float>>> createJoystickFlow() {
        return combineLatest(
                interval(100, TimeUnit.MILLISECONDS),
                joystick.readXY(),
                (i, xy) -> xy)
                .onBackpressureDrop()
                .timeInterval();
    }

    /**
     * Create the flow of scan command results
     */
    private Flowable<Integer> createScannerCommand() {
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

        @SuppressWarnings("unchecked") Flowable<Integer> fullScann = mergeArray(
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

    private void handleJoystickEvent(Timed<Tuple2<Float, Float>> event) {
        long dt = event.time(TimeUnit.MILLISECONDS);
        double x = event.value()._1;
        double y = event.value()._2;
        logger.debug("event {}", event);
        if (x == 0 && y == 0) {
            alt = true;
        } else {
            alt = false;
            speed = -y;
            double da = x * MAX_ROT_SPEED * dt / 1000;
            direction = normalizeAngle(direction + da);
            logger.debug("speed: {}, da: {}, directionRad {}, directionDeg{} ", speed, da, direction, toDegrees(direction));
        }
    }

    @Override
    public InferenceEngine init(InferenceMonitor monitor) {
        return this;
    }

    @Override
    public Tuple2<MotionComand, Integer> process(Timed<MapStatus> data, InferenceMonitor monitor) {
        MotionComand cmd = alt ? AltCommand.create() : MoveCommand.create((int) round(toDegrees(direction)), speed);
        return Tuple2.of(cmd, scannerDirection);
    }
}
