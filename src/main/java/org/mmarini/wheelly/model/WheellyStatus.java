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

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyStatus {

    public static final int NO_STATUS_PARAMS = 15;

    /**
     * Returns the Wheelly status from status string
     * The string status is formatted as:
     * <pre>
     *     st
     *     [sampleTime]
     *     [xLocation]
     *     [yLocation]
     *     [yaw]
     *     [sensorDirection]
     *     [distance]
     *     [leftMotor]
     *     [rightMotor]
     *     [contactSignals]
     *     [voltage]
     *     [canMoveForward]
     *     [canMoveBackward]
     *     [imuFailure]
     *     [halt]
     * </pre>
     *
     * @param statusString the status string
     */
    public static Timed<WheellyStatus> from(String statusString, RemoteClock clock) {
        String[] params = statusString.split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException(format("Wrong status message \"%s\"", statusString));
        }

        long sampleInstant = clock.fromRemote(Long.parseLong(params[1]));
        double x = parseDouble(params[2]);
        double y = parseDouble(params[3]);
        int angle = Integer.parseInt(params[4]);
        RobotAsset asset = RobotAsset.create(x, y, angle);

        int sensorDirection = parseInt(params[5]);
        double distance = parseDouble(params[6]);
        int contactSensors = parseInt(params[9]);
        double voltage = parseDouble(params[10]);

        boolean canMoveForward = Integer.parseInt(params[11]) != 0;
        boolean canMoveBackward = Integer.parseInt(params[12]) != 0;
        ProxySample sample = ProxySample.create(sensorDirection, distance, asset, contactSensors, canMoveBackward, canMoveForward);

        double left = parseDouble(params[7]);
        double right = parseDouble(params[8]);
        Tuple2<Double, Double> motors = Tuple2.of(left, right);

        boolean imuFailure = Integer.parseInt(params[13]) != 0;
        boolean alt = Integer.parseInt(params[14]) != 0;

        return new Timed<>(
                new WheellyStatus(sample, motors, voltage, imuFailure, alt),
                sampleInstant, TimeUnit.MILLISECONDS);
    }

    public final boolean alt;
    public final boolean imuFailure;
    public final Tuple2<Double, Double> motors;
    public final ProxySample sample;
    public final double voltage;

    /**
     * Creates the Wheelly status
     *
     * @param sample     robot asset
     * @param motors     the motor speed
     * @param voltage    the voltage value
     * @param imuFailure true if imu failure
     * @param alt        true if alt status
     */
    protected WheellyStatus(ProxySample sample, Tuple2<Double, Double> motors, double voltage, boolean imuFailure, boolean alt) {
        this.sample = requireNonNull(sample);
        this.motors = requireNonNull(motors);
        this.voltage = voltage;
        this.imuFailure = imuFailure;
        this.alt = alt;
    }

    public ProxySample getSample() {
        return sample;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add("sample=" + sample)
                .add("motors=" + motors)
                .add("voltage=" + voltage)
                .add("alt=" + alt)
                .add("imuFailure=" + imuFailure)
                .toString();
    }
}
