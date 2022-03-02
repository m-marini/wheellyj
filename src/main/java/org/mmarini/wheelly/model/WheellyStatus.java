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

import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.Tuple2.toMap;

/**
 * The Wheelly status contain the sensor value of Wheelly
 */
public class WheellyStatus {

    public static final int NO_STATUS_PARAMS = 1 + 3 + 7 * 3 + 2 + 2;
    public static final double VOLTAGE_PRECISION = 5d * 3 / 1023;
    private static final int NO_DIRECTIONS = 7;

    /**
     * Returns the Wheelly status from status string
     *
     * @param statusString the status string
     */
    public static WheellyStatus from(String statusString, RemoteClock clock) {
        String[] params = statusString.split(" ");
        if (params.length != NO_STATUS_PARAMS) {
            throw new IllegalArgumentException("Missing status parameters");
        }
        long directionInstant = clock.fromRemote(Long.parseLong(params[1]));
        int left = Integer.parseInt(params[2]);
        int right = Integer.parseInt(params[3]);
        Timed<Tuple2<Integer, Integer>> direction = new Timed<>(Tuple2.of(left, right), directionInstant, TimeUnit.MILLISECONDS);

        Map<Integer, Timed<Integer>> obstacles = IntStream.range(0, NO_DIRECTIONS)
                .mapToObj(i -> {
                    long instant = Long.parseLong(params[i * 3 + 4]);
                    int dirScan = Integer.parseInt(params[i * 3 + 5]);
                    int distance = Integer.parseInt(params[i * 3 + 6]);
                    return Tuple2.of(instant, Tuple2.of(dirScan, distance));
                })
                .filter(t -> t._1 > 0)
                .map(t -> {
                            long instant = t._1;
                            int dirs = t._2._1;
                            int distance = t._2._2;
                            return Tuple2.of(dirs,
                                    new Timed<>(distance, clock.fromRemote(instant), TimeUnit.MILLISECONDS));
                        }
                )
                .collect(toMap());

        long voltageInstant = clock.fromRemote(Long.parseLong(params[25]));
        double v = Integer.parseInt(params[26]) * VOLTAGE_PRECISION;
        Timed<Double> voltage = new Timed<>(v, voltageInstant, TimeUnit.MILLISECONDS);

        long cpsInstant = clock.fromRemote(Long.parseLong(params[27]));
        double cpsValue = Integer.parseInt(params[28]);
        Timed<Double> cps = new Timed<>(cpsValue, cpsInstant, TimeUnit.MILLISECONDS);
        return new WheellyStatus(direction, obstacles, voltage, cps);
    }

    public final Timed<Double> cps;
    public final Timed<Tuple2<Integer, Integer>> direction;
    public final Map<Integer, Timed<Integer>> obstacles;
    public final Timed<Double> voltage;

    /**
     * Creates the Wheelly status
     *
     * @param direction the motor speed
     * @param obstacles the obstacles values
     * @param voltage   the voltage value
     * @param cps       the cycle per seconds
     */
    public WheellyStatus(Timed<Tuple2<Integer, Integer>> direction, Map<Integer, Timed<Integer>> obstacles, Timed<Double> voltage, Timed<Double> cps) {
        this.direction = requireNonNull(direction);
        this.obstacles = requireNonNull(obstacles);
        this.voltage = requireNonNull(voltage);
        this.cps = requireNonNull(cps);
    }

    /**
     * Returns the motor speeds
     */
    public Timed<Tuple2<Integer, Integer>> getDirection() {
        return direction;
    }


    /**
     * Returns the obstacles
     */
    public Map<Integer, Timed<Integer>> getObstacles() {
        return obstacles;
    }

    /**
     * Returns the battery voltage
     */
    public Timed<Double> getVoltage() {
        return voltage;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", WheellyStatus.class.getSimpleName() + "[", "]")
                .add("direction=" + direction)
                .add("voltage=" + voltage)
                .add("obstacles=" + obstacles)
                .toString();
    }
}
