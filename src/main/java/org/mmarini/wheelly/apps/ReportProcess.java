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

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

/**
 * Defines the report process
 *
 * @param kpiKey    the kpi key
 * @param reportKey the report key
 * @param reducer   the reducer function
 */
public record ReportProcess(String kpiKey, String reportKey, UnaryOperator<INDArray> reducer) {

    /**
     * Returns the maxAbs report
     *
     * @param kpiKey the kpi key
     */
    public static ReportProcess maxAbs(String kpiKey) {
        return new ReportProcess(kpiKey, kpiKey + ".maxAbs", ReportProcess::maxAbs);
    }

    /**
     * Returns the max of absolute value of columns
     *
     * @param records the records
     */
    static INDArray maxAbs(INDArray records) {
        try (INDArray abs = Transforms.abs(records)) {
            return abs.max(true, 1);
        }
    }


    /**
     * Returns the max/min ratio
     *
     * @param records the records
     */
    private static INDArray maxMinRatio(INDArray records) {
        INDArray max = records.max(true, 1);
        try (INDArray min = records.min(true, 1)) {
            return max.divi(min);
        }
    }

    /**
     * Returns the max/min ratio report
     *
     * @param kpiKey the kpi key
     */
    public static ReportProcess maxMinRatio(String kpiKey) {
        return new ReportProcess(kpiKey, kpiKey + ".maxMinRatio", ReportProcess::maxMinRatio);
    }

    /**
     * Returns the scalar report
     *
     * @param kpiKey the kpi and report key
     */
    public static ReportProcess scalar(String kpiKey) {
        return new ReportProcess(kpiKey, kpiKey, null);
    }

    /**
     * Creates the report process
     *
     * @param kpiKey    the kpi key
     * @param reportKey the report key
     * @param reducer   the reducer function
     */
    public ReportProcess(String kpiKey, String reportKey, UnaryOperator<INDArray> reducer) {
        this.kpiKey = requireNonNull(kpiKey);
        this.reportKey = requireNonNull(reportKey);
        this.reducer = reducer;
    }
}
