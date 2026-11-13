/*
 * Copyright (c) 2024-2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.storage.serde;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;

class FileArrayReaderTest {
    private Path file;
    private FileArrayReader reader;
    private INDArray records;
    private INDArray r0;
    private INDArray r1;
    private INDArray r2;

    @BeforeEach
    void setUp() throws IOException {
        this.records = Nd4j.arange(0, 12).castTo(DataType.FLOAT).reshape(3, 2, 2);
        this.r0 = records.get(NDArrayIndex.interval(0, 1));
        this.r1 = records.get(NDArrayIndex.interval(1, 2));
        this.r2 = records.get(NDArrayIndex.interval(2, 3));
        this.file = Path.of("tmp", "test.bin");
        try (FileArrayWriter fw = new FileArrayWriter(file)) {
            fw.write(records);
        }
        reader = new FileArrayReader(file);
    }

    @AfterEach
    void tearDown() throws IOException {
        reader.close();
        Files.deleteIfExists(file);
    }

    @Test
    void testRead() throws IOException {
        assertEquals(3, reader.size());
        assertEquals(0, reader.position());
        assertEquals(3, reader.available());

        INDArray read = reader.read(1);

        assertThat(read, matrixCloseTo(r0, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(1, reader.position());
        assertEquals(2, reader.available());

        read = reader.read(1);

        assertThat(read, matrixCloseTo(r1, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(2, reader.position());
        assertEquals(1, reader.available());

        read = reader.read(1);

        assertThat(read, matrixCloseTo(r2, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());

        read = reader.read(1);

        assertNull(read);
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());
    }

    @Test
    void testReadBlock() throws IOException {
        assertEquals(3, reader.size());
        assertEquals(0, reader.position());
        assertEquals(3, reader.available());

        INDArray read = reader.read(10);

        assertThat(read, matrixCloseTo(records, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());

        read = reader.read(1);

        assertNull(read);
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());
    }

    @Test
    void testReadReallocate() throws IOException {
        assertEquals(3, reader.size());
        assertEquals(0, reader.position());
        assertEquals(3, reader.available());

        INDArray read = reader.read(1);

        assertThat(read, matrixCloseTo(r0, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(1, reader.position());
        assertEquals(2, reader.available());

        read = reader.read(10);

        assertThat(read, matrixCloseTo(Nd4j.vstack(
                                r1.leverage(),
                                r2.leverage())
                        .reshape(2, 2, 2),
                1e-4));
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());
    }

    @Test
    void testReadReverse() throws IOException {
        assertEquals(3, reader.size());
        assertEquals(0, reader.position());
        assertEquals(3, reader.available());

        reader.seek(2);
        INDArray read = reader.read(1);

        assertThat(read, matrixCloseTo(r2, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());

        reader.seek(1);
        read = reader.read(1);

        assertThat(read, matrixCloseTo(r1, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(2, reader.position());
        assertEquals(1, reader.available());

        reader.seek(0);
        read = reader.read(1);

        assertThat(read, matrixCloseTo(r0, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(1, reader.position());
        assertEquals(2, reader.available());
    }

    @Test
    void testReadShrink() throws IOException {
        assertEquals(3, reader.size());
        assertEquals(0, reader.position());
        assertEquals(3, reader.available());

        INDArray read = reader.read(10);

        assertThat(read, matrixCloseTo(records, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(3, reader.position());
        assertEquals(0, reader.available());

        reader.seek(1);
        read = reader.read(1);

        assertThat(read, matrixCloseTo(r1, 1e-4));
        assertEquals(3, reader.size());
        assertEquals(2, reader.position());
        assertEquals(1, reader.available());
    }
}