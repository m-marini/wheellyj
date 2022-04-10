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

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Timed;
import org.mmarini.Tuple2;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.model.ReliableSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import static io.reactivex.rxjava3.core.Flowable.just;
import static java.lang.Math.sqrt;

/**
 *
 */
public class SensorReliability {
    public static final String HOST = "192.168.1.11";
    public static final int COUNT = 100;
    public static final int SKIP = 10;
    private static final Logger logger = LoggerFactory.getLogger(SensorReliability.class);

    /**
     * @param ranks
     * @param perc
     * @return
     */
    private static double centile(Tuple2<double[], double[]> ranks, double perc) {
        return centile(ranks._1, ranks._2, perc);
    }

    /**
     * @param samples
     * @param cumulative
     * @param perc
     * @return
     */
    private static double centile(double[] samples, double[] cumulative, double perc) {
        int n = samples.length;
        if (n == 0) {
            return 0;
        } else if (n == 1 || perc <= 0) {
            return samples[0];
        } else if (perc >= 1) {
            return samples[n - 1];
        } else {
            int idx = 0;
            for (int i = 0; i < n - 1; i++) {
                if (perc >= cumulative[i]) {
                    idx = i;
                    break;
                }
            }
            double x0 = samples[idx];
            double x1 = samples[idx + 1];
            double p0 = idx == 0 ? 0 : cumulative[idx - 1];
            double p1 = cumulative[idx];
            double x = (x1 - x0) * (perc - p0) / (p1 - p0) + x0;
            return x;
        }
    }

    /**
     * @param data
     * @return
     */
    private static Tuple2<double[], double[]> computeRanks(double[] data) {
        List<Double> samples = new ArrayList<>();
        List<Double> ranks = new ArrayList<>();
        int n = data.length;
        double last = data[0];
        int count = 1;
        for (int i = 1; i < n; i++) {
            double v = data[i];
            if (v != last) {
                samples.add(last);
                ranks.add((double) count / n);
                last = v;
            }
            count++;
        }
        samples.add(last);
        ranks.add((double) count / n);
        return Tuple2.of(
                samples.stream().mapToDouble(x -> x).toArray(),
                ranks.stream().mapToDouble(x -> x).toArray()
        );
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        logger.info("Started");
        new SensorReliability().start().blockingAwait();
        logger.info("Completed");
    }

    private final ReliableSocket socket;
    private final JFrame frame;
    private final JFormattedTextField reliability;
    private final JFormattedTextField average;
    private final JFormattedTextField sigma;
    private final JFormattedTextField centile0;
    private final JFormattedTextField centile10;
    private final JFormattedTextField centile25;
    private final JFormattedTextField centile50;
    private final JFormattedTextField centile75;
    private final JFormattedTextField centile90;
    private final JFormattedTextField centile100;
    private final JFormattedTextField range50;
    private final JFormattedTextField range80;
    private final JFormattedTextField range100;
    private final JToggleButton stopButton;

    /**
     *
     */
    public SensorReliability() {
        this.frame = new JFrame("Sensor reliability");
        this.reliability = new JFormattedTextField();
        this.average = new JFormattedTextField();
        this.sigma = new JFormattedTextField();
        this.centile0 = new JFormattedTextField();
        this.centile10 = new JFormattedTextField();
        this.centile25 = new JFormattedTextField();
        this.centile50 = new JFormattedTextField();
        this.centile75 = new JFormattedTextField();
        this.centile90 = new JFormattedTextField();
        this.centile100 = new JFormattedTextField();
        this.range50 = new JFormattedTextField();
        this.range80 = new JFormattedTextField();
        this.range100 = new JFormattedTextField();
        this.stopButton = new JToggleButton("Stop");

        initAll(reliability, average, sigma, centile0, centile10, centile25, centile50, centile75, centile90, centile100, range50, range80, range100);

        this.frame.setSize(400, 300);
        this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.frame.getContentPane().setLayout(new BorderLayout());
        this.frame.getContentPane().add(createContent(), BorderLayout.CENTER);


        this.socket = ReliableSocket.create(HOST, 22, 3000);
        socket.readLines()
                .doOnError(ex -> logger.error("Error reading socket", ex))
                .map(Timed::value)
                .filter(line -> line.startsWith("pr "))
                .map(s -> s.split(" "))
                .filter(ary -> ary.length >= 3)
                .map(ary -> Double.parseDouble(ary[3]))
                .buffer(COUNT, SKIP)
                .map(this::toAverage)
                .doOnNext(this::handleData)
                .doOnComplete(() -> {
                    logger.info("Stopping ...");
                    socket.println(just("stop"))
                            .doOnComplete(() -> {
                                logger.info("Closing ...");
                                socket.close();
                            }).subscribe();
                })
                .subscribe();
    }

