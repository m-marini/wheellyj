/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.apps;

import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.ParallelProcess;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.BinArrayFileMap;
import org.mmarini.rl.agents.CSVWriter;
import org.mmarini.wheelly.swing.Messages;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static java.lang.Math.*;

/**
 * Runs the process to produce report data about learning kpis
 */
public class Report {
    public static final int ERROR_INDEX = 2;
    private static final int DEFAULT_NUM_BINS = 11;
    private static final long DEFAULT_BATCH_SIZE = 300;
    private static final Logger logger = LoggerFactory.getLogger(Report.class);
    private static final int N_INDEX = 0;
    private static final int MIN_INDEX = 1;
    private static final int MAX_INDEX = ERROR_INDEX;
    private static final int AVG_INDEX = 3;
    private static final int AVGLOG_INDEX = 5;
    private static final long X_INDEX = 0;
    private static final long Y_MEAN_INDEX = 1;
    private static final long Y_MIN_INDEX = ERROR_INDEX;
    private static final long Y_MAX_INDEX = 3;
    private static final File TEMP_PATH = new File("tmp");
    private static final Map<String, Optional<UnaryOperator<INDArray>>> KPIS = Map.of(
            "delta", Optional.empty(),
            "reward", Optional.empty(),
            "deltaPhase1", Optional.empty(),
            "netGrads.direction", Optional.of(Report::maxAbs),
            "netGrads.speed", Optional.of(Report::maxAbs),
            "netGrads.sensorAction", Optional.of(Report::maxAbs),
            "policy.direction", Optional.of(Report::maxAbs),
            "policy.speed", Optional.of(Report::maxAbs),
            "policy.sensorAction", Optional.of(Report::maxAbs)
    );

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(Report.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-b", "--batchSize")
                .setDefault(DEFAULT_BATCH_SIZE)
                .type(Long.class)
                .help("batch size");
        parser.addArgument("kpis")
                .required(true)
                .help("specify the source kpis path");
        parser.addArgument("reportPath")
                .required(true)
                .help("specify the destination report path");
        return parser;
    }

    /**
     * Returns the exponential regression by parameters
     *
     * @param x      the input data
     * @param params the parameters (m,q)
     */
    private static INDArray exponential(INDArray x, INDArray params) {
        return Transforms.exp(linear(x, params));
    }

