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
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.UnaryOperator;

/**
 * Report utility functions
 */
public interface Reports {
    Logger logger = LoggerFactory.getLogger(Reports.class);

    /**
     * Returns the linear report of a file (mean, min, max, exponential mean)
     *
     * @param file      the file
     * @param preFunc   the input transformation
     * @param n         the number of bins
     * @param gamma     the gamma decay factor
     * @param batchSize the batch size
     */
    static INDArray linReport(BinArrayFile file, UnaryOperator<INDArray> preFunc, int n, double gamma, long batchSize) throws IOException {
        INDArray result = Nd4j.create(n, 5);
        long m = file.size();
        // Fills the bins
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
                }
            }

            // Writes the bin values
            result.putScalar(binIndex, 0, (double) file.position());
            result.putScalar(binIndex, 1, stats.mean());
            result.putScalar(binIndex, 2, stats.min);
            result.putScalar(binIndex, 3, stats.max);
            result.putScalar(binIndex, 4, stats.moveExpMean);
        }
        return result;
    }

    /**
     * Returns the logarithmic best policy
     *
     * @param data the policy
     */
    static INDArray logBestPolicy(INDArray data) {
        try (INDArray max = data.max(true, 1)) {
            return Transforms.log(max);
        }
    }

    /**
     * Returns the logarithmic base 10 report of best policy file (mean, min, max, exponential mean, ratio mean, ratio min, ratio max, ratio exponential mean)
     *
     * @param file      the file
     * @param n         the number of bins
     * @param gamma     the gamma decay factor
     * @param batchSize the batch size
     */
    static INDArray policyReport(BinArrayFile file, int n, double gamma, long batchSize) throws IOException {
        INDArray result = Nd4j.create(n, 9);
        long m = file.size();
        // Fills the bins
        for (int binIndex = 0; binIndex < n; binIndex++) {
            // Compute the end record index
            long end = (binIndex + 1) * m / n;

            BinStats policyStats = new BinStats(gamma);
            BinStats ratioStats = new BinStats(gamma);

            // Read bin data
            INDArray batchRecords1;
            while ((batchRecords1 = readBin(file, end, batchSize)) != null) {
                // Computes the number of records to read
                try (INDArray batchRecords = batchRecords1) {
                    INDArray log10 = Transforms.log(batchRecords, 10, false);
                    try (INDArray max = log10.max(true, 1)) {
                        policyStats.add(max);
                        try (INDArray mean = log10.mean(true, 1)) {
                            try (INDArray ratio = max.sub(mean)) {
                                ratioStats.add(ratio);
                            }
                        }
                    }
                }
            }

            // Writes the bin values
            result.putScalar(binIndex, 0, (double) file.position());
            result.putScalar(binIndex, 1, policyStats.mean());
            result.putScalar(binIndex, 2, policyStats.min);
            result.putScalar(binIndex, 3, policyStats.max);
            result.putScalar(binIndex, 4, policyStats.moveExpMean);
            result.putScalar(binIndex, 5, ratioStats.mean());
            result.putScalar(binIndex, 6, ratioStats.min);
            result.putScalar(binIndex, 7, ratioStats.max);
            result.putScalar(binIndex, 8, ratioStats.moveExpMean);
        }
        return result;
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
                moveExpMean = moveExpMean * notGamma + records.getDouble(i, 0) * gamma;
            }
            return this;
        }

        public double mean() {
            return sum / numSamples;
        }
    }
}
