/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.agents;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;

/**
 * Kpi functions
 */
public interface Kpis {

    /**
     * Returns the maximum of absolute values
     *
     * @param data the records
     */
    static INDArray absMax(INDArray data) {
        try (INDArray abs = Transforms.abs(data)) {
            return abs.max(true, 1);
        }
    }

    /**
     * Returns the geometric mean
     *
     * @param records the records
     */
    static INDArray geometricMean(INDArray records) {
        try (INDArray dataLog = Transforms.log(records)) {
            try (INDArray meanLog = dataLog.mean(true, 1)) {
                return Transforms.exp(meanLog);
            }
        }
    }

    /**
     * Returns the max/mean ratio
     *
     * @param records the records
     */
    static INDArray maxGeometricMeanRatio(INDArray records) {
        INDArray max = records.max(true, 1);
        try (INDArray gm = geometricMean(records)) {
            return max.divi(gm);
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
     * Returns the root-mean-square of columns
     *
     * @param records the data records
     */
    static INDArray rms(INDArray records) {
        try (INDArray square = Transforms.pow(records, 2, true)) {
            INDArray sat = square.mean(true, 1);
            return Transforms.sqrt(sat, false);
        }
    }
}
