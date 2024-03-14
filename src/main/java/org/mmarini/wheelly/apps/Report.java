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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.ParallelProcess;
import org.mmarini.rl.agents.BinArrayFile;
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
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static java.lang.Math.*;
import static java.lang.String.format;

/**
 * Runs the process to produce report data about learning kpis
 */
public class Report {
    public static final int ERROR_INDEX = 2;
    public static final int SIGMA_INDEX = 4;
    private static final int DEFAULT_NUM_BINS = 11;
    private static final long DEFAULT_BATCH_SIZE = 256;
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
    private static final List<ReportProcess> reports = List.of(
            ReportProcess.scalar("reward"),
            ReportProcess.scalar("delta"),
            ReportProcess.scalar("deltaPhase1"),
            ReportProcess.maxAbs("deltas.critic"),
            ReportProcess.maxAbs("deltas.direction"),
            ReportProcess.maxAbs("deltas.speed"),
            ReportProcess.maxAbs("deltas.sensorAction"),
            ReportProcess.maxAbs("layers0.direction.values"),
            ReportProcess.maxMinRatio("layers0.direction.values"),
            ReportProcess.maxAbs("layers0.speed.values"),
            ReportProcess.maxMinRatio("layers0.speed.values"),
            ReportProcess.maxAbs("layers0.sensorAction.values"),
            ReportProcess.maxMinRatio("layers0.sensorAction.values")
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
        parser.addArgument("-p", "--parallel")
                .action(Arguments.storeTrue())
                .help("run parallel tasks");
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
        return Transforms.exp(linear(x, params), false);
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

    private final int numBins;
    private final long numChartPoints;
    private final long batchSize;
    protected Namespace args;
    private File reportPath;
    private File kpisPath;

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
        Batches.Monitor monitor = new Batches.Monitor();
        try (file) {
            try (CSVWriter out = CSVWriter.createByKey(reportPath, key)) {
                logger.atInfo().log("Creating {} ...", out.file());
                long n = stats.getLong(N_INDEX);
                long numPoints = min(n, numChartPoints);
                long stride = max(n / (numPoints + 1), 1);
                long len = n - (numPoints - 1) * stride;
                try (INDArray chart = Nd4j.zeros(numPoints, 4)) {
                    for (long idx = 0; idx < numPoints; idx++) {
                        file.seek(idx * stride);
                        long j = 0;
                        float mean = 0;
                        float min = Float.MAX_VALUE;
                        float max = -Float.MAX_VALUE;
                        // sample: (x, mean, min, max)
                        while (j < len) {
                            long m = min(len, batchSize);
                            logger.atDebug().log("Reading chart {} at {}", file.file(), file.position());
                            INDArray data = file.read(m);
                            if (data == null) {
                                break;
                            }
                            j += data.size(0);
                            monitor.wakeUp(file.file(), j);
                            mean += data.sumNumber().floatValue();
                            min = min(min, data.minNumber().floatValue());
                            max = max(max, data.maxNumber().floatValue());
                        }
                        chart.putScalar(idx, X_INDEX, idx * stride + j / 2f);
                        chart.putScalar(idx, Y_MEAN_INDEX, mean / j);
                        chart.putScalar(idx, Y_MIN_INDEX, min);
                        chart.putScalar(idx, Y_MAX_INDEX, max);
                    }
                    out.write(chart);
                }
                logger.atInfo().log("Created {}", out.file());
            }
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
    void hist(BinArrayFile file, String key, INDArray stats) throws Exception {
        logger.atInfo().log("Computing histogram {} ...", file.file());
        long[] counters = new long[numBins];
        double min1 = stats.getDouble(MIN_INDEX);
        double max1 = stats.getDouble(MAX_INDEX);
        double min = (max1 - min1) < 1e-20 ? (max1 - min1) - 1e-20 : min1;
        double max = (max1 - min1) < 1e-20 ? (max1 - min1) + 1e-20 : max1;
        double dx = (max - min) / numBins;
        Batches.reduce(file, counters, batchSize,
                (counters1, data, ignored) -> {
                    try (INDArray bins = Transforms.floor(data.sub(min).divi(dx), false)) {
                        for (long i = 0; i < bins.size(0); i++) {
                            int binIndex = (int) bins.getLong(i);
                            int bin = min(binIndex, numBins - 1);
                            counters1[bin]++;
                        }
                    }
                    return counters1;
                });
        try (INDArray hist = Nd4j.zeros(2, numBins)) {
            // Initialize histogram seed
            try (INDArray x = Nd4j.arange(0, numBins).addi(0.5).muli(dx).addi(min)) {
                hist.get(NDArrayIndex.point(1), NDArrayIndex.all()).assign(x);
            }
            for (int i = 0; i < counters.length; i++) {
                hist.putScalar(0, i, counters[i]);
            }
            try (CSVWriter out = CSVWriter.createByKey(reportPath, key)) {
                logger.atInfo().log("Creating {} ...", out.file());
                out.clear();
                out.write(hist);
                logger.atInfo().log("Created {}", out.file());
            }
        }
    }

    /**
     * Processes the report
     *
     * @param report the report
     * @throws Exception in case of error
     */
    private void process(ReportProcess report) throws Exception {
        UnaryOperator<INDArray> reducer = report.reducer();
        String kpiKey = report.kpiKey();
        BinArrayFile file = reducer != null
                ? reduce(kpiKey, report.reportKey(), reducer)
                : BinArrayFile.createByKey(kpisPath, kpiKey);
        String reportKey = report.reportKey();
        try (INDArray stats = stats(reportKey, file)) {
            // Computes histogram
            hist(file, reportKey + ".histogram", stats);
            // Computes chart
            chart(file, reportKey + ".chart", stats);
            // Computes linear regression
            regression(file,
                    reportKey + ".linear",
                    stats.getLong(N_INDEX),
                    stats.getFloat(AVG_INDEX),
                    UnaryOperator.identity(),
                    Report::linear);

            // Computes exponential regression
            if (stats.getDouble(MIN_INDEX) > 0) {
                regression(file,
                        reportKey + ".exponential",
                        stats.getLong(N_INDEX),
                        stats.getFloat(AVGLOG_INDEX),
                        Transforms::log,
                        Report::exponential);
            }
        }
        logger.atInfo().log("Completed key {}", kpiKey);
    }

    /**
     * Returns the reduced input records file
     *
     * @param key     the key
     * @param outKey  the reduced key
     * @param reducer the reducer
     * @throws IOException in case of error
     */
    private BinArrayFile reduce(String key, String outKey, UnaryOperator<INDArray> reducer) throws IOException {
        logger.atInfo().log("Reducing {} ...", key);
        BinArrayFile file = BinArrayFile.createByKey(kpisPath, key);
        if (!file.file().canRead()) {
            throw new IOException(format("File %s cannot be read", file.file()));
        }
        BinArrayFile result = BinArrayFile.createByKey(TEMP_PATH, outKey);
        Batches.map(result, file, batchSize, reducer);
        logger.atInfo().log("Reduced {}.", key);
        return result;
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
    void regression(BinArrayFile file, String key, long n, float ym,
                    UnaryOperator<INDArray> mapper,
                    BiFunction<INDArray, INDArray, INDArray> regression) throws Exception {
        logger.atInfo().log("Computing regression {} ...", key);
        try (INDArray reg = Nd4j.zeros(1, 3)) {
            float xm = (n - 1) / 2f;
            Batches.reduce(file, reg,
                    batchSize,
                    (reg1, data, i) -> {
                        // Computes the mapped dy value dy = f(y) - ym
                        try (INDArray dy = mapper.apply(data).sub(ym)) {
                            long m = dy.size(0);
                            // Computes the dx value
                            try (INDArray dx = Nd4j.arange(i, i + m).subi(xm).reshape(m, 1)) {
                                // Accumulates sxx and sxy
                                float sxx = dx.mul(dx).sumNumber().floatValue();
                                float sxy = dy.mul(dx).sumNumber().floatValue();
                                try (INDArray tmp = reg1.getScalar(0)) {
                                    tmp.addi(sxx);
                                }
                                try (INDArray tmp = reg1.getScalar(1)) {
                                    tmp.addi(sxy);
                                }
                            }
                        }
                        return reg1;
                    }
            );
            // Computes the regression parameters
            float mm = reg.getFloat(1) / reg.getFloat(0); // sxy / sxx
            float qq = ym - mm * xm;
            reg.putScalar(0, mm);
            reg.putScalar(1, qq);

            // Computes the RMS error
            logger.atInfo().log("Computing RMS error {} ...", key);
            Batches.reduce(file, reg,
                    batchSize,
                    (reg1, data, i) -> {
                        // Computes mapped y
                        try (INDArray y = mapper.apply(data)) {
                            long m = y.size(0);
                            // Compute x
                            try (INDArray x = Nd4j.arange(i, i + m).reshape(m, 1)) {
                                // Compute the regression
                                try (INDArray y1 = regression.apply(x, reg)) {
                                    // Computes the error
                                    try (INDArray err2 = data.sub(y1)) {
                                        float errSq = err2.muli(err2).sumNumber().floatValue();
                                        try (INDArray tmp = reg1.getScalar(ERROR_INDEX)) {
                                            tmp.addi(errSq);
                                        }
                                    }
                                }
                            }
                        }
                        return reg1;
                    }
            );
            reg.putScalar(ERROR_INDEX, sqrt(reg.getDouble(ERROR_INDEX) / (n - 1)));
            try (CSVWriter out = CSVWriter.createByKey(reportPath, key)) {
                logger.atInfo().log("Creating {} ...", out.file());
                out.clear();
                out.write(reg);
                logger.atInfo().log("Created {}", out.file());
            }
        }
    }

    /**
     * Start to produce the report
     *
     * @throws IOException in case of error
     */
    protected void start() throws Throwable {
        this.kpisPath = new File(args.getString("kpis"));
        this.reportPath = new File(args.getString("reportPath"));
        List<Action> tasks = reports.stream()
                .filter(t -> BinArrayFile.createByKey(kpisPath, t.kpiKey()).file().canRead())
                .<Action>map(t -> () -> this.process(t))
                .toList();
        if (args.getBoolean("parallel")) {
            ParallelProcess.scheduler(tasks).run();
        } else {
            for (Action task : tasks) {
                task.run();
            }
        }
        logger.atInfo().log("Completed.");
    }

    /**
     * Returns the statistic of reader (n, min, max, avg, sigma, avg(log))
     *
     * @param key  the key of kpi
     * @param file the file
     * @throws IOException in case of error
     */
    INDArray stats(String key, BinArrayFile file) throws Exception {
        logger.atInfo().log("Computing stats {} ...", file.file());
        INDArray stats = Nd4j.zeros(1, 6);
        stats.putScalar(MIN_INDEX, Double.MAX_VALUE);
        stats.putScalar(MAX_INDEX, -Double.MAX_VALUE);
        Batches.reduce(file, stats, batchSize,
                (stats1, data, ignored) -> {
                    float minValue = min(stats1.getFloat(MIN_INDEX), data.minNumber().floatValue());
                    float maxValue = max(stats1.getFloat(MAX_INDEX), data.maxNumber().floatValue());
                    float sum = data.sumNumber().floatValue();
                    stats1.putScalar(MIN_INDEX, minValue);
                    stats1.putScalar(MAX_INDEX, maxValue);
                    try (INDArray tmp = stats1.getScalar(AVG_INDEX)) {
                        tmp.addi(sum);
                    }
                    try (INDArray data2 = data.mul(data)) {
                        try (INDArray tmp = stats1.getScalar(SIGMA_INDEX)) {
                            tmp.addi(data2.sumNumber());
                        }
                    }
                    if (minValue > 0) {
                        try (INDArray log = Transforms.log(data)) {
                            try (INDArray tmp = stats1.getScalar(AVGLOG_INDEX)) {
                                tmp.addi(log.sumNumber().floatValue());
                            }
                        }
                    }
                    return stats1;
                });
        // Computes the final averages
        long n;
        try (file) {
            n = file.size();
        }
        float t1 = stats.getFloat(AVG_INDEX);
        float t2 = stats.getFloat(SIGMA_INDEX);
        double sigma = sqrt((t2 - t1 * t1 / n) / (n - 1));
        stats.putScalar(N_INDEX, (float) n);
        try (INDArray tmp = stats.getScalar(AVG_INDEX)) {
            tmp.divi(n);
        }
        stats.putScalar(SIGMA_INDEX, sigma);
        try (INDArray tmp = stats.getScalar(AVGLOG_INDEX)) {
            tmp.divi(n);
        }
        try (CSVWriter out = CSVWriter.createByKey(reportPath, key + ".stats")) {
            logger.atInfo().log("Creating {} ...", out.file());
            out.write(stats);
            logger.atInfo().log("Created {}", out.file());
        }
        return stats;
    }
}
