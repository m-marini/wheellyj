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

import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;

import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyStatus {

    public static final int NO_STATUS_PARAMS = 12;

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     st [sampleTime] [xLocation] [yLocation] [angle] [leftMotor] [rightMotor] [canMoveForward] [voltageTime] [voltage] [cpsTime] [cps]
     * </pre>
     *
     * @param statusString the status string
     */
    public static WheellyStatus from(String statusString, RemoteClock clock) {
        String[] params = statusString.split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException("Missing status parameters");
        }

        long sampleInstant = clock.fromRemote(Long.parseLong(params[1]));
        float x = Float.parseFloat(params[2]);
        float y = Float.parseFloat(params[3]);
        int angle = Integer.parseInt(params[4]);
        Timed<RobotAsset> asset = new Timed<>(RobotAsset.create(x, y, angle), sampleInstant, TimeUnit.MILLISECONDS);

        float left = Float.parseFloat(params[5]);
        float right = Float.parseFloat(params[6]);
        Timed<Tuple2<Float, Float>> motors = new Timed<>(Tuple2.of(left, right), sampleInstant, TimeUnit.MILLISECONDS);

        boolean moveForward = Integer.parseInt(params[7]) != 0;
        Timed<Boolean> canMoveForward = new Timed<>(moveForward, sampleInstant, TimeUnit.MILLISECONDS);
        ;

        long voltageInstant = clock.fromRemote(Long.parseLong(params[8]));
        float v = Float.parseFloat(params[9]);
        Timed<Float> voltage = new Timed<>(v, voltageInstant, TimeUnit.MILLISECONDS);

        long cpsInstant = clock.fromRemote(Long.parseLong(params[10]));
        float cpsValue = Float.parseFloat(params[11]);
        Timed<Float> cps = new Timed<>(cpsValue, cpsInstant, TimeUnit.MILLISECONDS);

        return new WheellyStatus(asset, motors, canMoveForward, voltage, cps);
    }

    public final Timed<RobotAsset> asset;
    public final Timed<Boolean> canMoveForward;
    public final Timed<Float> cps;
    public final Timed<Tuple2<Float, Float>> motors;
    public final Timed<Float> voltage;

    /**
     * Creates the Wheelly status
     *
     * @param asset          robot asset
     * @param motors         the motor speed
     * @param canMoveForward true if robot can move forward
     * @param voltage        the voltage value
     * @param cps            the cycle per seconds
     */
    protected WheellyStatus(Timed<RobotAsset> asset, Timed<Tuple2<Float, Float>> motors, Timed<Boolean> canMoveForward, Timed<Float> voltage, Timed<Float> cps) {
        this.asset = requireNonNull(asset);
        this.motors = requireNonNull(motors);
        this.canMoveForward = canMoveForward;
        this.voltage = requireNonNull(voltage);
        this.cps = requireNonNull(cps);
    }

    /**
     * Returns the motor speeds
     */
    public Timed<Tuple2<Float, Float>> getMotors() {
        return motors;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add("asset=" + asset)
                .add("motors=" + motors)
                .add("voltage=" + voltage)
                .add("cps=" + cps)
                .toString();
    }
}
