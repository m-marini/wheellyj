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

package org.mmarini;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class RandomArgumentsGeneratorTest {

    @Test
    void testDouble() {
        List<Arguments> args = new RandomArgumentsGenerator(new Random(123))
                .uniform(0D, 10D)
                .build(6)
                .toList();
        assertThat(args, hasSize(6));
        assertThat(args.getFirst().get(), arrayContaining(0D));
        assertThat(args.get(1).get(), arrayContaining(10D));

        assertThat((Double) args.get(2).get()[0], closeTo(7.231, 1e-3));
        assertThat((Double) args.get(3).get()[0], closeTo(9.909, 1e-3));
        assertThat((Double) args.get(4).get()[0], closeTo(2.533, 1e-3));
        assertThat((Double) args.get(5).get()[0], closeTo(6.088, 1e-3));
    }

    @Test
    void testInt() {
        List<Arguments> args = new RandomArgumentsGenerator(new Random(123))
                .uniform(0, 10)
                .build(6)
                .toList();
        assertThat(args, hasSize(6));
        assertThat(args.getFirst().get(), arrayContaining(0));
        assertThat(args.get(1).get(), arrayContaining(10));

        assertThat(args.get(2).get(), arrayContaining(10));
        assertThat(args.get(3).get(), arrayContaining(9));
        assertThat(args.get(4).get(), arrayContaining(0));
        assertThat(args.get(5).get(), arrayContaining(9));
    }

    @Test
    void testIntInt() {
        List<Arguments> args = new RandomArgumentsGenerator(new Random(123))
                .uniform(0, 10)
                .uniform(2, 20)
                .build(7)
                .toList();
        assertThat(args, hasSize(7));
        assertThat(args.getFirst().get(), arrayContaining(0, 2));
        assertThat(args.get(1).get(), arrayContaining(0, 20));
        assertThat(args.get(2).get(), arrayContaining(10, 2));
        assertThat(args.get(3).get(), arrayContaining(10, 20));

        assertThat(args.get(4).get(), arrayContaining(10, 14));
        assertThat(args.get(5).get(), arrayContaining(0, 6));
        assertThat(args.get(6).get(), arrayContaining(0, 4));
    }

    @Test
    void testLong() {
        List<Arguments> args = new RandomArgumentsGenerator(new Random(123))
                .uniform(0L, 10L)
                .build(6)
                .toList();
        assertThat(args, hasSize(6));
        assertThat(args.getFirst().get(), arrayContaining(0L));
        assertThat(args.get(1).get(), arrayContaining(10L));

        assertThat(args.get(2).get(), arrayContaining(7L));
        assertThat(args.get(3).get(), arrayContaining(0L));
        assertThat(args.get(4).get(), arrayContaining(8L));
        assertThat(args.get(5).get(), arrayContaining(5L));
    }
}