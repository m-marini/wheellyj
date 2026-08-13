/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.apps;

import org.mmarini.rl.agents.BinArrayFile;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.UnaryOperator;

import static java.lang.Math.log;

/**
 * Report utility functions
 */
public interface Reports {
    Logger logger = LoggerFactory.getLogger(Reports.class);

    /**
     * Returns the linear report of a file (mean, min, max, exponential mean)
     * and the linear regression initial and final values
     *
     * @param file      the file
     * @param preFunc   the input transformation
     * @param n         the number of bins
     * @param gamma     the gamma decay factor
     * @param batchSize the batch size
     */
    static INDArray[] linReport(BinArrayFile file, UnaryOperator<INDArray> preFunc, int n, double gamma, long batchSize) throws IOException {
        INDArray result = Nd4j.create(n, 5);
        long m = file.size();
        // Fills the bins
        LinearRegression regression = new LinearRegression();
        for (int binIndex = 0; binIndex < n; binIndex++) {
            // Compute the end record index
            long end = (binIndex + 1) * m / n;

            BinStats stats = new BinStats(gamma);

            // Read bin data
            INDArray batchRecords1;
            while ((batchRecords1 = readBin(file, end, batchSize)) != null) {
                // Computes the number of records to read
                try (INDArray batchRecords = batchRecords1) {
                    INDArray records = preFunc.apply(batchRecords);
                    stats.add(records);
                    regression.add(records);
                }
            }

            // Writes the bin values
            result.putScalar(binIndex, 0, (double) file.position());
            result.putScalar(binIndex, 1, stats.mean());
            result.putScalar(binIndex, 2, stats.min);
            result.putScalar(binIndex, 3, stats.max);
            result.putScalar(binIndex, 4, stats.moveExpMean);
        }
        INDArray resultRegression = Nd4j.create(new float[]{
                        (float) regression.initialValue(),
                        (float) regression.finalValue()})
                .reshape(1, 2);
        return new INDArray[]{result, resultRegression};
    }

    /**
     * Returns the logarithmic base 10 report of the best policy file
     * (mean, min, max, exponential mean, ratio mean, ratio min, ratio max, ratio exponential mean),
     * the best policy linear regression values and the entropy linear regression values
     *
     * @param file      the file
     * @param n         the number of bins
     * @param gamma     the gamma decay factor
     * @param batchSize the batch size
     */
    static INDArray[] policyReport(BinArrayFile file, int n, double gamma, long batchSize) throws IOException {
        INDArray result = Nd4j.create(n, 9);
        long m = file.size();
        // Fills the bins
        LinearRegression policyRegression = new LinearRegression();
        LinearRegression entropyRegression = new LinearRegression();
        for (int binIndex = 0; binIndex < n; binIndex++) {
            // Compute the end record index
            long end = (binIndex + 1) * m / n;

            BinStats policyStats = new BinStats(gamma);
            BinStats entropyStats = new BinStats(gamma);

            // Read bin data
            INDArray batchRecords1;
            while ((batchRecords1 = readBin(file, end, batchSize)) != null) {
                // Computes the number of records to read
                try (INDArray batchRecords = batchRecords1) {
                    INDArray max = batchRecords.get(NDArrayIndex.all(), NDArrayIndex.indices(0));
                    try (INDArray log10 = Transforms.log(max, 10, true)) {
                        policyStats.add(log10);
                        policyRegression.add(log10);
                    }
                    // Computes the normalised entropy
                    INDArray h = batchRecords.get(NDArrayIndex.all(), NDArrayIndex.indices(1));
                    entropyStats.add(h);
                    entropyRegression.add(h);
                }
            }

            // Writes the bin values
            result.putScalar(binIndex, 0, (double) file.position());
            result.putScalar(binIndex, 1, policyStats.mean());
            result.putScalar(binIndex, 2, policyStats.min);
            result.putScalar(binIndex, 3, policyStats.max);
            result.putScalar(binIndex, 4, policyStats.moveExpMean);
            result.putScalar(binIndex, 5, entropyStats.mean());
            result.putScalar(binIndex, 6, entropyStats.min);
            result.putScalar(binIndex, 7, entropyStats.max);
            result.putScalar(binIndex, 8, entropyStats.moveExpMean);
        }
        INDArray regressions = Nd4j.create(new float[]{
                (float) policyRegression.initialValue(),
                (float) policyRegression.finalValue(),
                (float) entropyRegression.initialValue(),
                (float) entropyRegression.finalValue()
        }).reshape(1, 4);
        return new INDArray[]{result, regressions};
    }

