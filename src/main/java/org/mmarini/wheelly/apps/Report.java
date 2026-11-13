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

import io.reactivex.rxjava3.functions.Action;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.ParallelProcess;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.storage.csv.CSVWriter;
import org.mmarini.wheelly.storage.serde.ArrayReader;
import org.mmarini.wheelly.storage.serde.FileArrayReader;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.mmarini.wheelly.gyms.MacroActionsAdapter.ACTION_HEAD_ID;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.ACTION_MOVE_ID;
import static org.mmarini.wheelly.rlagents.NNRLTrainingDataGenerator.CRITIC_ID;
import static org.mmarini.wheelly.rlagents.TrainingKpis.AVG_REWARD_ID;
import static org.mmarini.wheelly.rlagents.TrainingKpis.DELTAS_ID;

/**
 * Command-line application that processes reinforcement learning Key Performance Indicators (KPIs)
 * and generates CSV report files for subsequent analysis and visualisation.
 * <p>
 * The application reads serialised array datasets for rewards, training KPIs (average reward, deltas,
 * critic values), and action policies (movement and heading), aggregates them into binned distributions
 * alongside ordinary least squares regression trends, and writes a pair of CSV files ({@code <key>} and
 * {@code <key>_reg}) for each metric into the specified destination directory.
 * </p>
 */
public class Report {
    public static final int DEFAULT_NUM_BINS = 100;
    public static final String REWARDS_ID = "rewards";
    private static final long DEFAULT_BATCH_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(Report.class);

    /**
     * Builds and configures the command-line argument parser for this application.
     *
     * @return the configured {@link ArgumentParser} instance
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(Report.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Parse kpi files and produce the csv files for report generation.");
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
     * Application entry point. Parses the command-line arguments and initiates the report generation workflow.
     * <p>
     * Exits with status code {@code 1} if argument parsing fails, or status code {@code 2} if an unexpected
     * runtime error occurs during processing.
     * </p>
     *
     * @param args the command-line arguments supplied to the programme
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
    private final Path reportPath;
    private final Path kpisPath;
    private final FileArrayReader rewardsFile;
    protected Namespace args;

    /**
     * Initialises a new {@code Report} application instance from the parsed command-line arguments.
     *
     * @param args the {@link Namespace} containing the parsed command-line options and positional arguments
     */
    protected Report(Namespace args) {
        this.args = args;
        this.batchSize = args.getLong("batchSize");
        this.numBins = args.getInt("numBins");
        this.kpisPath = Paths.get(args.getString("kpis"));
        this.reportPath = Path.of(args.getString("reportPath"));
        String filename = args.getString("rewards");
        this.rewardsFile = filename != null
                ? new FileArrayReader(Paths.get(filename))
                : FileArrayReader.createByKey(kpisPath, "rewards");
    }

    /**
     * Creates a processing task that generates a linear report from a serialised file located
     * under the specified base directory.
     *
     * @param path the base directory containing the source KPI file
     * @param key  the identifier key used to resolve the source file and name the output CSV files
     * @return an {@link Action} encapsulating the linear report generation task
     */
    private Action createLinearTask(Path path, String key) {
        return createLinearTask(FileArrayReader.createByKey(path, key), key);
    }

    /**
     * Creates a processing task that computes binned linear statistics and regression endpoints
     * from the given {@link ArrayReader}, writing two CSV files into {@code reportPath}:
     * <ul>
     *   <li>{@code <key>} (shape {@code [n, 5]}): binned summary where each row contains:
     *     <ol start="0">
     *       <li>Ending record index (file position)</li>
     *       <li>Arithmetic mean</li>
     *       <li>Minimum value</li>
     *       <li>Maximum value</li>
     *       <li>Exponential moving average (EMA) computed with {@code gamma = min(numBins / N, 0.99)}</li>
     *     </ol>
     *   </li>
     *   <li>{@code <key>_reg} (shape {@code}): single-row linear regression endpoints containing
     *       the initial fitted value at {@code x = 0} (column 0) and the final fitted value at
     *       {@code x = M - 1} (column 1).</li>
     * </ul>
     *
     * @param reader the reader providing access to the serialised source array
     * @param key    the identifier key used for logging and naming the output CSV files
     * @return an {@link Action} encapsulating the linear report generation task
     */
    private Action createLinearTask(ArrayReader reader, String key) {
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
     * Creates a processing task that computes binned policy distribution statistics and regression
     * trends for a specific action key, writing two CSV files into {@code reportPath}:
     * <ul>
     *   <li>{@code <key>} (shape {@code [n, 10]}): binned summary where each row contains:
     *     <ol start="0">
     *       <li>Ending record index (file position)</li>
     *       <li>Mean of {@code log10(max policy)}</li>
     *       <li>Minimum of {@code log10(max policy)}</li>
     *       <li>Maximum of {@code log10(max policy)}</li>
     *       <li>Exponential moving average (EMA) of {@code log10(max policy)} computed with
     *           {@code gamma = min(numBins / N, 0.99)}</li>
     *       <li>Mean of normalised policy entropy</li>
     *       <li>Minimum of normalised policy entropy</li>
     *       <li>Maximum of normalised policy entropy</li>
     *       <li>Exponential moving average (EMA) of normalised policy entropy</li>
     *       <li>Number of available actions</li>
     *     </ol>
     *   </li>
     *   <li>{@code <key>_reg} (shape {@code [1, 4]}): single-row linear regression endpoints across all
     *       {@code M} records containing:
     *     <ol start="0">
     *       <li>Initial fitted value of {@code log10(max policy)} at {@code x = 0}</li>
     *       <li>Final fitted value of {@code log10(max policy)} at {@code x = M - 1}</li>
     *       <li>Initial fitted value of normalised entropy at {@code x = 0}</li>
     *       <li>Final fitted value of normalised entropy at {@code x = M - 1}</li>
     *     </ol>
     *   </li>
     * </ul>
     *
     * @param key the action identifier key used to locate the source file and name the output CSV files
     * @return an {@link Action} encapsulating the policy report generation task
     */
    private Action createPolicyTask(String key) {
        return () -> {
            logger.atInfo().log("Started logarithmic report for key: {}", key);
            FileArrayReader reader = FileArrayReader.createByKey(kpisPath, key);
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
     * Assembles the ordered stream of report generation tasks.
     * <p>
     * The resulting stream concatenates the reward report task, the linear KPI report tasks
     * (average reward, training deltas, and critic values), and the policy action report tasks
     * (movement and heading actions).
     * </p>
     *
     * @return a {@link Stream} of {@link Action} instances ready for execution
     */
    private Stream<Action> createTasks() {
        Stream<Action> actionTasks = Stream.of(ACTION_MOVE_ID, ACTION_HEAD_ID)
                .map(this::createPolicyTask);
        Stream<Action> kpisTasks = Stream.of(AVG_REWARD_ID, DELTAS_ID, CRITIC_ID)
                .map(key ->
                        createLinearTask(kpisPath, key)
                );
        Stream<Action> rewardTasks = Stream.of(createLinearTask(rewardsFile, REWARDS_ID));
        return Stream.concat(
                Stream.concat(rewardTasks, kpisTasks),
                actionTasks);
    }

    /**
     * Initiates the report generation workflow by executing all configured tasks.
     * <p>
     * When the {@code --parallel} flag is enabled, tasks are scheduled and executed concurrently via
     * {@link ParallelProcess}. Otherwise, they are executed sequentially on the current thread, logging
     * any individual task failures without aborting subsequent tasks.
     * </p>
     *
     * @throws Throwable if an unrecoverable error occurs during parallel scheduling or task execution
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
