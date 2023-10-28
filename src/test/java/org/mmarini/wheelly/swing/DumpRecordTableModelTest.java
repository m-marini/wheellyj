/*
 * Copyright (c) 2023 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mmarini.wheelly.swing.DumpRecordTableModel.formatDuration;

class DumpRecordTableModelTest {

    @Test
    void formatDurationTest() {
        assertEquals("0.000\"", formatDuration(Duration.ofMillis(0)));
        assertEquals("0.999\"", formatDuration(Duration.ofMillis(999)));
        assertEquals("1.000\"", formatDuration(Duration.ofMillis(1000)));
        assertEquals("59.999\"", formatDuration(Duration.ofMillis(59999)));
        assertEquals("1' 00.000\"", formatDuration(Duration.ofMillis(60000)));
        assertEquals("59' 59.999\"", formatDuration(Duration.ofMillis(59 * 60000 + 59999)));
        assertEquals("1h 00' 00.000\"", formatDuration(Duration.ofMillis(60 * 60 * 1000)));
        assertEquals("1h 59' 59.999\"", formatDuration(Duration.ofMillis((60 + 59) * 60 * 1000 + 59999)));
        assertEquals("24h 00' 00.000\"", formatDuration(Duration.ofMillis(24 * 60 * 60 * 1000)));

        assertEquals("-0.999\"", formatDuration(Duration.ofMillis(-999)));
        assertEquals("-1.000\"", formatDuration(Duration.ofMillis(-1000)));
        assertEquals("-59.999\"", formatDuration(Duration.ofMillis(-59999)));
        assertEquals("-1' 00.000\"", formatDuration(Duration.ofMillis(-60000)));
        assertEquals("-59' 59.999\"", formatDuration(Duration.ofMillis(-59 * 60000 - 59999)));
        assertEquals("-1h 00' 00.000\"", formatDuration(Duration.ofMillis(-60 * 60 * 1000)));
        assertEquals("-1h 59' 59.999\"", formatDuration(Duration.ofMillis((-60 - 59) * 60 * 1000 - 59999)));
        assertEquals("-24h 00' 00.000\"", formatDuration(Duration.ofMillis(-24 * 60 * 60 * 1000)));
    }
}