/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.functions.BiFunction;
import io.reactivex.rxjava3.functions.Function;
import org.mmarini.rl.agents.ArrayReader;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.CSVWriter;
import org.mmarini.rl.agents.KeyFileMap;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.lang.Math.min;
import static java.lang.Math.sqrt;
import static java.util.Objects.requireNonNull;

/**
 * Defines the report process
 *
 * @param reportKey  the report key
 * @param reader     the reader
 * @param aggregator the chart aggregator
 * @param reportPath the report path
 * @param batchSize  the batch size
 */
public record ReportProcess(String reportKey,
                            ArrayReader reader,
                            Aggregator aggregator,
                            File reportPath,
                            long batchSize) {
    public static final int ERROR_INDEX = 2;
    public static final int SIGMA_INDEX = 4;
    public static final int DEFAULT_NUM_CHART_POINTS = 200;
    public static final double MIN_DIFFERENCE_RATIO = 1e-2;
    public static final double MIN_DIFFERENCE = 1e-12;
    private static final int DEFAULT_NUM_BINS = 11;
    private static final int N_INDEX = 0;
    private static final int MIN_INDEX = 1;
    private static final int MAX_INDEX = 2;
    private static final int AVG_INDEX = 3;
    private static final int AVGLOG_INDEX = 5;
    private static final long X_INDEX = 0;
    private static final long Y_MEAN_INDEX = 1;
    private static final long Y_MIN_INDEX = 2;
    private static final long Y_MAX_INDEX = 3;
    private static final Logger logger = LoggerFactory.getLogger(ReportProcess.class);

    /**
     * Returns a reducer from MapArray to ArrayReader
     *
     * @param files   the files
     * @param reducer the reducer
     */
    static ArrayReader createKeyFileReducer(Map<String, BinArrayFile> files, Function<Map<String, INDArray>, INDArray> reducer) {
        return new ArrayReader() {
            @Override
            public void close() throws IOException {
                try {
                    KeyFileMap.close(files);
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public File file() {
                return files.values().iterator().next().file();
            }

            @Override
            public long position() throws IOException {
                return files.values().iterator().next().position();
            }

            @Override
            public INDArray read(long numRecords) throws IOException {
                Map<String, INDArray> data = KeyFileMap.read(files, numRecords);
                try {
                    return data != null ? reducer.apply(data) : null;
                } catch (IOException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void seek(long record) throws IOException {
                KeyFileMap.seek(files, record);

            }

            @Override
            public long size() throws IOException {
                return files.values().iterator().next().size();
            }
        };
    }

    /**
     * Returns the deltaRatio
     *
     * @param data the data the input data (pi, pi0, actions)
     */
    private static INDArray deltaRatio(Map<String, INDArray> data) {
        INDArray pi0 = data.get("pi0");
        INDArray pi = data.get("pi");
        INDArray masks = data.get("actionMasks");
        INDArray prob = pi.mul(masks).sum(true, 1);
        INDArray prob0 = pi0.mul(masks).sum(true, 1);
        try (INDArray ratio = prob.divi(prob0).subi(1)) {
            return Transforms.abs(ratio);
        }
    }

    /**
     * Returns the builder of report of mean of delta ratio od actions
     *
     * @param actionKey the action key
     */
    public static Builder deltaRatioReport(String actionKey) {
        Function<File, Map<String, BinArrayFile>> filesBuilder = path ->
                Map.of(
                        "pi", BinArrayFile.createByKey(path, "trainedLayers." + actionKey + ".values"),
                        "pi0", BinArrayFile.createByKey(path, "trainingLayers." + actionKey + ".values"),
                        "actionMasks", BinArrayFile.createByKey(path, "actionMasks." + actionKey)
                );

        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                try {
                    ArrayReader reader = createKeyFileReducer(filesBuilder.apply(path),
                            ReportProcess::deltaRatio);
                    return new ReportProcess(actionKey + ".delta", reader, rmsAggregator(), reportPath, batchSize);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public boolean canCreate(File path) {
                try {
                    return filesBuilder.apply(path).values().stream()
                            .allMatch(file -> file.file().canRead());
                } catch (Throwable e) {
                    return false;
                }
            }
        };
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
     * Returns the geometric mean
     *
     * @param records the records
     */
    static INDArray gm(INDArray records) {
        try (INDArray dataLog = Transforms.log(records)) {
            try (INDArray meanLog = dataLog.mean(true, 1)) {
                return Transforms.exp(meanLog);
            }
        }
    }

    /**
     * Returns the builder of report of geometric mean of value
     *
     * @param reportKey the report key
     */
    public static Builder gmReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                ArrayReader reader = BinArrayFile.createByKey(path, reportKey).map(ReportProcess::gm);
                return new ReportProcess(reportKey + ".gm", reader, meanAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
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
     * Returns the builder of report of mean of max value
     *
     * @param reportKey the report key
     */
    public static Builder maxGMRatioReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                ArrayReader maxReader = BinArrayFile.createByKey(path, reportKey).map(ReportProcess::maxGmRatio);
                return new ReportProcess(reportKey + ".maxGMRatio", maxReader, meanAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Returns the max/min ratio
     *
     * @param records the records
     */
    static INDArray maxGmRatio(INDArray records) {
        INDArray max = records.max(true, 1);
        try (INDArray dataLog = Transforms.log(records)) {
            try (INDArray meanLog = dataLog.mean(true, 1)) {
                try (INDArray geoMean = Transforms.exp(meanLog)) {
                    return max.divi(geoMean);
                }
            }
        }
    }

    /**
     * Returns the max/min ratio
     *
     * @param records the records
     */
    static INDArray maxMinRatio(INDArray records) {
        INDArray max = records.max(true, 1);
        try (INDArray min = records.min(true, 1)) {
            return max.divi(min);
        }
    }

    /**
     * Returns the builder of report of mean of max value
     *
     * @param reportKey the report key
     */
    public static Builder maxMinRatioReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                ArrayReader maxReader = BinArrayFile.createByKey(path, reportKey).map(ReportProcess::maxMinRatio);
                return new ReportProcess(reportKey + ".maxMinRatio", maxReader, meanAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Returns the builder of report of mean of max value
     *
     * @param reportKey the report key
     */
    public static Builder maxReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                ArrayReader maxReader = BinArrayFile.createByKey(path, reportKey).map(a -> a.max(true, 1));
                return new ReportProcess(reportKey + ".max", maxReader, meanAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Returns the mean aggregator
     */
    static Aggregator meanAggregator() {
        return new Aggregator() {
            private long numSamples;
            private float sum;
            private float min;
            private float max;

            @Override
            public void add(INDArray data) {
                this.numSamples += data.size(0);
                this.sum += data.sumNumber().floatValue();
                min = Math.min(min, data.minNumber().floatValue());
                max = Math.max(max, data.maxNumber().floatValue());
            }

            @Override
            public float max() {
                return max;
            }

            @Override
            public float min() {
                return min;
            }

            @Override
            public long numSamples() {
                return numSamples;
            }

            public void reset() {
                this.sum = 0;
                this.numSamples = 0;
                this.min = Float.MAX_VALUE;
                this.max = -Float.MAX_VALUE;
            }

            @Override
            public float value() {
                return sum / numSamples;
            }
        };
    }

    /**
     * Returns the mean report builder
     *
     * @param reportKey the report key
     */
    public static Builder meanReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                return new ReportProcess(reportKey, BinArrayFile.createByKey(path, reportKey), meanAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Returns the rms aggregator
     */
    static Aggregator rmsAggregator() {
        return new Aggregator() {
            private long numSamples;
            private float sum;
            private float min;
            private float max;

            @Override
            public void add(INDArray data) {
                this.numSamples += data.size(0);
                try (INDArray dataSqr = Transforms.pow(data, 2)) {
                    this.sum += dataSqr.sumNumber().floatValue();
                }
                min = Math.min(min, data.minNumber().floatValue());
                max = Math.max(max, data.maxNumber().floatValue());
            }

            @Override
            public float max() {
                return max;
            }

            @Override
            public float min() {
                return min;
            }

            @Override
            public long numSamples() {
                return numSamples;
            }

            public void reset() {
                this.sum = 0;
                this.numSamples = 0;
                this.min = Float.MAX_VALUE;
                this.max = -Float.MAX_VALUE;
            }

            @Override
            public float value() {
                return (float) Math.sqrt(sum / numSamples);
            }
        };
    }

    /**
     * Returns the rms report builder
     *
     * @param reportKey the report key
     */
    public static Builder rmsReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                ArrayReader reader = BinArrayFile.createByKey(path, reportKey).map(Transforms::abs);
                return new ReportProcess(reportKey,
                        reader, rmsAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Returns the builder of report of rms of sum
     *
     * @param reportKey the report key
     */
    public static Builder sumRmsReport(String reportKey) {
        return new Builder() {

            @Override
            public ReportProcess build(File path, File reportPath, long batchSize) {
                ArrayReader sumReader = BinArrayFile.createByKey(path, reportKey).map(x -> {
                    try (INDArray sum = x.sum(true, 1)) {
                        return Transforms.abs(sum);
                    }
                });
                return new ReportProcess(reportKey + ".sum", sumReader, rmsAggregator(), reportPath, batchSize);
            }

            @Override
            public boolean canCreate(File path) {
                try (BinArrayFile file = BinArrayFile.createByKey(path, reportKey)) {
                    return file.file().canRead();
                } catch (IOException e) {
                    return false;
                }
            }
        };
    }

    /**
     * Creates the report process
     *
     * @param reportKey  the report key
     * @param reader     the reader
     * @param aggregator the chart aggregator
     * @param reportPath the report path
     * @param batchSize  the batch size
     */
    public ReportProcess(String reportKey,
                         ArrayReader reader,
                         Aggregator aggregator,
                         File reportPath, long batchSize) {
        this.reportKey = requireNonNull(reportKey);
        this.aggregator = requireNonNull(aggregator);
        this.reader = requireNonNull(reader);
        this.reportPath = requireNonNull(reportPath);
        this.batchSize = batchSize;
    }

    /**
     * Creates the chart
     *
     * @param output the output chart csv  file
     * @param stats  the statistics of file
     */
    private void chart(CSVWriter output, INDArray stats) throws IOException {
        Batches.Monitor monitor = new Batches.Monitor();
        try (CSVWriter chartFile = output) {
            logger.atInfo().log("Creating chart {} ...", chartFile.file());
            long n = stats.getLong(N_INDEX);
            long numPoints = min(n, DEFAULT_NUM_CHART_POINTS);
            long stride = Math.max(n / (numPoints + 1), 1);
            long len = n - (numPoints - 1) * stride;
            try (INDArray chart = Nd4j.zeros(numPoints, 4)) {
                for (long idx = 0; idx < numPoints; idx++) {
                    reader.seek(idx * stride);
                    aggregator.reset();
                    while (aggregator.numSamples() < len) {
                        // Number of samples
                        long m = min(len, batchSize);
                        logger.atDebug().log("Reading chart {} at {}", reader.file(), reader.position());
                        INDArray data = reader.read(m);
                        if (data == null) {
                            break;
                        }
                        aggregator.add(data);
                        monitor.wakeUp(reader.file(), aggregator.numSamples());
                    }
                    chart.putScalar(idx, X_INDEX, idx * stride + aggregator.numSamples() / 2f);
                    chart.putScalar(idx, Y_MEAN_INDEX, aggregator.value());
                    chart.putScalar(idx, Y_MIN_INDEX, aggregator.min());
                    chart.putScalar(idx, Y_MAX_INDEX, aggregator.max());
                }
                chartFile.write(chart);
            }
            logger.atInfo().log("Created chart {}", chartFile.file());
        }
    }

    /**
     * Computes the histogram
     *
     * @param output the output report csv file
     * @param stats  the statistic of dataset
     * @throws IOException in case of error
     */
    private void hist(CSVWriter output, INDArray stats) throws Exception {
        logger.atInfo().log("Computing histogram {} ...", reportKey);
        long[] counters = new long[DEFAULT_NUM_BINS];
        double min1 = stats.getDouble(MIN_INDEX);
        double max1 = stats.getDouble(MAX_INDEX);
        double minDiff = Math.max((max1 + min1) * MIN_DIFFERENCE_RATIO / 2, MIN_DIFFERENCE);
        double diff = max1 - min1;
        double min = diff < minDiff ? min1 - minDiff : min1;
        double max = diff < minDiff ? max1 + minDiff : max1;
        double dx = (max - min) / DEFAULT_NUM_BINS;
        Batches.reduce(reader, counters, batchSize,
                (counters1, data, ignored) -> {
                    try (INDArray bins = Transforms.floor(data.sub(min).divi(dx), false)) {
                        for (long i = 0; i < bins.size(0); i++) {
                            int binIndex = (int) bins.getLong(i);
                            int bin = min(binIndex, DEFAULT_NUM_BINS - 1);
                            counters1[bin]++;
                        }
                    }
                    return counters1;
                });
        try (INDArray hist = Nd4j.zeros(2, DEFAULT_NUM_BINS)) {
            // Initialize histogram seed
            try (INDArray x = Nd4j.arange(0, DEFAULT_NUM_BINS).addi(0.5).muli(dx).addi(min)) {
                hist.get(NDArrayIndex.point(1), NDArrayIndex.all()).assign(x);
            }
            for (int i = 0; i < counters.length; i++) {
                hist.putScalar(0, i, counters[i]);
            }
            try (CSVWriter histFile = output) {
                logger.atInfo().log("Creating histogram {} ...", histFile.file());
                histFile.clear();
                histFile.write(hist);
                logger.atInfo().log("Created histogram {}", histFile.file());
            }
        }
    }

    /**
     * Processes the report
     * The process consists of reduction of kpi file (for the kpi key) into single value file
     * Computes the stats data and the relative stats csv file
     * Computes the histogram csv file
     * Computes the chart csv file
     * Computes the linear regression csv file
     * Computes the exponential regression csv file if values are all positive
     *
     * @throws Throwable in case of error
     */
    public void process() throws Throwable {
        try (INDArray stats = stats(CSVWriter.createByKey(reportPath, reportKey + ".stats"), batchSize)) {
            // Computes histogram
            hist(CSVWriter.createByKey(reportPath, reportKey + ".histogram"), stats);
            // Computes chart
            chart(CSVWriter.createByKey(reportPath, reportKey + ".chart"), stats);
            // Computes linear regression
            regression(CSVWriter.createByKey(reportPath, reportKey + ".linear"),
                    stats.getLong(N_INDEX),
                    stats.getFloat(AVG_INDEX),
                    UnaryOperator.identity(),
                    ReportProcess::linear);

            // Computes exponential regression
            if (stats.getDouble(MIN_INDEX) > 0) {
                regression(CSVWriter.createByKey(reportPath, reportKey + ".exponential"),
                        stats.getLong(N_INDEX),
                        stats.getFloat(AVG_INDEX),
                        UnaryOperator.identity(),
                        ReportProcess::exponential);
            }
        }
        logger.atInfo().log("Completed report {}", reportKey);
    }

    /**
     * Returns the regression parameters (m, q)
     *
     * @param out        the output csv file
     * @param n          the number of samples
     * @param ym         the average y
     * @param mapper     the data mapper
     * @param regression the regression function
     */
    void regression(CSVWriter out, long n, float ym,
                    UnaryOperator<INDArray> mapper,
                    BiFunction<INDArray, INDArray, INDArray> regression) throws Throwable {
        logger.atInfo().log("Computing regression {} ...", reportKey);
        try (INDArray reg = Nd4j.zeros(1, 3)) {
            float xm = (n - 1) / 2f;
            Batches.reduce(reader, reg,
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
            logger.atInfo().log("Computing RMS error {} ...", reader.file());
            Batches.reduce(reader, reg,
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
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                        return reg1;
                    }
            );
            reg.putScalar(ERROR_INDEX, sqrt(reg.getDouble(ERROR_INDEX) / (n - 1)));
            try (CSVWriter out1 = out) {
                logger.atInfo().log("Creating regression {} ...", out1.file());
                out1.clear();
                out1.write(reg);
                logger.atInfo().log("Created regression {}", out1.file());
            }
        }
    }

    /**
     * Returns the statistic of reader (n, min, max, avg, sigma, avg(log))
     *
     * @param out       the output report path
     * @param batchSize the batch size
     * @throws IOException in case of error
     */
    private INDArray stats(CSVWriter out, long batchSize) throws Exception {
        logger.atInfo().log("Computing stats {} ...", reportKey);
        INDArray stats = Nd4j.zeros(1, 6);
        stats.putScalar(MIN_INDEX, Double.MAX_VALUE);
        stats.putScalar(MAX_INDEX, -Double.MAX_VALUE);
        Batches.reduce(reader, stats, batchSize,
                (stats1, data, ignored) -> {
                    float minValue = min(stats1.getFloat(MIN_INDEX), data.minNumber().floatValue());
                    float maxValue = Math.max(stats1.getFloat(MAX_INDEX), data.maxNumber().floatValue());
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
        long n = reader.size();
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
        try (CSVWriter out1 = out) {
            logger.atInfo().log("Creating stats {} ...", out1.file());
            out1.write(stats);
            logger.atInfo().log("Created stats {}", out1.file());
        }
        return stats;
    }

    /**
     * Builds a report process and check if report can be created
     */
    public interface Builder {
        /**
         * Returns the report process
         *
         * @param path       the input path
         * @param reportPath the report path
         * @param batchSize  the batch size
         */
        ReportProcess build(File path, File reportPath, long batchSize);

        boolean canCreate(File path);
    }

    public interface Aggregator {
        /**
         * Add data to aggregator
         *
         * @param data the data
         */
        void add(INDArray data);

        /**
         * Returns the max value
         */
        float max();

        /**
         * Returns the min value
         */
        float min();

        /**
         * Returns the number of samples
         */
        long numSamples();

        /**
         * Resets the aggregator
         */
        void reset();

        /**
         * Returns the value
         */
        float value();
    }
}