    /**
     * Returns the linear regression by parameters
     *
     * @param x      the input data
     * @param params the parameters (m,q)
     */
    private static INDArray linear(INDArray x, INDArray params) {
        return x.mul(params.getScalar(0)).addi(params.getScalar(1));
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new Report(parser.parseArgs(args)).start();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error generating report");
            System.exit(2);
        }
    }

    /**
     * Returns the max of absolute value of columns
     *
     * @param records the records
     */
    static INDArray maxAbs(INDArray records) {
        INDArray result = Transforms.abs(records).max(1);
        return result.reshape(records.size(0), 1);
    }

    private final int numBins;
    private final long numChartPoints;
    private final long batchSize;
    protected Namespace args;
    // Second pass computes distribution
    private BinArrayFileMap files;
    private File reportPath;

    /**
     * Creates the report application
     *
     * @param args the parsed arguments
     */
    protected Report(Namespace args) {
        this.args = args;
        this.batchSize = args.getLong("batchSize");
        this.numBins = DEFAULT_NUM_BINS;
        this.numChartPoints = 200;
    }

    /**
     * Creates the chart
     *
     * @param file the file to read
     * @param key  the out key
     */
    private void chart(BinArrayFile file, String key, INDArray stats) throws IOException {
        CSVWriter out = CSVWriter.createByKey(reportPath, key);
        try {
            logger.atInfo().log("Creating {} ...", out.file());
            long n = stats.getLong(N_INDEX);
            long numPoints = min(n, numChartPoints);
            long stride = n / (numPoints + 1);
            long len = n - (numPoints - 1) * stride;
            INDArray chart = Nd4j.tile(
                            Nd4j.createFromArray(0f, 0f, Float.MAX_VALUE, -Float.MAX_VALUE),
                            (int) numPoints)
                    .reshape(numPoints, 4);
            file = file.dup();
            try {
                for (long idx = 0; idx < numPoints; idx++) {
                    file.seek(idx * stride);
                    long j = 0;
                    double mean = 0;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    // sample: (x, mean, min, max)
                    while (j < len) {
                        long m = min(len, batchSize);
                        logger.atDebug().log("Reading chart {} at {}", file.file(), file.position());
                        INDArray data = file.read(m);
                        if (data == null) {
                            break;
                        }
                        mean += data.sumNumber().floatValue();
                        min = min(min, data.minNumber().floatValue());
                        max = max(max, data.maxNumber().floatValue());
                        j += data.size(0);
                    }
                    chart.putScalar(idx, X_INDEX, idx * stride + j / 2f);
                    chart.putScalar(idx, Y_MEAN_INDEX, mean / j);
                    chart.putScalar(idx, Y_MIN_INDEX, min);
                    chart.putScalar(idx, Y_MAX_INDEX, max);
                }
                out.write(chart);
            } finally {
                file.close();
            }
            logger.atInfo().log("Created {}", out.file());
        } finally {
            out.close();
        }
    }

    /**
     * Computes the histogram
     *
     * @param file  the file
     * @param key   the output key
     * @param stats the statistic of dataset
     * @throws IOException in case of error
     */
    void hist(BinArrayFile file, String key, INDArray stats) throws IOException {
        CSVWriter out = CSVWriter.createByKey(reportPath, key);
        try {
            logger.atInfo().log("Creating {} ...", out.file());
            INDArray hist = Nd4j.zeros(2, numBins);
            double min = stats.getDouble(MIN_INDEX);
            double max = stats.getDouble(MAX_INDEX);
            if ((max - min) < 1e-20) {
                min = (max - min) - 1e-20;
                max = (max - min) + 1e-20;
            }
            double dx = (max - min) / (numBins - 1);
            hist.get(NDArrayIndex.point(1), NDArrayIndex.all()).assign(Nd4j.arange(min, max + dx, dx));
            file = file.dup();
            try {
                file.seek(0);
                for (; ; ) {
                    logger.atDebug().log("Reading hist {} {} at {}", file.hashCode(), file.file(), file.position());
                    INDArray data = file.read(batchSize);
                    if (data == null) {
                        break;
                    }
                    // bin = (v - min) / (min-max) * m
                    INDArray bins = Transforms.round(
                            data.sub(min).muli(numBins / (max - min)));
                    for (long i = 0; i < bins.size(0); i++) {
                        long bin = min(round(bins.getLong(i)), numBins - 1);
                        hist.getScalar(0, bin).addi(1);
                    }
                }
            } finally {
                file.close();
            }
            out.write(hist);
            logger.atInfo().log("Created {}", out.file());
        } finally {
            out.close();
        }
    }

    /**
     * Process a key dataset
     *
     * @param key the key
     * @throws IOException in case of error
     */
    private void process(String key, UnaryOperator<INDArray> reducer) throws IOException {
        BinArrayFile inFile = files.get(key);
        BinArrayFile file = reducer != null ? reduce(key, inFile, reducer) : inFile;
        // First pass compute min, max, tot, sqTot
        INDArray stats;
        try {
            CSVWriter out = CSVWriter.createByKey(reportPath, key + ".stats");
            try {
                logger.atInfo().log("Creating {} ...", out.file());
                stats = stats(file);
                out.write(stats);
                logger.atInfo().log("Created {}", out.file());
            } finally {
                out.close();
            }
        } finally {
            file.close();
        }
        ParallelProcess.TaskScheduler tasks = ParallelProcess.scheduler(Schedulers.computation())
                .add(() -> regression(file,
                                key + ".linear",
                                stats.getLong(N_INDEX),
                                stats.getDouble(AVG_INDEX),
                                UnaryOperator.identity(),
                                Report::linear),
                        () -> hist(file, key + ".histogram", stats),
                        () -> chart(file, key + ".chart", stats));
        if (stats.getDouble(MIN_INDEX) > 0) {
            tasks = tasks.add(() -> regression(file,
                    key + ".exponential",
                    stats.getLong(N_INDEX),
                    stats.getDouble(AVGLOG_INDEX),
                    Transforms::log,
                    Report::exponential));
        }
        tasks.run();
        logger.atInfo().log("Completed key {}", key);
    }

    /**
     * Returns the reduced input records file
     *
     * @param key     the key
     * @param file    the input file
     * @param reducer the reducer
     * @throws IOException in case of error
     */
    private BinArrayFile reduce(String key, BinArrayFile file, UnaryOperator<INDArray> reducer) throws IOException {
        BinArrayFile result = BinArrayFile.createBykey(TEMP_PATH, key);
        result.clear();
        try {
            file.seek(0);
            for (; ; ) {
                INDArray data = file.read(batchSize);
                if (data == null) {
                    break;
                }
                INDArray reduced = reducer.apply(data);
                result.write(reduced);
            }
            result.close();
            return result;
        } finally {
            file.close();
        }
    }

    /**
     * Returns the regression parameters (m, q)
     *
     * @param file       the data file
     * @param key        the output key
     * @param n          the number of samples
     * @param ym         the average y
     * @param mapper     the data mapper
     * @param regression the regression function
     */
    void regression(BinArrayFile file, String key, long n, double ym,
                    UnaryOperator<INDArray> mapper,
                    BiFunction<INDArray, INDArray, INDArray> regression) throws IOException {
        CSVWriter out = CSVWriter.createByKey(reportPath, key);
        try {
            logger.atInfo().log("Creating ... {}", out.file());
            double sxx = 0;
            double sxy = 0;
            double xm = (n - 1) / 2d;
            file = file.dup();
            try {
                file.seek(0);
                for (long i = 0; ; ) {
                    logger.atDebug().log("Reading regression {} {} at {}", file.hashCode(), file.file(), file.position());
                    INDArray data = file.read(batchSize);
                    if (data == null) {
                        break;
                    }
                    INDArray y = mapper.apply(data);
                    long m = y.size(0);
                    INDArray dy = y.sub(ym);
                    INDArray dx = Nd4j.arange(i, i + m);
                    dx = dx.reshape(m, 1).subi(xm);
                    sxx += dx.mul(dx).sumNumber().doubleValue();
                    sxy += dy.mul(dx).sumNumber().doubleValue();
                    i += data.size(0);
                    data.close();
                }
                double mm = sxy / sxx;
                double qq = ym - mm * xm;
                INDArray reg = Nd4j.createFromArray(mm, qq, 0).reshape(1, 3);

                // Computes the average error
                double error = 0;
                file.seek(0);
                for (long i = 0; ; ) {
                    INDArray data = file.read(batchSize);
                    if (data == null) {
                        break;
                    }
                    long m = data.size(0);
                    INDArray x = Nd4j.arange(i, i + m).reshape(m, 1);
                    INDArray y = regression.apply(x, reg);
                    INDArray err2 = data.sub(y);
                    err2.muli(err2);
                    error += err2.sumNumber().doubleValue();
                    i += data.size(0);
                }
                reg.putScalar(ERROR_INDEX, sqrt(error / (n - 1)));
                out.write(reg);
            } finally {
                file.close();
            }
            logger.atInfo().log("Created {}", out.file());
        } finally {
            out.close();
        }
    }

    /**
     * Start to produce the report
     *
     * @throws IOException in case of error
     */
    protected void start() throws IOException {
        File kpisPath = new File(args.getString("kpis"));
        this.reportPath = new File(args.getString("reportPath"));
        this.files = BinArrayFileMap.create(kpisPath, "");
        ParallelProcess.scheduler(Tuple2.stream(KPIS)
                        .filter(t -> files.contains(t._1))
                        .map(t -> (Action) () -> process(t._1, t._2.orElse(null)))
                        .toList())
                .run();
        logger.atInfo().log("Completed.");
    }

    /**
     * Returns the statistic of reader (n, min, max, avg, sigma, avg(log))
     *
     * @param file the file
     * @throws IOException in case of error
     */
    INDArray stats(BinArrayFile file) throws IOException {
        // we use
        double minValue = Double.MAX_VALUE;
        double maxValue = -Double.MAX_VALUE;
        double t1 = 0;
        double t2 = 0;
        double t3 = 0;
        long n = 0;
        file = file.dup();
        try {
            file.seek(0);
            /*
             * Each workspace is tied to a JVM Thread via ID. So, same ID in different threads will point to different actual workspaces
             * Each workspace is created using some configuration, and different workspaces can either share the same config, or have their own
             */

            // we create config with 10MB memory space pre allocated
            for (; ; ) {
                INDArray data = file.read(batchSize);
                if (data == null) {
                    break;
                }
                minValue = min(minValue, data.minNumber().doubleValue());
                maxValue = max(maxValue, data.maxNumber().doubleValue());
                double sum = data.sumNumber().doubleValue();
                t1 += sum;
                t2 += sum * sum;
                if (minValue > 0) {
                    t3 += Transforms.log(data).sumNumber().doubleValue();
                }
                n += data.size(0);
            }
            double avg = t1 / n;
            double sigma = sqrt((t2 - t1 * t1 / n) / (n - 1));
            double avglog = t3 / n;
            return Nd4j.createFromArray((double) n, minValue, maxValue, avg, sigma, minValue > 0 ? avglog : 0).reshape(1, 6);
        } finally {
            file.close();
        }
    }
}
