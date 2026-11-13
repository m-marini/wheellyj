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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FileArrayReaderMapTest {
    private static final Logger logger = LoggerFactory.getLogger(FileArrayReaderMapTest.class);

    static {
        logger.atDebug().log("OK");
    }

    @BeforeEach
    void setUp() throws IOException {
        try (FileArrayWriter f = new FileArrayWriter(Path.of("tmp/bin/f.bin"))) {
            f.write(Nd4j.zeros(2, 10).castTo(DataType.FLOAT));
        }
        try (FileArrayWriter f = new FileArrayWriter(Path.of("tmp/bin/f/a.bin"))) {
            f.write(Nd4j.zeros(2, 10).castTo(DataType.FLOAT));
        }
        try (FileArrayWriter f = new FileArrayWriter(Path.of("tmp/bin/f/b.bin"))) {
            f.write(Nd4j.zeros(2, 10).castTo(DataType.FLOAT));
        }
    }

    @Test
    void testCreate() {
        Map<String, FileArrayReader> map = FileArrayReaderMap.create(Path.of("tmp/bin"), "");
        assertThat(map, hasKey("f"));
        assertThat(map, hasKey("f.a"));
        assertThat(map, hasKey("f.b"));
        assertEquals(3, map.size());
    }
}