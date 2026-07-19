/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
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
import org.mmarini.rl.agents.BinArrayFile;
import org.mmarini.rl.agents.CSVWriter;
import org.mmarini.swing.Messages;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Runs the process to produce report data about learning kpis
 */
public class Report {
    public static final int DEFAULT_NUM_BINS = 100;
    private static final long DEFAULT_BATCH_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(Report.class);

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(Report.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-b", "--batchSize")
                .setDefault(DEFAULT_BATCH_SIZE)
                .type(Long.class)
                .help("batch size");
        parser.addArgument("-n", "--numBins")
                .setDefault(DEFAULT_NUM_BINS)
                .type(Integer.class)
                .help("batch size");
        parser.addArgument("-p", "--parallel")
                .action(Arguments.storeTrue())
                .help("run parallel tasks");
        parser.addArgument("-r", "--rewards")
                .help("specify the reward source path");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("kpis")
                .required(true)
                .help("specify the source kpis path");
        parser.addArgument("reportPath")
                .required(true)
                .help("specify the destination report path");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new Report(parser.parseArgs(args)).start();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error generating report");
            System.exit(2);
        }
    }

    private final long batchSize;
    private final int numBins;
    private final File reportPath;
    private final File kpisPath;
    private final BinArrayFile rewardsFile;
    protected Namespace args;

    /**
     * Creates the report application
     *
     * @param args the parsed arguments
     */
    protected Report(Namespace args) {
        this.args = args;
        this.batchSize = args.getLong("batchSize");
        this.numBins = args.getInt("numBins");
        this.kpisPath = new File(args.getString("kpis"));
        this.reportPath = new File(args.getString("reportPath"));
        this.rewardsFile = Optional.ofNullable(args.getString("rewards"))
                .map(filename ->
                        new BinArrayFile(new File(filename), "r"))
                .orElseGet(() ->
                        BinArrayFile.createByKey(kpisPath, "rewards"));
    }

    /**
     * Returns the task for linear data
     *
     * @param path the base path
     * @param key  the key
     */
    private Action createLinearTask(File path, String key) {
        return createLinearTask(BinArrayFile.createByKey(path, key), key);
    }

    /**
     * Returns the task for linear data
     *
     * @param reader the binary array file
     * @param key    the key
     */
    private Action createLinearTask(BinArrayFile reader, String key) {
        return () -> {
            logger.atInfo().log("Started linear report task for key: {}", key);
            long n = reader.size();
            double gamma = Math.min((double) numBins / n, 0.99);
            INDArray[] reports = Reports.linReport(reader, UnaryOperator.identity(), numBins, gamma, batchSize);
            try (INDArray report = reports[0]) {
                try (CSVWriter writer = CSVWriter.createByKey(reportPath, key)) {
                    writer.clear();
                    writer.write(report);
                }
            }
            try (INDArray report = reports[1]) {
                try (CSVWriter writer = CSVWriter.createByKey(reportPath, key + "_reg")) {
                    writer.clear();
                    writer.write(report);
                }
            }
            logger.atInfo().log("Completed linear report for key: {}", key);
        };
    }

    /**
     * Returns the task for logarithmic data
     *
     * @param key the key
     */
    private Action createPolicyTask(String key) {
        return () -> {
            logger.atInfo().log("Started logarithmic report for key: {}", key);
            BinArrayFile reader = BinArrayFile.createByKey(kpisPath, key);
            long n = reader.size();
            double gamma = Math.min((double) numBins / n, 0.99);
            INDArray[] reports = Reports.policyReport(reader, numBins, gamma, batchSize);
            try (INDArray report = reports[0]) {
                try (CSVWriter writer = CSVWriter.createByKey(reportPath, key)) {
                    writer.clear();
                    writer.write(report);
                }
            }
            try (INDArray report = reports[1]) {
                try (CSVWriter writer = CSVWriter.createByKey(reportPath, key + "_reg")) {
                    writer.clear();
                    writer.write(report);
                }
            }
            logger.atInfo().log("Completed logarithmic report for key: {}", key);
        };
    }

    /**
     *
     * Returns the tasks
     */
    private Stream<Action> createTasks() {
        Stream<Action> actionTasks = Stream.of("head", "move")
                .map(this::createPolicyTask);
        Stream<Action> kpisTasks = Stream.of("avgReward", "deltas", "critic")
                .map(key ->
                        createLinearTask(kpisPath, key)
                );
        Stream<Action> rewardTasks = Stream.of(createLinearTask(rewardsFile, "rewards"));
        return Stream.concat(
                Stream.concat(rewardTasks, kpisTasks),
                actionTasks);
    }

    /**
     * Start to produce the report
     *
     * @throws IOException in case of error
     */
    protected void start() throws Throwable {
        Stream<Action> tasks = createTasks();
        if (args.getBoolean("parallel")) {
            ParallelProcess.scheduler(tasks.toList()).run();
        } else {
            tasks.forEach(task -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    logger.atError().setCause(e).log("Error running task");
                }
            });
        }
        logger.atInfo().log("Completed.");
    }
}
