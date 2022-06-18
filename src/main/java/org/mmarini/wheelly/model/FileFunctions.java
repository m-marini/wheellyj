/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableSubscriber;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.schedulers.Timed;
import io.reactivex.rxjava3.subscribers.DefaultSubscriber;
import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static java.lang.Math.min;
import static org.nd4j.common.util.MathUtils.round;

public interface FileFunctions {
    Logger logger = LoggerFactory.getLogger(FileFunctions.class);
    int WRITE_MONITOR_INTERVAL = 5000;

    static Tuple2<Timed<MapStatus>, Tuple2<MotionCommand, Integer>> fromDumpLine(String line) {
        String[] values = line.split(",");
        int idx = 0;
        long timestamp = parseLong(values[idx++]);
        double robotX = parseDouble(values[idx++]);
        double robotY = parseDouble(values[idx++]);
        int robotDeg = parseInt(values[idx++]);
        int sensorDeg = parseInt(values[idx++]);
        double distance = parseDouble(values[idx++]);
        double left = parseDouble(values[idx++]);
        double right = parseDouble(values[idx++]);
        int contacts = parseInt(values[idx++]);
        boolean cannotMoveForward = parseInt(values[idx++]) != 0;
        boolean cannotMoveBackward = parseInt(values[idx++]) != 0;
        boolean imuFailure = parseInt(values[idx++]) != 0;
        boolean halt = parseInt(values[idx++]) != 0;
        int moveDeg = parseInt(values[idx++]);
        double moveSpeed = parseDouble(values[idx++]);
        int nextSensorDeg = round(parseDouble(values[idx++]));

        WheellyStatus whelly = WheellyStatus.create(new Point2D.Double(robotX, robotY),
                robotDeg,
                sensorDeg,
                distance,
                left, right,
                contacts, 0,
                !cannotMoveForward, !cannotMoveBackward,
                imuFailure,
                halt,
                moveDeg, moveSpeed,
                nextSensorDeg);

        int noObstacles = parseInt(values[idx++]);
        List<Obstacle> obstacles = new ArrayList<>();
        for (int i = 0; i < noObstacles; i++) {
            double obsX = parseDouble(values[idx++]);
            double obsY = parseDouble(values[idx++]);
            long obsTimestamp = parseLong(values[idx++]);
            double likelihood = parseDouble(values[idx++]);
            obstacles.add(Obstacle.create(obsX, obsY, obsTimestamp, likelihood));
        }
        GridScannerMap map = GridScannerMap.create(obstacles, GridScannerMap.THRESHOLD_DISTANCE, GridScannerMap.THRESHOLD_DISTANCE, 0);

        MapStatus status = MapStatus.create(whelly, map);
        Timed<MapStatus> timed = new Timed<>(status, timestamp, TimeUnit.MILLISECONDS);

        boolean haltCmd = parseInt(values[idx++]) != 0;
        int moveCmdDeg = parseInt(values[idx++]);
        double moveCmdSpeed = parseDouble(values[idx++]);
        int sensorCmdDeg = parseInt(values[idx++]);
        MotionCommand motionCmd = haltCmd ? HaltCommand.HALT_COMMAND : MoveCommand.create(moveCmdDeg, moveCmdSpeed);
        Tuple2<MotionCommand, Integer> cmd = Tuple2.of(motionCmd, sensorCmdDeg);
        return Tuple2.of(timed, cmd);
    }

    static Flowable<Tuple2<Timed<MapStatus>, Tuple2<MotionCommand, Integer>>> readDumpFile(File file) {
        return readFile(file).map(FileFunctions::fromDumpLine);
    }

