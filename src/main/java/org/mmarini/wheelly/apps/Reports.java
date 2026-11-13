/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

import org.mmarini.wheelly.storage.serde.ArrayReader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.UnaryOperator;

import static java.lang.Math.min;

/**
 * Utility interface providing statistical aggregation and linear regression functions
 * for generating reinforcement learning reports from serialised array datasets.
 */
public interface Reports {
    Logger logger = LoggerFactory.getLogger(Reports.class);

    /**
     * Computes a binned linear statistical summary and an overall linear regression trend
     * for a serialised dataset.
     * <p>
     * The records read from {@code file} are transformed via {@code preFunc} and partitioned
     * into at most {@code n} contiguous bins. The returned array contains two {@link INDArray} matrices:
     * </p>
     * <ol>
     *   <li>A matrix of shape {@code [n, 5]} containing, for each bin:
     *     <ul>
     *       <li>Column 0: ending record index (file position)</li>
     *       <li>Column 1: arithmetic mean</li>
     *       <li>Column 2: minimum value</li>
     *       <li>Column 3: maximum value</li>
     *       <li>Column 4: exponential moving average (EMA)</li>
     *     </ul>
     *   </li>
     *   <li>A single-row {@code FLOAT} matrix of shape {@code} defining the endpoints of the
     *       ordinary least squares linear regression line fitted across all transformed records:
     *     <ul>
     *       <li>Column 0 ({@code (0, 0)}): initial fitted value at index {@code x = 0} (y-intercept {@code q})</li>
     *       <li>Column 1 ({@code (0, 1)}): final fitted value at index {@code x = M - 1} ({@code q + m * (M - 1)}),
     *           where {@code M} is the total number of records read</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param file      the {@link ArrayReader} providing access to the source dataset
     * @param preFunc   an operator applied to each batch of records prior to statistical aggregation
     * @param n         the target number of histogram bins (capped at the total number of records)
     * @param gamma     the smoothing factor applied to the exponential moving average within each bin
     * @param batchSize the maximum number of records to read per batch
     * @return a two-element {@link INDArray} array containing the {@code [n, 5]} binned statistics matrix
     * at index {@code 0} and the {@code} regression endpoints matrix at index {@code 1}
     * @throws IOException if an I/O error occurs while reading from {@code file}
     */
    static INDArray[] linReport(ArrayReader file, UnaryOperator<INDArray> preFunc, int n, double gamma, long batchSize) throws IOException {
        long m = file.size();
        n = Math.toIntExact(min(m, n));
        INDArray result = Nd4j.create(n, 5);
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
                INDArray records = preFunc.apply(batchRecords1);
                stats.add(records);
                regression.add(records);
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
     * Computes binned policy distribution statistics and linear regression trends for both
     * the base-10 logarithm of the maximum action probability and the normalised policy entropy.
     * <p>
     * The returned array contains two {@link INDArray} matrices:
     * </p>
     * <ol>
     *   <li>A matrix of shape {@code [n, 10]} containing, for each bin:
     *     <ul>
     *       <li>Column 0: ending record index (file position)</li>
     *       <li>Column 1: mean of {@code log10(max policy)}</li>
     *       <li>Column 2: minimum of {@code log10(max policy)}</li>
     *       <li>Column 3: maximum of {@code log10(max policy)}</li>
     *       <li>Column 4: exponential moving average (EMA) of {@code log10(max policy)}</li>
     *       <li>Column 5: mean of normalised entropy</li>
     *       <li>Column 6: minimum of normalised entropy</li>
     *       <li>Column 7: maximum of normalised entropy</li>
     *       <li>Column 8: exponential moving average (EMA) of normalised entropy</li>
     *       <li>Column 9: number of available actions</li>
     *     </ul>
     *   </li>
     *   <li>A matrix of shape {@code} containing the initial and final linear regression
     *       values for the maximum policy metric followed by those for the entropy metric.</li>
     * </ol>
     *
     * @param file      the {@link ArrayReader} providing access to the policy probability records
     * @param n         the target number of histogram bins (capped at the total number of records)
     * @param gamma     the smoothing factor applied to the exponential moving average within each bin
     * @param batchSize the maximum number of records to read per batch
     * @return a two-element {@link INDArray} array containing the binned policy statistics and regression endpoints
     * @throws IOException if an I/O error occurs while reading from {@code file}
     */
    static INDArray[] policyReport(ArrayReader file, int n, double gamma, long batchSize) throws IOException {
        long m = file.size();
        n = Math.toIntExact(min(m, n));
        INDArray result = Nd4j.create(n, 10);
        // Fills the bins
        LinearRegression policyRegression = new LinearRegression();
        LinearRegression entropyRegression = new LinearRegression();
        for (int binIndex = 0; binIndex < n; binIndex++) {
            // Compute the end record index
            long end = (binIndex + 1) * m / n;

            BinStats policyStats = new BinStats(gamma);
            BinStats entropyStats = new BinStats(gamma);

            // Read bin data
            int numActions = 0;
            INDArray batchRecords;
            while ((batchRecords = readBin(file, end, batchSize)) != null) {
                // Computes the number of records to read
                INDArray max = batchRecords.get(NDArrayIndex.all(), NDArrayIndex.point(0));
                try (INDArray log10 = Transforms.log(max, 10, true)) {
                    policyStats.add(log10);
                    policyRegression.add(log10);
                }
                INDArray h = batchRecords.get(NDArrayIndex.all(), NDArrayIndex.point(1));
                entropyStats.add(h);
                entropyRegression.add(h);
                numActions = batchRecords.getInt(0, 2);
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
            result.putScalar(binIndex, 9, numActions);
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
     * Reads the next batch of records within the current bin boundary from the given reader.
     *
     * @param file      the {@link ArrayReader} to read records from
     * @param end       the exclusive upper record index marking the end of the current bin
     * @param batchSize the maximum number of records to read in a single batch
     * @return an {@link INDArray} containing the read records, or {@code null} if the reader
     * has already reached or passed {@code end}
     * @throws IOException if an I/O error occurs while reading from {@code file}
     */
    static INDArray readBin(ArrayReader file, long end, long batchSize) throws IOException {
        // Read bin data
        if (file.position() < end) {
            // Computes the number of records to read
            long numRecords = min(end - file.position(), batchSize);
            return file.read(numRecords);
        } else {
            return null;
        }
    }

    /**
     * Accumulator that tracks descriptive statistics (minimum, maximum, arithmetic mean,
     * and exponential moving average) across batches of records within a single histogram bin.
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
         * Initialises a new {@code BinStats} accumulator with the specified smoothing factor.
         *
         * @param gamma the exponential moving average weighting factor in the range {@code}
         */
        public BinStats(double gamma) {
            min = Double.MAX_VALUE;
            max = -Double.MAX_VALUE;
            moveExpMean = Double.NaN;
            this.gamma = gamma;
            this.notGamma = 1 - gamma;
        }

        /**
         * Incorporates a batch of records into the running bin statistics.
         * <p>
         * Updates the minimum, maximum, cumulative sum, sample count, and sequential
         * exponential moving average using the first element of the initial batch as the seed.
         * </p>
         *
         * @param records the {@link INDArray} column vector of sample values to accumulate
         * @return this {@code BinStats} instance for method chaining
         */
        public BinStats add(INDArray records) {
            long n = records.size(0);
            numSamples += n;
            min = min(min, records.minNumber().doubleValue());
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
         * Returns the arithmetic mean of all samples accumulated in this bin.
         *
         * @return the arithmetic mean, or {@link Double#NaN} if no samples have been added
         */
        public double mean() {
            return sum / numSamples;
        }
    }

    /**
     * Online accumulator that computes an ordinary least squares (OLS) linear regression
     * ({@code y = m * x + q}) over a sequential series of sample values indexed by {@code x = 0, 1, ..., N - 1}.
     */
    class LinearRegression {
        double sumxy;
        private long numSamples;
        private double sum;

        /**
         * Incorporates a batch of sequential sample records into the linear regression model.
         *
         * @param records the {@link INDArray} column vector of sample values to accumulate
         * @return this {@code LinearRegression} instance for method chaining
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
         * Returns the fitted regression value evaluated at the final sample index ({@code x = N - 1}).
         *
         * @return the final fitted value {@code q + m * (N - 1)}, or {@link Double#NaN} if fewer than two samples were added
         */
        public double finalValue() {
            return q() + m() * (numSamples - 1);
        }

        /**
         * Returns the fitted regression value evaluated at the initial sample index ({@code x = 0}).
         *
         * @return the initial fitted value equal to the intercept {@code q}, or {@link Double#NaN} if fewer than two samples were added
         */
        public double initialValue() {
            return q();
        }

        /**
         * Returns the slope coefficient ({@code m}) of the fitted regression line.
         *
         * @return the slope {@code sxy() / sxx()}, or {@link Double#NaN} if fewer than two samples were added
         */
        public double m() {
            return sxy() / sxx();
        }

        /**
         * Returns the arithmetic mean of all accumulated sample values ({@code y_m}).
         *
         * @return the sample mean {@code sum / N}, or {@link Double#NaN} if no samples have been added
         */
        double mean() {
            return sum / numSamples;
        }

        /**
         * Returns the y-intercept ({@code q}) of the fitted regression line.
         *
         * @return the intercept {@code mean() - m() * xm()}, or {@link Double#NaN} if fewer than two samples were added
         */
        public double q() {
            return mean() - m() * xm();
        }

        /**
         * Computes the population variance of the zero-based sample indices ({@code x = 0, ..., N - 1}).
         *
         * @return the variance {@code (1 / N) * sum((x_i - x_m)^2)}
         */
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
         * Computes the sample covariance between the zero-based indices and the accumulated values.
         * <p>
         * Derived from:
         * </p>
         * <pre>
         * (1 / N) * sum_i (x_i - x_m) * (y_i - y_m)
         *   = (1 / N) * sum_i (x_i * y_i - x_i * y_m - x_m * y_i + x_m * y_m)
         *   = (1 / N) * sum_i (x_i * y_i) - y_m * x_m - x_m * y_m + x_m * y_m
         *   = (1 / N) * sum_i (x_i * y_i) - x_m * y_m
         * </pre>
         *
         * @return the covariance {@code sumxy / N - xm() * mean()}
         */
        double sxy() {
            return sumxy / numSamples - xm() * mean();
        }

        /**
         * Returns the arithmetic mean of the zero-based sample indices ({@code x = 0, ..., N - 1}).
         *
         * @return the mean index {@code (N - 1) / 2.0}
         */
        double xm() {
            return (numSamples - 1) / 2.0;
        }
    }
}
