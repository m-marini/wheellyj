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
import org.mmarini.wheelly.apis.CircularSector;
import org.mmarini.wheelly.apis.PolarMap;
import org.mmarini.wheelly.apis.RobotControllerApi;

import java.awt.geom.Point2D;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ExploringStateTest {
    private static final double STOP_DISTANCE = 0.4;
    private static final double MM1 = 0.001;

    static ProcessorContext createContext(StateNode state, PolarMap polarMap) {
        StateFlow flow = new StateFlow(List.of(state), List.of(), state, null);
        RobotControllerApi controller = mock();
        ProcessorContext context = new ProcessorContext(controller, flow);
        context.setPolarMap(polarMap);
        context.put("state.stopDistance", STOP_DISTANCE);
        return context;
    }

    static PolarMap createPolarMap(CircularSector[] sectors) {
        return new PolarMap(sectors, new Point2D.Double(), 0);
    }

    /**
     * Given an exploring state
     * And a polar map with all filled sectors below the stop distance
     * And a processor context
     * When find target sector
     * Then should result sector 0
     */
    @Test
    void findFullObstacle() {
        ExploringState state = new ExploringState("state", null, null, null);
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                CircularSector.hindered(1, STOP_DISTANCE - MM1 * i)
        ).toArray(CircularSector[]::new);
        ProcessorContext context = createContext(state, createPolarMap(sectors));

        int result = state.findTargetSector(context);

        assertEquals(0, result);
    }

    /**
     * Given an exploring state
     * And a polar map with empty sectors' interval at 1 and 10-12
     * And a processor context
     * When find target sector
     * Then should result sector 11 (middle of 10-12)
     */
    @Test
    void findLargerInterval3() {
        ExploringState state = new ExploringState("state", null, null, null);
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                (i == 1 || i >= 10 && i <= 12)
                        ? CircularSector.empty(1)
                        : CircularSector.hindered(1, STOP_DISTANCE - MM1)
        ).toArray(CircularSector[]::new);
        ProcessorContext context = createContext(state, createPolarMap(sectors));

        int result = state.findTargetSector(context);


        assertEquals(11, result);
    }

    /**
     * Given an exploring state
     * And a polar map with empty sectors' interval at 0-1
     * And a processor context
     * When find target sector
     * Then should result sector 0
     */
    @Test
    void findLargerIntervalFirst() {
        ExploringState state = new ExploringState("state", null, null, null);
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                i <= 1
                        ? CircularSector.empty(1)
                        : CircularSector.hindered(1, 1)
        ).toArray(CircularSector[]::new);
        ProcessorContext context = createContext(state, createPolarMap(sectors));

        int result = state.findTargetSector(context);

        assertEquals(0, result);
    }

    /**
     * Given an exploring state
     * And a full empty polar map
     * And a processor context
     * When find target sector
     * Then should result sector 0
     */
    @Test
    void findLargerIntervalFull() {
        ExploringState state = new ExploringState("state", null, null, null);
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                CircularSector.empty(1)
        ).toArray(CircularSector[]::new);
        ProcessorContext context = createContext(state, createPolarMap(sectors));

        int result = state.findTargetSector(context);

        assertEquals(0, result);
    }

    /**
     * Given an exploring state
     * And a polar map with all filled sectors and furthest sector at 3
     * And a processor context
     * When find target sector
     * Then should result sector 0
     */
    @Test
    void findLargerIntervalNone() {
        ExploringState state = new ExploringState("state", null, null, null);
        CircularSector[] sectors = IntStream.range(0, 24).mapToObj(i ->
                i == 3
                        ? CircularSector.hindered(1, STOP_DISTANCE + MM1)
                        : CircularSector.hindered(1, STOP_DISTANCE - MM1)
        ).toArray(CircularSector[]::new);
        ProcessorContext context = createContext(state, createPolarMap(sectors));

        int result = state.findTargetSector(context);

        assertEquals(3, result);
    }
}