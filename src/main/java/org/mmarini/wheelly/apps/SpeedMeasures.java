/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
 *
 *  Permission is hereby granted, free of charge, to any person
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

package org.mmarini.wheelly.apps;

import org.mmarini.MapStream;
import org.mmarini.Tuple2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.abs;
import static java.lang.Math.round;

public interface SpeedMeasures {

    /**
     * Regex for the status message
     * <test-time>,<test-execution>,<left-power>,<right-power>,<left-pulses>>,<right-pulses>,<front-sensor>,<rear-sensor>,<front-distance>,<rear-distance>
     */
    Pattern ARG_PATTERN = Pattern.compile("^(\\d+),(\\d+),(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)$");

    /**
     * Returns the power report for the given status chronological list
     *
     * @param states the status chronological list
     */
    static SpeedMeasure computeReportSpeed(List<Tuple2<Integer, MotorStatus>> states) {
        if (states.size() >= 2) {
            MotorStatus first = states.getFirst()._2;
            MotorStatus last = states.getLast()._2;
            long startTime = first.time();
            long endTime = last.time();
            int startPulses = first.pulses();
            int endPulses = last.pulses();
            long dt = endTime - startTime;
            int dp = endPulses - startPulses;
            return new SpeedMeasure(dt, dp);
        } else {
            return new SpeedMeasure(0, 0);
        }
    }

    /**
     * Returns the average supply
     *
     * @param list the list of motor status
     */
    private static int computeSupply(List<Tuple2<Integer, MotorStatus>> list) {
        return (int) round(list.stream()
                .mapToInt(t -> t._2.supply())
                .average()
                .orElse(0D));
    }

    /**
     * Returns the left motor status
     *
     * @param status the wheelly measure status
     */
    static MotorStatus createLeftMotorStatus(Status status) {
        return new MotorStatus(status.time(), status.leftPower(), status.leftPulses(), status.supply());
    }

    /**
     * Returns the right motor status
     *
     * @param status the wheelly measure status
     */
    static MotorStatus createRightMotorStatus(Status status) {
        return new MotorStatus(status.time(), status.rightPower(), status.rightPulses(), status.supply());
    }

    /**
     * Returns the samples from status list
     *
     * @param leftMotor true if left motor
     * @param list      the status list
     */
    static Map<SampleKey, SpeedMeasure> createSamplesByMotor(boolean leftMotor, List<MotorStatus> list) {
        int max = list.stream()
                .mapToInt(MotorStatus::power)
                .max()
                .orElse(0);
        int min = list.stream()
                .mapToInt(MotorStatus::power)
                .min()
                .orElse(0);
        int limit = abs(max) >= abs(min) ? max : min;
        long stepUpTime = list.stream()
                .filter(s -> s.power == limit)
                .mapToLong(MotorStatus::time)
                .max()
                .orElse(0);
        Map<SampleKey, SpeedMeasure> incrementReport = createSamplesByType(
                leftMotor ? SampleType.LeftMotorIncreaseSpeed : SampleType.RightMotorIncreaseSpeed,
                list.stream()
                        .filter(s -> s.time() <= stepUpTime));
        long splitDownTime = list.stream()
                .filter(s -> s.power == limit)
                .mapToLong(MotorStatus::time)
                .min()
                .orElse(0);
        Map<SampleKey, SpeedMeasure> decrementReport = createSamplesByType(leftMotor ? SampleType.LeftMotorDecreaseSpeed : SampleType.RightMotorDecreaseSpeed,
                list.stream()
                        .filter(s -> s.time() >= splitDownTime));
        return MapStream.concatMaps(incrementReport, decrementReport).toMap();
    }

    /**
     * Returns the report of test (null if invalid test)
     *
     * @param states the wheelly measure states
     */
    static Map<SampleKey, SpeedMeasure> createSamplesByStatus(List<Status> states) {
        // Check for invalid test
        if (states.stream().anyMatch(s -> !s.frontSensor() || !s.rearSensor())) {
            return null;
        }
        Map<SampleKey, SpeedMeasure> leftReport = createSamplesByMotor(true,
                states.stream()
                        .map(SpeedMeasures::createLeftMotorStatus)
                        .toList());
        Map<SampleKey, SpeedMeasure> rightReport = createSamplesByMotor(false,
                states.stream()
                        .map(SpeedMeasures::createRightMotorStatus)
                        .toList());
        return MapStream.concatMaps(leftReport, rightReport).toMap();
    }

