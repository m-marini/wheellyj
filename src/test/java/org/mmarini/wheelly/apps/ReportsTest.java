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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.rl.agents.BinArrayFile;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.io.File;
import java.io.IOException;

import static java.lang.Math.log10;
import static java.lang.Math.max;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class ReportsTest {

    public static final double GAMMA = 0.25;
    public static final double GAMMA1 = 1 - GAMMA;
    public static final int NUM_BINS = 3;
    public static final int NUM_BATCH = 3;
    public static final int BATCH_SIZE = 2;
    public static final int NUM_RECORDS = NUM_BINS * NUM_BATCH * BATCH_SIZE + 1;
    public static final File BIN_LIN_FILE = new File("tmp/testLinFile");
    public static final File POLICY_FILE = new File("tmp/testPolicyFile");
    public static final int NUM_ACTIONS = 3;
    public static final double EPSILON = 1e-3;

    static double ratio(double... data) {
        double sum = 0;
        double max = -Double.MAX_VALUE;
        for (double datum : data) {
            double sum1 = log10(datum);
            sum += sum1;
            max = max(max, sum1);
        }
        return max - sum / data.length;
    }

    @BeforeEach
    void setUp() throws IOException {
        try (BinArrayFile file = new BinArrayFile(BIN_LIN_FILE)) {
            file.clear();
            file.write(Nd4j.arange(2, NUM_RECORDS + 2).reshape(NUM_RECORDS, 1));
        }
        try (BinArrayFile file = new BinArrayFile(POLICY_FILE)) {
            file.clear();
            file.write(Nd4j.arange(1, (NUM_RECORDS * NUM_ACTIONS) + 1).reshape(NUM_RECORDS, NUM_ACTIONS));
        }
    }

    @Test
    void testLinReg() {
        // Given a data set
        INDArray data = Nd4j.arange(1, 4).reshape(3, 1);

        // When compute max
        Reports.LinearRegression stats = new Reports.LinearRegression().add(data);
        assertThat(stats.mean(), closeTo(2, 1e-3));
        assertThat(stats.xm(), closeTo(1, 1e-3));

        double sxx = ((-1) * (-1) + 1) / 3.0;
        assertThat(stats.sxx(), closeTo(sxx, 1e-3));

        double sumxy = (2 + 2 * 3);
        assertThat(stats.sumxy, closeTo(sumxy, 1e-3));

        double sxy = ((-1) * (1 - 2) + 1) / 3.0;
        assertThat(stats.sxy(), closeTo(sxy, 1e-3));

        assertThat(stats.m(), closeTo(1, 1e-3));
        assertThat(stats.q(), closeTo(1, 1e-3));

        assertThat(stats.initialValue(), closeTo(1, 1e-3));
        assertThat(stats.finalValue(), closeTo(3, 1e-3));
    }

    @Test
    void testLinReg1() {
        // Given a data set
        INDArray x = Nd4j.arange(0, 5).reshape(5, 1);
        INDArray y = Nd4j.create(new float[]{2, 3, 2, 1, 2}).reshape(5, 1);

        // When add data
        Reports.LinearRegression stats = new Reports.LinearRegression().add(y);
        assertThat(stats.mean(), closeTo(y.meanNumber().doubleValue(), 1e-3));
        assertThat(stats.xm(), closeTo(x.meanNumber().doubleValue(), 1e-3));

        double sxx = Transforms.pow(x.sub(x.mean()), 2).meanNumber().doubleValue();
        assertThat(stats.sxx(), closeTo(sxx, 1e-3));

        double sumxy = x.mul(y).sumNumber().doubleValue();
        assertThat(stats.sumxy, closeTo(sumxy, 1e-3));

        double sxy = x.sub(x.mean()).mul(y.sub(y.mean())).meanNumber().doubleValue();
        assertThat(stats.sxy(), closeTo(sxy, 1e-3));

        assertThat(stats.m(), closeTo(-0.2, 1e-3));
        assertThat(stats.q(), closeTo(2.4, 1e-3));

        assertThat(stats.initialValue(), closeTo(2.4, 1e-3));
        assertThat(stats.finalValue(), closeTo(1.6, 1e-3));
    }

    @Test
    void testLinReport() throws IOException {
        // Given a file
        // 2 ... 7
        // 8 ... 13
        // 14 ... 20
        BinArrayFile file = new BinArrayFile(BIN_LIN_FILE);

        // When generate linear report
        INDArray[] result = Reports.linReport(file, x -> x.mul(2), NUM_BINS, 0.25, BATCH_SIZE);

        // Then ...
        assertThat(result[0], matrixCloseTo(new long[]{NUM_BINS, 5},
                1e-3,
                6, 9f, 4, 14, 9.4328f,
                12, 21f, 16, 26, 21.4238f,
                19, 34f, 28, 40, 35.0679f));
    }

    @Test
    void testPolicyReport() throws IOException {
        // Given a file
        // 1, 2, 3
        // 4, 5, 6
        // 7, 8, 9
        // 10, 14, 15
        // 16, 17, 18

        // 19, 20, 21
        // ...
        // 34, 35, 36

        // 37, 38, 39
        // ...
        // 55, 56, 57

        BinArrayFile file = new BinArrayFile(POLICY_FILE);

        // When generate linear report
        INDArray[] result = Reports.policyReport(file, NUM_BINS, 0.25, BATCH_SIZE);

        // Then result should be 3 x 9 array
        assertArrayEquals(new long[]{NUM_BINS, 9}, result[0].shape());
        // And next sample index should be ...
        assertThat(result[0].getDouble(0, 0), closeTo(6, EPSILON));
        // And average max log10 should be ...
        assertThat(result[0].getDouble(0, 1), closeTo(log10(3d * 6 * 9 * 12 * 15 * 18) / 6, EPSILON));
        // And min max log10 should be ...
        assertThat(result[0].getDouble(0, 2), closeTo(log10(3), EPSILON));
        // And max max log10 should be ...
        assertThat(result[0].getDouble(0, 3), closeTo(log10(18), EPSILON));

        // And min ratio should be ...
        assertThat(result[0].getDouble(0, 6), closeTo(ratio(16, 17, 18), EPSILON));
        // And max ratio should be ...
        assertThat(result[0].getDouble(0, 7), closeTo(ratio(1, 2, 3), EPSILON));

        // And next sample index should be ...
        assertThat(result[0].getDouble(1, 0), closeTo(12, EPSILON));
        // And average max log10 should be ...
        assertThat(result[0].getDouble(1, 1), closeTo(log10(21d * 24 * 27 * 30 * 33 * 36) / 6, EPSILON));
        // And min min log10 should be ...
        assertThat(result[0].getDouble(1, 2), closeTo(log10(21), EPSILON));
        // And max max log10 should be ...
        assertThat(result[0].getDouble(1, 3), closeTo(log10(36), EPSILON));

        // And min ratio should be ...
        assertThat(result[0].getDouble(1, 6), closeTo(ratio(34, 35, 36), EPSILON));
        // And max ratio should be ...
        assertThat(result[0].getDouble(1, 7), closeTo(ratio(19, 20, 21), EPSILON));

        assertThat(result[0].getDouble(2, 0), closeTo(19, EPSILON));
        assertThat(result[0].getDouble(2, 1), closeTo(log10(39d * 42 * 45 * 48 * 51 * 54 * 57) / 7, EPSILON));
        assertThat(result[0].getDouble(2, 2), closeTo(log10(39), EPSILON));
        assertThat(result[0].getDouble(2, 3), closeTo(log10(57), EPSILON));

        // And min ratio should be ...
        assertThat(result[0].getDouble(2, 6), closeTo(ratio(55, 56, 57), EPSILON));
        // And max ratio should be ...
        assertThat(result[0].getDouble(2, 7), closeTo(ratio(37, 38, 39), EPSILON));
    }

    @Test
    void testStats() {
        // Given a data set
        INDArray data = Nd4j.arange(1, 4).reshape(3, 1);

        // When compute max
        Reports.BinStats stats = new Reports.BinStats(GAMMA).add(data);
        assertThat(stats.min, closeTo(1, 1e-3));
        assertThat(stats.max, closeTo(3, 1e-3));
        assertThat(stats.mean(), closeTo(2, 1e-3));
        double expected = 3d * GAMMA + 2d * GAMMA * GAMMA1 + 1d * GAMMA * GAMMA1 * GAMMA1 + 1d * GAMMA1 * GAMMA1 * GAMMA1;
        assertThat(stats.moveExpMean, closeTo(expected, 1e-3));
    }
}