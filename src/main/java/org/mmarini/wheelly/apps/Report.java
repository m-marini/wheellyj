/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import org.mmarini.wheelly.swing.Messages;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Runs the process to produce report data about learning kpis
 */
public class Report {
    private static final long DEFAULT_BATCH_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(Report.class);

    private static final List<ReportProcess.Builder> REPORTS = List.of(
            ReportProcess.meanReport("reward"), // Reward

            ReportProcess.meanReport("delta"), // td error

            ReportProcess.rmsReport("deltaGrads.critic"), // delta eta gradient for critic

            ReportProcess.maxReport("trainingLayers.direction.values"), // max probability for direction
            ReportProcess.maxMinRatioReport("trainingLayers.direction.values"), // max/min probability ratio for direction
            ReportProcess.maxGMRatioReport("trainingLayers.direction.values"), // max/mean probability for direction

            ReportProcess.maxReport("trainingLayers.speed.values"), // max probability for speed
            ReportProcess.maxMinRatioReport("trainingLayers.speed.values"),
            ReportProcess.maxGMRatioReport("trainingLayers.speed.values"),

            ReportProcess.maxReport("trainingLayers.sensorAction.values"), // max probability for speed
            ReportProcess.maxMinRatioReport("trainingLayers.sensorAction.values"), // max probability for direction
            ReportProcess.maxGMRatioReport("trainingLayers.sensorAction.values"),

            ReportProcess.sumRmsReport("deltaGrads.direction"), // delta eta alpha gradient for direction
            ReportProcess.sumRmsReport("deltaGrads.speed"), // delta eta for critic for speed
            ReportProcess.sumRmsReport("deltaGrads.sensorAction"), // delta eta for critic for

            ReportProcess.deltaRatioReport("direction"),
            ReportProcess.deltaRatioReport("speed"),
            ReportProcess.deltaRatioReport("sensorAction")
    );

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
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-p", "--parallel")
                .action(Arguments.storeTrue())
                .help("run parallel tasks");
        parser.addArgument("-b", "--batchSize")
                .setDefault(DEFAULT_BATCH_SIZE)
                .type(Long.class)
                .help("batch size");
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
    protected Namespace args;
    private File reportPath;
    private File kpisPath;

    /**
     * Creates the report application
     *
     * @param args the parsed arguments
     */
    protected Report(Namespace args) {
        this.args = args;
        this.batchSize = args.getLong("batchSize");
    }

    /**
     * Start to produce the report
     *
     * @throws IOException in case of error
     */
    protected void start() throws Throwable {
        this.kpisPath = new File(args.getString("kpis"));
        this.reportPath = new File(args.getString("reportPath"));
        List<ReportProcess.Builder> activeReports = REPORTS.stream()
                .filter(t ->
                        t.canCreate(kpisPath))
                .toList();

        List<Action> tasks = activeReports.stream()
                .<Action>map(t -> () ->
                        t.build(kpisPath, reportPath, batchSize).process())
                .toList();
        if (args.getBoolean("parallel")) {
            ParallelProcess.scheduler(tasks).run();
        } else {
            for (Action task : tasks) {
                task.run();
            }
        }
        logger.atInfo().log("Completed.");
    }
}