    /**
     * @return
     */
    private JPanel createContent() {
        return new GridLayoutHelper<>(new JPanel())
                .modify("insets,2 at,0,0").add(new JLabel("Reliability"))
                .modify("at,1,0").add(reliability)
                .modify("at,0,1").add(new JLabel("Average / Sigma"))
                .modify("at,1,1").add(average)
                .modify("at,2,1").add(sigma)
                .modify("at,0,2").add(new JLabel("Median"))
                .modify("at,1,2").add(centile50)
                .modify("at,0,3").add(new JLabel("Min / Max / Range"))
                .modify("at,1,3").add(centile0)
                .modify("at,2,3").add(centile100)
                .modify("at,3,3").add(range100)
                .modify("at,0,4").add(new JLabel("10% / 90% / Range 80%"))
                .modify("at,1,4").add(centile10)
                .modify("at,2,4").add(centile90)
                .modify("at,3,4").add(range80)
                .modify("at,0,5").add(new JLabel("25% / 75% / Range 50%"))
                .modify("at,1,5").add(centile25)
                .modify("at,2,5").add(centile75)
                .modify("at,3,5").add(range50)
                .modify("at,0,6 span,4,1 center").add(stopButton)
                .getContainer();
    }

    /**
     * @param data
     */
    private void handleData(Stats data) {
        if (!stopButton.isSelected()) {
            reliability.setValue(data.reliability);
            average.setValue(data.avg);
            sigma.setValue(data.sigma);
            centile0.setValue(data.centile[0]);
            centile10.setValue(data.centile[1]);
            centile25.setValue(data.centile[2]);
            centile50.setValue(data.centile[3]);
            centile75.setValue(data.centile[4]);
            centile90.setValue(data.centile[5]);
            centile100.setValue(data.centile[6]);
            range50.setValue(data.centile[4] - data.centile[2]);
            range80.setValue(data.centile[5] - data.centile[1]);
            range100.setValue(data.centile[6] - data.centile[0]);
        }
        logger.info(data.toString());
    }

    /**
     * @param fields
     */
    private void initAll(JFormattedTextField... fields) {
        for (JFormattedTextField field : fields) {
            field.setColumns(5);
            field.setHorizontalAlignment(JFormattedTextField.RIGHT);
            field.setEditable(false);
        }
    }

    /**
     * @return
     */
    private Completable start() {
        socket.connect();
        frame.setVisible(true);
        return Completable.never();
    }

    /**
     * @param data
     * @return
     */
    private Stats toAverage(List<Double> data) {
        List<Double> valid = data.stream().filter(x -> x > 0).collect(Collectors.toList());
        int n = valid.size();
        if (n > 1) {
            valid.sort(Double::compareTo);
            double[] samples = valid.stream().mapToDouble(x -> x).toArray();
            double reliability = (double) n / data.size();
            double sum = Arrays.stream(samples).sum();
            double square = Arrays.stream(samples).map(x -> x * x).sum();
            double avg = sum / n;
            double sigma = sqrt((square - sum * sum / n) / (n - 1));
            Tuple2<double[], double[]> ranks = computeRanks(samples);
            double[] centiles = new double[]{
                    centile(ranks, 0),
                    centile(ranks, 0.1),
                    centile(ranks, 0.25),
                    centile(ranks, 0.5),
                    centile(ranks, 0.75),
                    centile(ranks, 0.9),
                    centile(ranks, 1)
            };
            return new Stats(reliability, avg, sigma, centiles);
        } else {
            return new Stats(0, 0, 0, new double[7]);
        }
    }

    /**
     *
     */
    static class Stats {
        public final double avg;
        public final double[] centile;
        public final double reliability;
        public final double sigma;

        /**
         * @param reliability
         * @param avg
         * @param sigma
         * @param centile
         */
        public Stats(double reliability, double avg, double sigma, double[] centile) {
            this.avg = avg;
            this.reliability = reliability;
            this.sigma = sigma;
            this.centile = centile;
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Stats.class.getSimpleName() + "[", "]")
                    .add("reliability=" + reliability)
                    .add("avg=" + avg)
                    .add("sigma=" + sigma)
                    .add("centile=" + Arrays.toString(centile))
                    .toString();
        }
    }
}
