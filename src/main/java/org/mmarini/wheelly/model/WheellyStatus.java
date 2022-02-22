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

import org.mmarini.Tuple2;

import java.time.Instant;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;
import static org.mmarini.Tuple2.toMap;

/**
 *
 */
public class WheellyStatus {

    public static final int NO_STATUS_PARMS = 1 + 3 + 7 * 3 + 2 + 2;
    public static final double VOLTAGE_PRECISION = 5d * 3 / 1023;
    private static final int NO_DIRECTIONS = 7;

    /**
     * @param statusString
     * @return
     */
    public static WheellyStatus from(String statusString, RemoteClock clock) {
        String[] parms = statusString.split(" ");
        if (parms.length != NO_STATUS_PARMS) {
            throw new IllegalArgumentException("Missing status parameters");
        }
        Instant directionInstant = clock.fromRemote(Long.parseLong(parms[1]));
        int left = Integer.parseInt(parms[2]);
        int right = Integer.parseInt(parms[3]);
        InstantValue<Tuple2<Integer, Integer>> direction = InstantValue.of(directionInstant, Tuple2.of(left, right));

        Map<Integer, InstantValue<Integer>> obstacles = IntStream.range(0, NO_DIRECTIONS)
                .mapToObj(i -> {
                    long instant = Long.parseLong(parms[i * 3 + 4]);
                    int dirScan = Integer.parseInt(parms[i * 3 + 5]);
                    int distance = Integer.parseInt(parms[i * 3 + 6]);
                    return Tuple2.of(instant, Tuple2.of(dirScan, distance));
                })
                .filter(t -> t._1 > 0)
                .map(t -> {
                            long instant = t._1;
                            int dirs = t._2._1;
                            int distance = t._2._2;
                            return Tuple2.of(dirs,
                                    InstantValue.of(clock.fromRemote(instant), distance));
                        }
                )
                .collect(toMap());

        Instant voltageInstant = clock.fromRemote(Long.parseLong(parms[25]));
        double v = Integer.parseInt(parms[26]) * VOLTAGE_PRECISION;
        InstantValue<Double> voltage = InstantValue.of(voltageInstant, v);

        Instant cpsInstant = clock.fromRemote(Long.parseLong(parms[27]));
        double cpsValue = Integer.parseInt(parms[28]);
        InstantValue<Double> cps = InstantValue.of(cpsInstant, cpsValue);
        return new WheellyStatus(direction, obstacles, voltage, cps);
    }

    public final InstantValue<Tuple2<Integer, Integer>> direction;
    public final Map<Integer, InstantValue<Integer>> obstacles;
    public final InstantValue<Double> voltage;
    public final InstantValue<Double> cps;

    /**
     * @param direction
     * @param obstacles
     * @param voltage
     * @param cps
     */
    public WheellyStatus(InstantValue<Tuple2<Integer, Integer>> direction, Map<Integer, InstantValue<Integer>> obstacles, InstantValue<Double> voltage, InstantValue<Double> cps) {
        this.direction = requireNonNull(direction);
        this.obstacles = requireNonNull(obstacles);
        this.voltage = requireNonNull(voltage);
        this.cps = requireNonNull(cps);
    }

    /**
     * @return
     */
    public InstantValue<Tuple2<Integer, Integer>> getDirection() {
        return direction;
    }


    /**
     *
     */
    public Map<Integer, InstantValue<Integer>> getObstacles() {
        return obstacles;
    }

    /**
     *
     */
    public InstantValue<Double> getVoltage() {
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
