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
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileArrayWriterTest {
    private Path path;

    @BeforeEach
    void setUp() throws IOException {
        this.path = Paths.get("tmp", "test.bin");
        Files.deleteIfExists(path);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(path);
    }

    @Test
    void testCreateByKey() throws IOException {
        INDArray records = Nd4j.arange(1, 13)
                .castTo(DataType.FLOAT)
                .reshape(3, 2, 2);
        try (FileArrayWriter file = FileArrayWriter.createByKey(Path.of("tmp"), "a.b.c")) {
            file.write(records);
        }
        Path expected = Path.of("tmp/a/b/c.bin");
        assertTrue(Files.exists(expected));
        assertEquals(483, Files.size(expected));
    }

    @Test
    void testWrite() throws IOException {
        INDArray records = Nd4j.arange(1, 13)
                .castTo(DataType.FLOAT)
                .reshape(3, 2, 2);
        try (FileArrayWriter file = new FileArrayWriter(path)) {
            file.write(records);
        }

        assertEquals(483, Files.size(path));
    }

    @Test
    void testWrite1() throws IOException {
        INDArray records = Nd4j.zeros(1, 2, 2)
                .castTo(DataType.FLOAT);
        try (FileArrayWriter file = new FileArrayWriter(path)) {
            file.write(records);
        }
        assertEquals(161, Files.size(path));
    }

    @Test
    void testWrite10() throws IOException {
        INDArray records = Nd4j.arange(1, 13).castTo(DataType.FLOAT).reshape(3, 2, 2);
        try (FileArrayWriter file = new FileArrayWriter(path)) {
            for (int i = 0; i < 10; i++) {
                file.write(records);
            }
        }

        assertEquals(4830, Files.size(path));
    }

    @Test
    void testWriteView() throws IOException {
        INDArray records = Nd4j.zeros(2, 2, 2)
                .castTo(DataType.FLOAT);
        try (FileArrayWriter file = new FileArrayWriter(path)) {
            file.write(records.get(NDArrayIndex.point(0)).reshape(1, 2, 2));
        }
        assertEquals(161, Files.size(path));
    }
}