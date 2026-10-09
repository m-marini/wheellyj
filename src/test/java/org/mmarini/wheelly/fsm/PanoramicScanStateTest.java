/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.fsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.HeadStatus;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.SCAN_HEAD_DEG;

class PanoramicScanStateTest {

    public static final Complex[] SCAN_DEG_10 = IntStream.range(0, 13)
            .mapToObj(i -> Complex.fromDeg(i * 10 - 60))
            .toArray(Complex[]::new);
    public static final int ANGLE_INTERVAL_DEG = 45;
    WorldModelBuilder builder;
    PanoramicScanState state;
    List<EnvFSMContext> onCompletionContexts;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.state = new PanoramicScanState(ANGLE_INTERVAL_DEG)
                .onCompletion((ctx, def) -> {
                    onCompletionContexts.add(ctx);
                    return def;
                });
    }

    @Test
    void testComputeHeadDeg() {
        Complex[] heads = PanoramicScanState.computeHeadDeg(132, 10);
        assertArrayEquals(SCAN_DEG_10, heads);
    }

    @Test
    void testScan() {
        // Given ...
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st
                .add(builder)
                // 2 - at valid lidar signal -45 DEG
                .add(builder.headAngle(SCAN_HEAD_DEG[0].toIntDeg())
                        .addTime(1)
                        .updateLidarTime())
                // 3 - scan interval without lidar message
                .add(builder.addTime(1))
                // 4 - head 0 DEG and no lidar message
                .add(builder.headAngle(SCAN_HEAD_DEG[1].toIntDeg())
                        .addTime(1))
                // 5 - head 0 DEG and lidar message
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 6 - head 0 DEG and lidar message
                .add(builder.addTime(1)
                        .updateLidarTime())
                // 7 - head 45 DEG and lidar message
                .add(builder.addTime(1)
                        .headAngle(SCAN_HEAD_DEG[2].toIntDeg())
                        .updateLidarTime())
                // 8 - after completion
                .add()
                .build()
                .iterator();

        // When init
        MockFSMContext ctx = iter.next();
        state.init(ctx);

        //--------
        // When 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan at 1st direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[0]), cmd);
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // when 2 - at valid lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 2nd direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[1]), cmd);
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        // When 3 - scan interval without lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[1]), cmd);
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 4 - head ok and no lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[1]), cmd);
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 5 - head OK and lidar signal 0 DEG
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[2]), cmd);
        // And no completed
        assertFalse(state.completed());
        // And completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 6 - head 0 DEG and lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[2]), cmd);
        // And no completed
        assertFalse(state.completed());
        // And completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 7 - head 45 DEG and lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(HeadStatus.scan(SCAN_HEAD_DEG[2]), cmd);
        // And no completed
        assertTrue(state.completed());
        // And completion triggered
        assertThat(onCompletionContexts, contains(ctx));

        //--------
        // When 8 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(HeadStatus.lookStraight(), cmd);
        // And committed
        // And no completed
        assertTrue(state.completed());
        // And completion triggered
        assertThat(onCompletionContexts, hasSize(2));
        assertThat(onCompletionContexts, hasItem(ctx));
    }
}