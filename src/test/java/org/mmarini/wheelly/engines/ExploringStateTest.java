/*
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.engines;

import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.envs.CircularSector;
import org.mmarini.wheelly.envs.PolarMap;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExploringStateTest {
    static PolarMap createPolarMap(CircularSector[] sectors) {
        return new PolarMap(sectors);
    }

    @Test
    void findLargerInterval3() {

        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                (i == 1 || i >= 10 && i <= 12)
                        ? new CircularSector(1, 0)
                        : new CircularSector(1, 1)
        ).toArray(CircularSector[]::new);

        int result = ExploringState.findSectorTarget(createPolarMap(sectors), 0);

        assertEquals(11, result);
    }

    @Test
    void findLargerIntervalFirst() {
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                i <= 1
                        ? new CircularSector(1, 0)
                        : new CircularSector(1, 1)
        ).toArray(CircularSector[]::new);

        int result = ExploringState.findSectorTarget(createPolarMap(sectors), 0);

        assertEquals(0, result);
    }

    @Test
    void findLargerIntervalFull() {
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                new CircularSector(1, 0)
        ).toArray(CircularSector[]::new);

        int result = ExploringState.findSectorTarget(createPolarMap(sectors), 0);

        assertEquals(0, result);
    }

    @Test
    void findLargerIntervalLast() {
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                i >= 22
                        ? new CircularSector(1, 0)
                        : new CircularSector(1, 1)
        ).toArray(CircularSector[]::new);

        int result = ExploringState.findSectorTarget(createPolarMap(sectors), 0);

        assertEquals(22, result);
    }

    @Test
    void findLargerIntervalNone() {
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                i == 3 || i == 13
                        ? new CircularSector(1, 1 + i / 10D)
                        : new CircularSector(1, 1)
        ).toArray(CircularSector[]::new);

        int result = ExploringState.findSectorTarget(createPolarMap(sectors), 0);

        assertEquals(0, result);
    }

    @Test
    void findLargerIntervalWrapped() {
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                (i <= 1 || i >= 22)
                        ? new CircularSector(1, 0)
                        : new CircularSector(1, 1)
        ).toArray(CircularSector[]::new);

        int result = ExploringState.findSectorTarget(createPolarMap(sectors), 0);

        assertEquals(0, result);
    }
}