    /**
     * Returns the power report by power
     *
     * @param type the sample type
     * @param list the list of motor status
     */
    static Map<SampleKey, SpeedMeasure> createSamplesByType(SampleType type, Stream<MotorStatus> list) {
        Map<Integer, List<Tuple2<Integer, MotorStatus>>> map = list.map(s ->
                        Tuple2.of(s.power(), s))
                .collect(Collectors.groupingBy(Tuple2::getV1));
        return MapStream.of(map)
                .mapKeys((pow, l) ->
                        new SampleKey(type, pow, computeSupply(l))
                )
                .mapValues(SpeedMeasures::computeReportSpeed)
                .toMap();
    }

    /**
     * Returns the status by parsing the mqtt message
     *
     * @param message the message
     */
    static Status createStatus(String message) {
        Matcher matcher = ARG_PATTERN.matcher(message);
        if (!matcher.matches()) {
            return null;
        }
        long time = Long.parseLong(matcher.group(1));
        boolean isTesting = !"0".equals(matcher.group(2));
        int leftPower = Integer.parseInt(matcher.group(3));
        int rightPower = Integer.parseInt(matcher.group(4));
        int leftPulses = Integer.parseInt(matcher.group(5));
        int rightPulses = Integer.parseInt(matcher.group(6));
        boolean frontSensor = !"0".equals(matcher.group(7));
        boolean rearSensor = !"0".equals(matcher.group(8));
        int frontDistance = Integer.parseInt(matcher.group(9));
        int rearDistance = Integer.parseInt(matcher.group(10));
        int supply = Integer.parseInt(matcher.group(11));
        return new Status(time, isTesting, leftPower, rightPower, leftPulses, rightPulses, frontSensor, rearSensor, frontDistance, rearDistance, supply);
    }

    /**
     * Merges results
     *
     * @param results the results
     */
    static Map<SampleKey, SpeedMeasure> merge(Map<SampleKey, SpeedMeasure>... results) {
        if (results.length == 0) {
            return Map.of();
        }
        if (results.length == 1) {
            return results[0];
        }
        Map<SampleKey, SpeedMeasure> merge = new HashMap<>();
        for (Map<SampleKey, SpeedMeasure> map : results) {
            for (Map.Entry<SampleKey, SpeedMeasure> entry : map.entrySet()) {
                SpeedMeasure value = entry.getValue();
                merge.compute(entry.getKey(), (k, v) ->
                        v == null
                                ? value :
                                new SpeedMeasure(v.dt() + value.dt(), v.dPulses() + value.dPulses())
                );
            }
        }
        return merge;
    }

    /**
     * The type of samples
     */
    enum SampleType {
        LeftMotorIncreaseSpeed,
        LeftMotorDecreaseSpeed,
        RightMotorIncreaseSpeed,
        RightMotorDecreaseSpeed
    }

    /**
     * Speed report
     *
     * @param dt      the time difference
     * @param dPulses the pulse difference
     */
    record SpeedMeasure(long dt, int dPulses) {
        public double speed() {
            return dt != 0 ? dPulses * 1000. / dt : 0;
        }
    }

    /**
     * The key of sample
     *
     * @param type   the type of measure
     * @param power  the power
     * @param supply the supply measure
     */
    record SampleKey(SampleType type, int power, int supply) {
        public int voltage() {
            return power * supply;
        }
    }

    /**
     * The motor status
     *
     * @param time   the time of record
     * @param power  the power
     * @param pulses the number of pulses
     * @param supply the supply measure
     */
    record MotorStatus(long time, int power, int pulses, int supply) {
    }

    /**
     * The wheely measure record
     *
     * @param time          the test time
     * @param isTesting     true if testing
     * @param leftPower     the left power
     * @param rightPower    the right power
     * @param leftPulses    the left pulses
     * @param rightPulses   the right pulses
     * @param frontSensor   true if front sensor clear
     * @param rearSensor    true if rear sensor clear
     * @param frontDistance the front distance (mm)
     * @param rearDistance  the rear distance (mm)
     * @param supply        the supply sensor value (u)
     */
    record Status(long time,
                  boolean isTesting,
                  int leftPower,
                  int rightPower,
                  int leftPulses,
                  int rightPulses,
                  boolean frontSensor,
                  boolean rearSensor,
                  int frontDistance,
                  int rearDistance,
                  int supply) {
    }
}