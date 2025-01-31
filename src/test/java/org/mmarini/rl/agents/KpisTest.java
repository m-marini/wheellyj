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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

public class KpisTest {

    @Test
    void maxGmRatioTest() {
        // Given a dataset
        // And a dataset
        INDArray data = Nd4j.createFromArray(
                0.1f, 0.2f, 0.5f, 0.2f,
                0.1f, 0.6f, 0.2f, 0.1f
        ).reshape(2, 4);

        // When reset compute maxMinRatio
        INDArray ratio = Kpis.maxGeometricMeanRatio(data);

        float mean0 = (float) Math.sqrt(Math.sqrt(0.1f * 0.2f * 0.5f * 0.2f));
        float mean1 = (float) Math.sqrt(Math.sqrt(0.1f * 0.6f * 0.2f * 0.1f));
        assertThat(ratio, matrixCloseTo(new long[]{2, 1}, 1e-6,
                0.5f / mean0,
                0.6f / mean1
        ));
    }

    @Test
    void maxMinRatioTest() {
        // Given a dataset
        // And a dataset
        INDArray data = Nd4j.createFromArray(
                0.1f, 0.2f, 0.5f, 0.2f,
                0.1f, 0.6f, 0.2f, 0.1f
        ).reshape(2, 4);

        // When reset compute maxMinRatio
        INDArray ratio = Kpis.maxMinRatio(data);

        assertThat(ratio, matrixCloseTo(new long[]{2, 1}, 1e-6,
                0.5f / 0.1f,
                0.6f / 0.1f
        ));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0,0, 0",
            "-1,1,1, 1",
            "0.5,0.5,-0.5, 0.5",
            "-1,0,1, 0.816496581",
    })
    void rmsTest(float v0, float v1, float v2, float expected) {
        // Given a dataset
        INDArray data = Nd4j.createFromArray(
                v0, v1, v2,
                v0, v1, v2
        ).reshape(2, 3);

        // When compute rms
        INDArray ratio = Kpis.rms(data);

        assertThat(ratio, matrixCloseTo(new long[]{2, 1}, 1e-6,
                expected,
                expected
        ));
    }
}
