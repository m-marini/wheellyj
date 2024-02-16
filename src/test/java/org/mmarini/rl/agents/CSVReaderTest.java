/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org.
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
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class CSVReaderTest {

    private static final Logger logger = LoggerFactory.getLogger(CSVReaderTest.class);

    @Test
    void read() throws IOException {
        CSVReader reader = new CSVReader(new File("src/test/resources/dataset2/data.csv"));

        INDArray array = reader.read(1);
        assertEquals(Nd4j.createFromArray(1f, 2f, 3f, 4f).reshape(1, 2, 2), array);

        array = reader.read(10);
        assertEquals(Nd4j.createFromArray(5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f).reshape(2, 2, 2), array);

        array = reader.read(10);
        assertNull(array);
    }

    @Test
    void readFlat() throws IOException {
        CSVReader reader = new CSVReader(new File("src/test/resources/dataset1/data.csv"));
        INDArray array = reader.read(1);
        assertThat(array, matrixCloseTo(new float[][]{
                {1, 2, 3}
        }, 1e-3));

        array = reader.read(10);
        assertThat(array, matrixCloseTo(new float[][]{
                {4, 5, 6},
                {7, 8, 9}
        }, 1e-3));

        array = reader.read(10);
        assertNull(array);
    }

    @Test
    void resetTest() throws IOException {
        CSVReader reader = new CSVReader(new File("src/test/resources/dataset2/data.csv"));

        INDArray array = reader.read(1);
        assertEquals(Nd4j.createFromArray(1f, 2f, 3f, 4f).reshape(1, 2, 2), array);

        array = reader.reset().read(1);
        assertEquals(Nd4j.createFromArray(1f, 2f, 3f, 4f).reshape(1, 2, 2), array);
    }
}