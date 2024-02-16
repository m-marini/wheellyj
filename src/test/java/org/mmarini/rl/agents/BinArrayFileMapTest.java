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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;

class BinArrayFileMapTest {
    private static final Logger logger = LoggerFactory.getLogger(BinArrayFileMapTest.class);

    static {
        Nd4j.zeros(1, 1);
    }

    @BeforeEach
    void setUp() {
        /*
        INDArray data = Nd4j.arange(0, 12).reshape(6, 2).castTo(DataType.FLOAT);
        Stream.of("src/test/resources/f/data.bin",
                        "src/test/resources/f/a/data.bin",
                        "src/test/resources/f/b/data.bin")
                .map(File::new)
                .map(BinArrayFile::new)
                .forEach(file -> {
                    try {
                        file.file().delete();
                        file.write(data);
                        file.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
         */
    }

    @Test
    void testAdd() throws IOException {
        try (BinArrayFileMap map1 = BinArrayFileMap.empty()) {
            BinArrayFileMap map = map1.addRead(new File("src/test/resources"), "f");
            assertThat(map.files(), hasKey("f.a"));
            assertThat(map.files(), hasKey("f.b"));
        }
    }

    @Test
    void testChildren() throws IOException {
        try (BinArrayFileMap map1 = BinArrayFileMap.create(new File("src/test/resources"), "f")) {
            BinArrayFileMap map = map1.children("f");
            assertThat(map.files(), hasKey("a"));
            assertThat(map.files(), hasKey("b"));
            assertThat(map.files(), hasKey("."));
            assertEquals(3, map.files().size());
        }
    }

    @Test
    void testChildrena() throws IOException {
        try (BinArrayFileMap map1 = BinArrayFileMap.create(new File("src/test/resources"), "f")) {
            BinArrayFileMap map = map1.children("f.a");

            assertThat(map.files(), hasKey("."));
            assertEquals(1, map.files().size());
        }
    }

    @Test
    void testClose() throws IOException {
        BinArrayFileMap map = BinArrayFileMap.create(new File("src/test/resources"), "f");
        map.read(1);
        Map<String, INDArray> result;
        map.close();

        result = map.read(1);

        assertThat(result, hasKey("f"));
        assertThat(result, hasKey("f.a"));
        assertThat(result, hasKey("f.b"));
        assertEquals(3, result.size());
        INDArray expected = Nd4j.createFromArray(0f, 1f).reshape(1, 2);
        assertEquals(expected, result.get("f"));
        assertEquals(expected, result.get("f.a"));
        assertEquals(expected, result.get("f.b"));
    }

    @Test
    void testCreate() {
        BinArrayFileMap map = BinArrayFileMap.create(new File("src/test/resources"), "f");
        assertThat(map.files(), hasKey("f.a"));
        assertThat(map.files(), hasKey("f.b"));
        assertThat(map.files(), hasKey("f"));
        assertEquals(new File("src/test/resources/f/a/data.bin"),
                map.files().get("f.a").file());
    }

    @Test
    void testEmpty() {
        assertTrue(BinArrayFileMap.empty().isEmpty());
    }

    @Test
    void testFilter() {
        BinArrayFileMap map = BinArrayFileMap.create(new File("src/test/resources"), "f");
        map = map.filter("f.a");

        assertThat(map.files(), hasKey("f.a"));
        assertEquals(1, map.files().size());
    }

    @Test
    void testGet() {
        BinArrayFileMap map = BinArrayFileMap.create(new File("src/test/resources"), "f");
        BinArrayFile result = map.get("f.a");
        assertNotNull(result);
        assertEquals(new File("src/test/resources/f/a/data.bin"),
                result.file());
    }

    @Test
    void testRead() {
        BinArrayFileMap map = BinArrayFileMap.create(new File("src/test/resources"), "f");
        Map<String, INDArray> result = map.read(10);
        assertThat(result, hasKey("f"));
        assertThat(result, hasKey("f.a"));
        assertThat(result, hasKey("f.b"));
        assertEquals(3, result.size());
        INDArray expected = Nd4j.arange(0f, 12f).reshape(6, 2);
        assertEquals(expected, result.get("f"));
        assertEquals(expected, result.get("f.a"));
        assertEquals(expected, result.get("f.b"));

        assertThat(result, hasKey("f"));
        assertThat(result, hasKey("f.a"));
        assertThat(result, hasKey("f.b"));
        assertEquals(3, result.size());

        Map<String, INDArray> result2 = map.read(10);
        assertNull(result2);
    }

    @Test
    void testReset() throws IOException {
        BinArrayFileMap map = BinArrayFileMap.create(new File("src/test/resources"), "f");
        Map<String, INDArray> result = map.read(1);
        map.reset();
        result = map.read(1);

        assertThat(result, hasKey("f"));
        assertThat(result, hasKey("f.a"));
        assertThat(result, hasKey("f.b"));
        assertEquals(3, result.size());
        INDArray expected = Nd4j.createFromArray(0f, 1f).reshape(1, 2);
        assertEquals(expected, result.get("f"));
        assertEquals(expected, result.get("f.a"));
        assertEquals(expected, result.get("f.b"));
    }
}