    /**
     * Returns the logarithmic base 10 report of the best policy file
     * (mean, min, max, exponential mean, ratio mean, ratio min, ratio max, ratio exponential mean),
     * the best policy linear regression values and the entropy linear regression values
     *
     * @param file      the file
     * @param n         the number of bins
     * @param gamma     the gamma decay factor
     * @param batchSize the batch size
     */
    static INDArray[] policyReport1(BinArrayFile file, int n, double gamma, long batchSize) throws IOException {
        INDArray result = Nd4j.create(n, 9);
        long m = file.size();
        // Fills the bins
        LinearRegression policyRegression = new LinearRegression();
        LinearRegression entropyRegression = new LinearRegression();
        for (int binIndex = 0; binIndex < n; binIndex++) {
            // Compute the end record index
            long end = (binIndex + 1) * m / n;

            BinStats policyStats = new BinStats(gamma);
            BinStats entropyStats = new BinStats(gamma);

            // Read bin data
            INDArray batchRecords1;
            while ((batchRecords1 = readBin(file, end, batchSize)) != null) {
                // Computes the number of records to read
                try (INDArray batchRecords = batchRecords1) {
                    try (INDArray log10 = Transforms.log(batchRecords, 10, true)) {
                        try (INDArray max = log10.max(true, 1)) {
                            policyStats.add(max);
                            policyRegression.add(max);
                        }
                    }
                    // Computes the normalised entropy
                    try (INDArray entropy = batchRecords.entropy(1)
                            .reshape(batchRecords.size(0), 1)
                            .divi(log(batchRecords.size(1)))
                    ) {
                        entropyStats.add(entropy);
                        entropyRegression.add(entropy);
                    }
                }
            }

            // Writes the bin values
            result.putScalar(binIndex, 0, (double) file.position());
            result.putScalar(binIndex, 1, policyStats.mean());
            result.putScalar(binIndex, 2, policyStats.min);
            result.putScalar(binIndex, 3, policyStats.max);
            result.putScalar(binIndex, 4, policyStats.moveExpMean);
            result.putScalar(binIndex, 5, entropyStats.mean());
            result.putScalar(binIndex, 6, entropyStats.min);
            result.putScalar(binIndex, 7, entropyStats.max);
            result.putScalar(binIndex, 8, entropyStats.moveExpMean);
        }
        INDArray regressions = Nd4j.create(new float[]{
                (float) policyRegression.initialValue(),
                (float) policyRegression.finalValue(),
                (float) entropyRegression.initialValue(),
                (float) entropyRegression.finalValue()
        }).reshape(1, 4);
        return new INDArray[]{result, regressions};
    }

    /**
     * Returns the data from file limited by bin size and batch size or null if end of bin
     *
     * @param file      the file
     * @param end       the end of data bin file index
     * @param batchSize the batch size
     */
    static INDArray readBin(BinArrayFile file, long end, long batchSize) throws IOException {
        // Read bin data
        if (file.position() < end) {
            // Computes the number of records to read
            long numRecords = Math.min(end - file.position(), batchSize);
            return file.read(numRecords);
        } else {
            return null;
        }
    }

    /**
     * Statistics of data bin
     */
    class BinStats {
        private final double notGamma;
        private final double gamma;
        double min;
        double max;
        double moveExpMean;
        private long numSamples;
        private double sum;

        /**
         * Creates the bin statistics
         *
         * @param gamma the move exponential average factor
         */
        public BinStats(double gamma) {
            min = Double.MAX_VALUE;
            max = -Double.MAX_VALUE;
            moveExpMean = Double.NaN;
            this.gamma = gamma;
            this.notGamma = 1 - gamma;
        }

        /**
         * Add data records to bin
         *
         * @param records the records
         */
        public BinStats add(INDArray records) {
            long n = records.size(0);
            numSamples += n;
            min = Math.min(min, records.minNumber().doubleValue());
            max = Math.max(max, records.maxNumber().doubleValue());
            sum += records.sumNumber().doubleValue();
            if (Double.isNaN(moveExpMean)) {
                // Initialise the exponential mean seed
                moveExpMean = records.getDouble(0, 0);
            }
            for (long i = 0; i < n; i++) {
                double y = records.getDouble(i, 0);
                moveExpMean = moveExpMean * notGamma + y * gamma;
            }
            return this;
        }

        /**
         * Returns the mean
         */
        public double mean() {
            return sum / numSamples;
        }
    }

    /**
     * Computes the linear regression of data
     */
    class LinearRegression {
        double sumxy;
        private long numSamples;
        private double sum;

        /**
         * Add data records to bin
         *
         * @param records the records
         */
        public LinearRegression add(INDArray records) {
            long n = records.size(0);
            long i0 = numSamples;
            numSamples += n;
            sum += records.sumNumber().doubleValue();
            for (long i = 0; i < n; i++) {
                double y = records.getDouble(i, 0);
                sumxy += i0 * y;
                i0++;
            }
            return this;
        }

        /**
         * Returns the linear regression final value
         */
        public double finalValue() {
            return q() + m() * (numSamples - 1);
        }

        /**
         * Returns the linear regression initial value
         */
        public double initialValue() {
            return q();
        }

        /**
         * Returns the linear regression coefficient
         */
        public double m() {
            return sxy() / sxx();
        }

        /**
         * Returns the mean
         */
        double mean() {
            return sum / numSamples;
        }

        /**
         * Returns the linear regression offset
         */
        public double q() {
            return mean() - m() * xm();
        }

        double sxx() {
            double result = 0;
            double xm = xm();
            for (long i = 0; i < numSamples; i++) {
                double x = i - xm;
                result += x * x;
            }
            return result / numSamples;
        }

        /**
         * Returns
         * <p>
         * 1 / N sum_i (xi - xm) (yi - ym)
         * = 1 / N sum_i (xi yi - xi ym - xm yi + xm ym)
         * = 1 / N sum_i (xi yi) - ym / N sum_i xi - xm / N sum_i yi + xm ym
         * = 1 / N sum_i (xi yi) - ym xm - xm ym + xm ym
         * = 1 / N sum_i (xi yi) - ym xm
         */
        double sxy() {
            return sumxy / numSamples - xm() * mean();
        }

        double xm() {
            return (numSamples - 1) / 2.0;
        }
    }
}
