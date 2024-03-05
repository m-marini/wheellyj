/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

import io.reactivex.rxjava3.functions.Action;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.ParallelProcess;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.KeyFileMap;
import org.mmarini.wheelly.swing.Messages;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Merges different kpis folders into a merged kpis folder
 */
public class MergeKpis {
    public static final long DEFAULT_BATCH_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(MergeKpis.class);

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(ToCsv.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-b", "--batchSize")
                .setDefault(DEFAULT_BATCH_SIZE)
                .type(Long.class)
                .help("batch size");
        parser.addArgument("-p", "--parallel")
                .action(Arguments.storeTrue())
                .help("run parallel tasks");
        parser.addArgument("output")
                .required(true)
                .help("specify the out path");
        parser.addArgument("sources")
                .type(String.class)
                .nargs("+")
                .help("the source paths");
        return parser;
    }

    /**
     * Runs the app
     *
     * @param args the command line argument s
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            Namespace arguments = parser.parseArgs(args);
            new MergeKpis(arguments).run();
        } catch (ArgumentParserException ex) {
            parser.handleError(ex);
            System.exit(1);
        }
    }

    private final Namespace args;
    private File outputFile;
    private Map<String, List<BinArrayFile>> sources;
    private long batchSize;

    /**
     * Creates the merge kpis application
     *
     * @param args the parsed command line arguments
     */
    public MergeKpis(Namespace args) {
        this.args = args;
    }

    /**
     * Merges kpi
     *
     * @param key           the key
     * @param binArrayFiles the source files
     */
    private void merge(String key, List<BinArrayFile> binArrayFiles) throws IOException {
        try (BinArrayFile output = BinArrayFile.createByKey(outputFile, key)) {
            output.clear();
            for (BinArrayFile source : binArrayFiles) {
                logger.atInfo().log("Merging {} -> {}", source.file(), output.file());
                source.seek(0);
                try (BinArrayFile source1 = source) {
                    for (; ; ) {
                        INDArray data = source1.read(batchSize);
                        if (data == null) {
                            break;
                        }
                        output.write(data);
                    }
                }
            }
        }
    }

    /**
     * Runs the application
     */
    private void run() {
        this.outputFile = new File(args.getString("output"));
        this.batchSize = args.getLong("batchSize");
        validateSources();
        List<Action> tasks = Tuple2.stream(sources)
                .<Action>map(t -> () -> merge(t._1, t._2))
                .toList();
        if (args.getBoolean("parallel")) {
            ParallelProcess.scheduler(tasks).run();
        } else {
            tasks.forEach(task -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    logger.atError().setCause(e).log("Error running task");
                    throw new RuntimeException(e);
                }
            });
        }
    }

    /**
     * Validates the sources
     */
    private void validateSources() {
        List<Tuple2<File, Map<String, BinArrayFile>>> sources = args.<List<String>>get("sources").stream()
                .map(name -> {
                    File file = new File(name);
                    return Tuple2.of(file, KeyFileMap.create(file, ""));
                })
                .toList();

        // Validates output
        if (outputFile.exists() && !outputFile.isDirectory()) {
            throw new IllegalArgumentException(format("Output \"%s\" is not a directory", outputFile));
        }
        outputFile.mkdirs();

        // Validates sources
        Set<String> allKeys = sources.stream()
                .flatMap(t -> t._2.keySet().stream())
                .collect(Collectors.toSet());
        List<Tuple2<File, List<String>>> missing = sources.stream()
                .filter(Predicate.not(t -> allKeys.equals(t._2.keySet())))
                .map(Tuple2.map2(t1 ->
                        allKeys.stream()
                                .filter(Predicate.not(t1::containsKey))
                                .toList()))
                .toList();
        if (!missing.isEmpty()) {
            for (Tuple2<File, List<String>> source : missing) {
                logger.atError().log("Source \"{}\" lack of keys", source._1);
                for (String key : source._2) {
                    logger.atError().log("  \"{}\"", key);
                }
            }
            throw new IllegalArgumentException("Missing keys");
        }
        Map<String, List<Tuple2<String, BinArrayFile>>> binFilesByKey = sources.stream()
                .flatMap(t ->
                        Tuple2.stream(t._2))
                .collect(Collectors.groupingBy(Tuple2::getV1));
        this.sources = Tuple2.stream(binFilesByKey)
                .map(t -> t.setV2(t._2.stream()
                        .map(t1 -> t1._2)
                        .toList()))
                .collect(Tuple2.toMap());
    }
}
