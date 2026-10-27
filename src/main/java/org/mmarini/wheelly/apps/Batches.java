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

package org.mmarini.wheelly.apps;

import io.reactivex.functions.Function3;
import org.mmarini.wheelly.storage.serde.ArrayReader;
import org.mmarini.wheelly.storage.serde.ArrayWriter;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.lang.String.format;

/**
 * Batch utilities
 */
public interface Batches {
    Logger logger = LoggerFactory.getLogger(Batches.class);
    long MONITOR_INTERVAL = 30000;

    /**
     * Copies the input reader to output writer
     *
     * @param output    the output writer
     * @param input     the input reader
     * @param batchSize the batch size
     * @throws IOException in case of error
     */
    static void copy(ArrayWriter output, ArrayReader input, long batchSize) throws IOException {
        Monitor monitor = new Monitor();
        try (input) {
            try (output) {
                output.clear();
                input.seek(0);
                for (long n = 0; ; ) {
                    INDArray records = input.read(batchSize);
                    if (records == null) {
                        break;
                    }
                    n += records.size(0);
                    output.write(records);
                    monitor.wakeUp(input.file(), n);
                }
            }
        }
    }

    /**
     * Maps the input file to output file using mapper
     *
     * @param output    the output file
     * @param input     he input file
     * @param batchSize the batch size
     * @param mapper    the mapper function
     * @throws IOException in case of error
     */
    static void map(ArrayWriter output, ArrayReader input, long batchSize, UnaryOperator<INDArray> mapper) throws IOException {
        Monitor monitor = new Monitor();
        try (input) {
            try (output) {
                output.clear();
                input.seek(0);
                for (long n = 0; ; ) {
                    INDArray records = input.read(batchSize);
                    if (records == null) {
                        break;
                    }
                    n += records.size(0);
                    monitor.wakeUp(input.file(), n);
                    try (INDArray mapped = mapper.apply(records)) {
                        output.write(mapped);
                    }
                }
            }
        }
    }

    /**
     * Returns the reduced value of input file
     *
     * @param <T>       the reduce result type
     * @param input     the input file
     * @param seed      the seed
     * @param batchSize the batch size
     * @param reducer   the reducer function
     * @throws IOException in case of error
     */
    static <T> T reduce(ArrayReader input, T seed, long batchSize, Function3<T, INDArray, Long, T> reducer) throws Exception {
        Monitor monitor = new Monitor();
        try (input) {
            input.seek(0);
            for (long n = 0; ; ) {
                INDArray data = input.read(batchSize);
                if (data == null) {
                    break;
                }
                seed = reducer.apply(seed, data, n);
                n += data.size(0);
                monitor.wakeUp(input.file(), n);
            }
            return seed;
        }
    }

    class Monitor {
        private long next;

        public Monitor() {
            next = System.currentTimeMillis() + MONITOR_INTERVAL;
        }

        public void wakeUp(Supplier<String> msg) {
            long t0 = System.currentTimeMillis();
            if (t0 >= next) {
                logger.atInfo().log(msg);
                next = t0 + MONITOR_INTERVAL;
            }
        }

        public void wakeUp(File file, long position) {
            wakeUp(() -> format("Records %d at %s", position, file));
        }
    }
}
