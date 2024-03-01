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
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyFileMapTest {
    private static final Logger logger = LoggerFactory.getLogger(KeyFileMapTest.class);

    static {
        Nd4j.zeros(1, 1);
        logger.atDebug().log("OK");
    }

    @Test
    void testChildren() {
        Map<String, BinArrayFile> map1 = KeyFileMap.create(new File("src/test/resources"), "f");
        Map<String, BinArrayFile> map = KeyFileMap.children(map1, "f");
        assertThat(map, hasKey("a"));
        assertThat(map, hasKey("b"));
        assertEquals(2, map.size());
    }

    @Test
    void testChildrena() {
        Map<String, BinArrayFile> map1 = KeyFileMap.create(new File("src/test/resources"), "f");
        Map<String, BinArrayFile> map = KeyFileMap.children(map1, "f.a");
        assertTrue(map.isEmpty());
    }

    @Test
    void testCreate() {
        Map<String, BinArrayFile> map = KeyFileMap.create(new File("src/test/resources"), "f");
        assertThat(map, hasKey("f.a"));
        assertThat(map, hasKey("f.b"));
        assertThat(map, hasKey("f"));
        assertEquals(new File("src/test/resources/f/a/data.bin"),
                map.get("f.a").file());
    }
}