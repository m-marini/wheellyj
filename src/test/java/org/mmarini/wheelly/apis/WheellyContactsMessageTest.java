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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.*;

class WheellyContactsMessageTest {

    @Test
    void testParse() {
        WheellyContactsMessage result = WheellyContactsMessage.parse(1234, "4321,1,0,1,0");
        assertNotNull(result);
        assertEquals(1234, result.simulationTime());
        assertTrue(result.frontSensors());
        assertFalse(result.rearSensors());
        assertTrue(result.canMoveForward());
        assertFalse(result.canMoveBackward());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4321 1 0 1",
            "a 1 0 1 0",
            "4321 a 0 1 0",
            "4321 1 a 1 0",
            "4321 1 0 a 0",
            "4321 1 0 1 a",
            "4321 11 0 1 0",
            "4321 1 01 1 0",
            "4321 1 0 11 0",
            "4321 1 0 1 01",
    })
    void testParseError(String arg) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> WheellyContactsMessage.parse(1234, arg));
        assertThat(ex.getMessage(), matchesPattern("Wrong contacts message .*"));
    }
}