    static Flowable<String> readFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            return readFile(reader);
        } catch (FileNotFoundException e) {
            return Flowable.error(e);
        }
    }

    static Flowable<String> readFile(BufferedReader reader) {
        return Flowable.<String>create(emitter -> {
                    try (reader) {
                        for (; ; ) {
                            String line = reader.readLine();
                            if (line == null) {
                                break;
                            }
                            emitter.onNext(line);
                        }
                        emitter.onComplete();
                    } catch (IOException ex) {
                        emitter.onError(ex);
                    }
                }, BackpressureStrategy.BUFFER)
                .subscribeOn(Schedulers.io());
    }

    static String toCSVRaw(INDArray data) {
        long n = data.shape()[1];
        StringJoiner joiner = new StringJoiner(",");
        for (long i = 0; i < n; i++) {
            joiner.add(Double.toString(data.getDouble(0, i)));
        }
        return joiner.toString();
    }

    static String toString(Tuple2<Timed<MapStatus>, Tuple2<MotionCommand, Integer>> tuple) {
        Timed<MapStatus> status = tuple._1;
        Tuple2<MotionCommand, Integer> command = tuple._2;
        WheellyStatus wheelly = status.value().getWheelly();
        GridScannerMap map = status.value().getMap();
        MotionCommand moveCmd = command._1;
        int sensor = command._2;

        StringJoiner joiner = new StringJoiner(",");
        joiner.add(String.valueOf((status.time(TimeUnit.MILLISECONDS))));
        joiner.add(String.valueOf(wheelly.getRobotLocation().getX()));
        joiner.add(String.valueOf(wheelly.getRobotLocation().getY()));
        joiner.add(String.valueOf(wheelly.getRobotDeg()));
        joiner.add(String.valueOf(wheelly.getSensorRelativeDeg()));
        joiner.add(String.valueOf(wheelly.getSampleDistance()));
        joiner.add(String.valueOf(wheelly.getLeftSpeed()));
        joiner.add(String.valueOf(wheelly.getRightSpeed()));
        joiner.add(String.valueOf(wheelly.getContactSensors()));
        joiner.add(String.valueOf(wheelly.getCannotMoveForward() ? 1 : 0));
        joiner.add(String.valueOf(wheelly.getCannotMoveBackward() ? 1 : 0));
        joiner.add(String.valueOf(wheelly.isImuFailure() ? 1 : 0));
        joiner.add(String.valueOf(wheelly.isHalt() ? 1 : 0));
        joiner.add(String.valueOf(wheelly.getMoveDeg()));
        joiner.add(String.valueOf(wheelly.getMoveSpeed()));
        joiner.add(String.valueOf(wheelly.getNextSensorDeg()));

        List<Obstacle> obstacles = map.getObstacles();
        joiner.add(String.valueOf(obstacles.size()));
        for (Obstacle obstacle : obstacles) {
            joiner.add(String.valueOf(obstacle.getLocation().getX()));
            joiner.add(String.valueOf(obstacle.getLocation().getY()));
            joiner.add(String.valueOf(obstacle.getTimestamp()));
            joiner.add(String.valueOf(obstacle.getLikelihood()));
        }

        if (moveCmd instanceof HaltCommand) {
            joiner.add("1");
            joiner.add("0");
            joiner.add("0");
        } else {
            joiner.add("0");
            joiner.add(String.valueOf(((MoveCommand) moveCmd).direction));
            joiner.add(String.valueOf(((MoveCommand) moveCmd).speed));
        }
        joiner.add(String.valueOf(sensor));
        return joiner.toString();
    }

    static FlowableSubscriber<String> writeFile(File file) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(file, true));
        return writeFile(writer);
    }

    static FlowableSubscriber<String> writeFile(PrintWriter writer) {
        return new DefaultSubscriber<>() {
            long last = System.currentTimeMillis() + WRITE_MONITOR_INTERVAL;
            long count;

            @Override
            public void onComplete() {
                logger.info("Written {} records", count);
                writer.close();
            }

            @Override
            public void onError(Throwable throwable) {
                Utils.logger.error(throwable.getMessage(), throwable);
                writer.close();
            }

            @Override
            public void onNext(String line) {
                count++;
                if (System.currentTimeMillis() >= last) {
                    last += WRITE_MONITOR_INTERVAL;
                    logger.info("Written {} records", count);
                }
                writer.println(line);
            }
        };
    }

    static FlowableSubscriber<String[]> writeFiles(File[] files) {
        PrintWriter[] printers = Arrays.stream(files)
                .map(file -> {
                    try {
                        return new PrintWriter(new FileWriter(file, true));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(PrintWriter[]::new);
        return writeFiles(printers);
    }

    static FlowableSubscriber<String[]> writeFiles(PrintWriter[] writers) {
        return new DefaultSubscriber<>() {
            long last = System.currentTimeMillis() + WRITE_MONITOR_INTERVAL;
            long count;

            @Override
            public void onComplete() {
                logger.info("Written {} records", count);
                for (PrintWriter writer : writers) {
                    writer.close();
                }
            }

            @Override
            public void onError(Throwable throwable) {
                Utils.logger.error(throwable.getMessage(), throwable);
                for (PrintWriter writer : writers) {
                    writer.close();
                }
            }

            @Override
            public void onNext(String[] lines) {
                count++;
                if (System.currentTimeMillis() >= last) {
                    last += WRITE_MONITOR_INTERVAL;
                    logger.info("Written {} records", count);
                }
                for (int i = 0; i < min(writers.length, lines.length); i++) {
                    writers[i].println(lines[i]);
                }
            }
        };
    }
}
