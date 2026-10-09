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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mmarini.RandomArgumentsGenerator;
import org.mmarini.wheelly.apis.Complex;
import org.mmarini.wheelly.apis.HeadStatus;
import org.mmarini.wheelly.apis.WorldModelBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ScanStateTest {
    public static final int COMMITMENT_TIME = 1000;
    public static final int SEED = 1234;
    public static final int NUM_RANDOM_TEST_CASES = 30;

    public static Stream<Arguments> dataScan() {
        return RandomArgumentsGenerator.create(SEED)
                .uniform(-65, 65)
                .build(NUM_RANDOM_TEST_CASES);
    }

    WorldModelBuilder builder;
    ScanState state;

    @BeforeEach
    void setUp() {
        this.builder = new WorldModelBuilder();
        this.state = new ScanState();
    }

    @ParameterizedTest
    @CsvSource("0")
    @MethodSource("dataScan")
    void testScan(int scanDeg) {
        // Given a look straight action
        Complex scanDir = Complex.fromDeg(scanDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - valid lidar signal
                .add(builder.headAngle(scanDeg)
                        .addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, scanDir);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan at target direction
        assertEquals(HeadStatus.scan(scanDir), cmd);
        assertFalse(state.completed());

        // When 2 - valid lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at target direction
        assertEquals(HeadStatus.scan(scanDir), cmd);
        assertTrue(state.completed());

        // When 3 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at target direction
        assertEquals(HeadStatus.scan(scanDir), cmd);
        assertTrue(state.completed());
    }

    @ParameterizedTest
    @CsvSource("0")
    @MethodSource("dataScan")
    void testScanOnCompletion(int scanDeg) {
        // Given a look straight action
        List<EnvFSMContext> onCompletions = new ArrayList<>();
        state.onCompletion((ctx, def) -> {
            onCompletions.add(ctx);
            return def;
        });
        Complex scanDir = Complex.fromDeg(scanDeg);
        Iterator<MockFSMContext> iter = MockFSMContext.builder()
                // 0 - init
                .add(builder)
                // 1 - 1st tick
                .add(builder)
                // 2 - valid lidar signal
                .add(builder.headAngle(scanDeg)
                        .addTime(1)
                        .updateLidarTime())
                // 3 - after completion
                .add(builder.addTime(1))
                .build()
                .iterator();

        // When 0 - init
        MockFSMContext ctx = iter.next();
        state.init(ctx, scanDir);

        // When 1 - 1st tick
        ctx = iter.next();
        HeadStatus cmd = state.tick(ctx);
        // Then command should scan at target direction
        assertEquals(HeadStatus.scan(scanDir), cmd);
        assertFalse(state.completed());
        assertThat(onCompletions, empty());

        // When 2 - valid lidar signal
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at target direction
        assertEquals(HeadStatus.scan(scanDir), cmd);
        assertTrue(state.completed());
        assertThat(onCompletions, contains(ctx));

        // When 4 - after completion
        ctx = iter.next();
        cmd = state.tick(ctx);
        // Then command should scan at target direction
        assertEquals(HeadStatus.scan(scanDir), cmd);
        assertTrue(state.completed());
        assertThat(onCompletions, hasSize(2));
        assertThat(onCompletions, hasItem(ctx));
    }
}