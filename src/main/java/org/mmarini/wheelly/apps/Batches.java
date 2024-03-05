package org.mmarini.wheelly.apps;

import io.reactivex.functions.Function3;
import org.mmarini.rl.agents.ArrayReader;
import org.mmarini.rl.agents.ArrayWriter;
import org.mmarini.rl.agents.BinArrayFile;
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
    static <T> T reduce(BinArrayFile input, T seed, long batchSize, Function3<T, INDArray, Long, T> reducer) throws Exception {
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
