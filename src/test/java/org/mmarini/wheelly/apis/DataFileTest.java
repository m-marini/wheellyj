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

package org.mmarini.wheelly.apis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataFileTest {
    public static final File FILE = new File("tmp/dump.bin");

    private DataFileWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        FILE.delete();
        this.writer = DataFileWriter.fromFile(FILE);
    }

    @AfterEach
    void tearDown() throws Exception {
        writer.close();
        FILE.delete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testBoolean(boolean value) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            boolean read = reader.readBoolean();
            assertEquals(1, reader.size());
            assertEquals(value, read);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {
            0d,
            -0d,
            1d,
            -1d,
            Double.MIN_VALUE,
            -Double.MIN_VALUE,
            Double.MAX_VALUE,
            -Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NaN
    })
    void testDouble(double value) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            assertEquals(Double.BYTES, reader.size());
            double read = reader.readDouble();
            assertEquals(value, read);
        }
    }


    @ParameterizedTest
    @ValueSource(floats = {
            0f,
            -0f,
            1f,
            -1f,
            Float.MIN_VALUE,
            -Float.MIN_VALUE,
            Float.MAX_VALUE,
            -Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NaN
    })
    void testFloat(float value) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            assertEquals(Float.BYTES, reader.size());
            float read = reader.readFloat();
            assertEquals(value, read);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,1",
            "1,1",
            "63,1",
            "64,2",
            "8191,2",
            "8192,3",
            "134217727,4",
            "134217728,5",
            "2147483647,5",
            "-1,1",
            "-2,1",
            "-64,1",
            "-65,2",
            "-8192,2",
            "-8193,3",
            "-134217728,4",
            "-134217729,5",
            "-2147483648,5",
    })
    void testInt(int value, long size) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            int read = reader.readInt();
            assertEquals(size, reader.size());
            assertEquals(value, read);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,1",
            "1,1",
            "63,1",
            "64,2",
            "8191,2",
            "8192,3",
            "4611686018427387903,9",
            "4611686018427387904,10",
            "9223372036854775807,10",
            "-1,1",
            "-2,1",
            "-64,1",
            "-65,2",
            "-8192,2",
            "-8193,3",
            "-4611686018427387904,9",
            "-4611686018427387905,10",
            "-9223372036854775808,10",
    })
    void testLong(long value, long size) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            assertEquals(size, reader.size());
            long read = reader.readLong();
            assertEquals(value, read);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0,1",
            "1,1",
            "63,1",
            "64,2",
            "32767,3",
            "-1,1",
            "-2,1",
            "-64,1",
            "-65,2",
            "-8192,2",
            "-32768,3",
    })
    void testShort(short value, long size) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            short read = reader.readShort();
            assertEquals(size, reader.size());
            assertEquals(value, read);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "A,2",
            "AA,3",
    })
    void testString(String value, long size) throws IOException {
        writer.write(value);
        try (DataFileReader reader = DataFileReader.fromFile(FILE)) {
            String read = reader.readString();
            assertEquals(size, reader.size());
            assertEquals(value, read);
        }
    }
}