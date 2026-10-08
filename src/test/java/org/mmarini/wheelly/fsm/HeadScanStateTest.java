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
import static org.mmarini.Matchers.angleCloseTo;
import static org.mmarini.wheelly.apis.HeadStatus.HeadStatusId.FIX_DIRECTION;
import static org.mmarini.wheelly.fsm.HaltLookStraightStateTest.*;

class HeadScanStateTest {

    public static final Complex[] SCAN_DEG_10 = IntStream.range(0, 13)
            .mapToObj(i -> Complex.fromDeg(i * 10 - 60))
            .toArray(Complex[]::new);
    public static final int ANGLE_INTERVAL_DEG = 45;
    WorldModelBuilder builder;
    HeadScanState state;
    List<EnvFSMContext> onCompletionContexts;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.onCompletionContexts = new ArrayList<>();
        this.state = new HeadScanState(COMMITMENT_TIME, SCAN_INTERVAL, ANGLE_INTERVAL_DEG)
                .onCompletion(ctx -> {
                    onCompletionContexts.add(ctx);
                    return HeadStatus.lookStraight();
                });
    }

    @Test
    void testComputeHeadDeg() {
        Complex[] heads = HeadScanState.computeHeadDeg(132, 10);
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
                // 2 - at commit time
                .add(builder.addTime(COMMITMENT_TIME))
                // 3 - at valid lidar signal
                .add(builder.headAngle(SCAN_HEAD_DEG[0].toIntDeg())
                        .addTime(1)
                        .updateLidarTime())
                // 4 - scan interval without lidar message
                .add(builder.addTime(SCAN_INTERVAL))
                // 5 - head OK and no lidar signal
                .add(builder.headAngle(SCAN_HEAD_DEG[2].toIntDeg())
                        .addTime(1))
                // 6 - head OK and lidar signal
                .add(builder.addTime(SCAN_INTERVAL)
                        .updateLidarTime())
                // 7 - after completion
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
        assertEquals(SCAN_HEAD_DEG[0], cmd.direction());
        // And no committed
        assertFalse(state.expired(ctx));
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 2 - at commit time
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 1st direction
        assertEquals(SCAN_HEAD_DEG[0], cmd.direction());
        // And committed
        assertTrue(state.expired(ctx));
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // when 3 - at valid lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 2nd direction
        assertEquals(SCAN_HEAD_DEG[1], cmd.direction());
        // And committed
        assertTrue(state.expired(ctx));
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        // When 4 - scan interval without lidar message
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(SCAN_HEAD_DEG[2], cmd.direction());
        // And committed
        assertTrue(state.expired(ctx));
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 5 - head ok and no lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(SCAN_HEAD_DEG[2], cmd.direction());
        // And committed
        assertTrue(state.expired(ctx));
        // And no completed
        assertFalse(state.completed());
        // And no completion triggered
        assertThat(onCompletionContexts, empty());

        //--------
        // When 6 - head ok and lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(FIX_DIRECTION, cmd.status());
        assertThat(cmd.direction(), angleCloseTo(0));
        // And committed
        assertTrue(state.expired(ctx));
        // And no completed
        assertTrue(state.completed());
        // And completion triggered
        assertThat(onCompletionContexts, contains(ctx));

        //--------
        // When 7 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at 3rd direction
        assertEquals(FIX_DIRECTION, cmd.status());
        assertThat(cmd.direction(), angleCloseTo(0));
        // And committed
        assertTrue(state.expired(ctx));
        // And no completed
        assertTrue(state.completed());
        // And completion triggered
        assertThat(onCompletionContexts, hasItem(ctx));
    }
}