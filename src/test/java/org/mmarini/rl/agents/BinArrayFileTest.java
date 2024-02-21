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
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BinArrayFileTest {
    @Test
    void testReadWrite() throws IOException {
        INDArray records = Nd4j.arange(1, 13).castTo(DataType.FLOAT).reshape(3, 2, 2);
        File file1 = new File("tmp/test.bin");
        file1.delete();
        BinArrayFile file = new BinArrayFile(file1);
        file.write(records);

        assertEquals(3, file.size());
        assertEquals(3, file.position());
        assertEquals(0, file.available());

        file.seek(0);
        assertEquals(3, file.size());
        assertEquals(0, file.position());
        assertEquals(3, file.available());

        INDArray read = file.read(10);
        assertEquals(records, read);
        assertEquals(3, file.size());
        assertEquals(3, file.position());
        assertEquals(0, file.available());

        read = file.read(10);
        assertNull(read);
    }

    @Test
    void testWriteCloseRead() throws IOException {
        INDArray records = Nd4j.arange(1, 13).castTo(DataType.FLOAT).reshape(3, 2, 2);
        File file1 = new File("tmp/test.bin");
        file1.delete();
        BinArrayFile file = new BinArrayFile(file1);
        try {
            file.write(records);

            assertEquals(3, file.size());
            assertEquals(3, file.position());
            assertEquals(0, file.available());
        } finally {
            file.close();
        }

        file = new BinArrayFile(file1);
        try {
            assertEquals(3, file.size());
            assertEquals(0, file.position());
            assertEquals(3, file.available());

            INDArray read = file.read(10);
            assertEquals(records, read);
            assertEquals(3, file.size());
            assertEquals(3, file.position());
            assertEquals(0, file.available());

            file.seek(2);
            assertEquals(3, file.size());
            assertEquals(2, file.position());
            assertEquals(1, file.available());

            read = file.read(10);
            assertEquals(Nd4j.arange(9, 13).castTo(DataType.FLOAT).reshape(1, 2, 2), read);
            assertEquals(3, file.size());
            assertEquals(3, file.position());
            assertEquals(0, file.available());
        } finally {
            file.close();
        }
    }

    @Test
    void testWriteReadRewrite() throws IOException {
        INDArray records = Nd4j.arange(1, 13).castTo(DataType.FLOAT).reshape(3, 2, 2);
        File file1 = new File("tmp/test.bin");
        file1.delete();
        BinArrayFile file = new BinArrayFile(file1);
        try {
            file.write(records);
            assertEquals(3, file.size());
            assertEquals(3, file.position());
            assertEquals(0, file.available());

            file.seek(0);
            assertEquals(3, file.size());
            assertEquals(0, file.position());
            assertEquals(3, file.available());

            INDArray read = file.read(1);
            assertEquals(3, file.size());
            assertEquals(1, file.position());
            assertEquals(2, file.available());
            assertEquals(Nd4j.arange(1, 5).castTo(DataType.FLOAT).reshape(1, 2, 2), read);

            file.write(read);
            assertEquals(3, file.size());
            assertEquals(2, file.position());
            assertEquals(1, file.available());

            file.seek(0);
            assertEquals(3, file.size());
            assertEquals(0, file.position());
            assertEquals(3, file.available());

            read = file.read(10);
            assertEquals(3, file.size());
            assertEquals(3, file.position());
            assertEquals(0, file.available());
            assertEquals(Nd4j.createFromArray(1f, 2f, 3f, 4f, 1f, 2f, 3f, 4f, 9f, 10f, 11f, 12f)
                            .reshape(3, 2, 2),
                    read);
        } finally {
            file.close();
        }
    }
}