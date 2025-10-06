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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.functions.Action;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.ParallelProcess;
import org.mmarini.swing.Messages;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.mmarini.yaml.Utils.fromFile;
import static org.mmarini.yaml.Utils.objectMapper;

/**
 * Runs the process to produce report data about learning kpis
 */
public class Report {
    private static final long DEFAULT_BATCH_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(Report.class);

    private static final List<ReportProcess.Builder> REPORTS = List.of(
            ReportProcess.meanReport("reward"), // Reward
            ReportProcess.meanReport("avgReward"), // Average reward

            ReportProcess.meanReport("dr"), // residual reward
            ReportProcess.meanReport("dv"), // residual prediction
            ReportProcess.meanReport("delta"), // td error

            ReportProcess.meanReport("trainingLayers.critic.values"), // Critic

            ReportProcess.statsReport("trainingLayers.move.values"), // max probability for move
            ReportProcess.maxReport("trainingLayers.move.values"), // max probability for move
            ReportProcess.gmReport("trainingLayers.move.values"), // gm probability for move
            ReportProcess.maxMinRatioReport("trainingLayers.move.values"), // max/min probability ratio for move
            ReportProcess.saturationReport("trainingLayers.move[1].values"), // saturation ratio for move
            ReportProcess.maxGMRatioReport("trainingLayers.move.values"), // max/mean probability for move

            ReportProcess.statsReport("trainingLayers.sensorAction.values"), // max probability for move
            ReportProcess.maxReport("trainingLayers.sensorAction.values"), // max probability for sensor
            ReportProcess.gmReport("trainingLayers.sensorAction.values"), // gm probability for sensor
            ReportProcess.saturationReport("trainingLayers.sensorAction[1].values"), // saturation for sensor
            ReportProcess.maxMinRatioReport("trainingLayers.sensorAction.values"), // max probability for sensor
            ReportProcess.maxGMRatioReport("trainingLayers.sensorAction.values"),

            ReportProcess.rmsReport("deltaGrads.critic"), // delta eta gradient for critic
            ReportProcess.sumRmsReport("deltaGrads.move"), // delta eta alpha gradient for move
            ReportProcess.sumRmsReport("deltaGrads.sensorAction"), // delta eta for critic for

            ReportProcess.deltaRatioReport("move"),
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
        parser.addArgument("model")
                .required(true)
                .help("specify the source model path");
        parser.addArgument("reportPath")
                .required(true)
                .help("specify the destination report path");
        return parser;
    }

    /**
     * Returns the report json node
     *
     * @param root the agent json node
     */
    private static JsonNode extractReportYaml(JsonNode root) {
        Locator networkLocator = Locator.locate("network");
        Locator layersLocator = networkLocator.path("layers");
        Locator sizesLocator = networkLocator.path("sizes");
        double moveTemperature = layersLocator.elements(root)
                .filter(l -> "move".equals(l.path("name").getNode(root).asText()))
                .findAny()
                .map(l -> l.path("temperature").getNode(root).asDouble())
                .orElse(0d);
        double sensorTemperature = layersLocator.elements(root)
                .filter(l -> "sensorAction".equals(l.path("name").getNode(root).asText()))
                .findAny()
                .map(l -> l.path("temperature").getNode(root).asDouble())
                .orElse(0d);
        long moveSize = sizesLocator.path("move").getNode(root).asLong();
        long sensorSize = sizesLocator.path("sensorAction").getNode(root).asLong();
        double ppoEpsilon = root.path("ppoEpsilon").asDouble();
        double eta = root.path("eta").asDouble();
        double moveAlpha = Locator.locate("alphas").path("move").getNode(root).asDouble();
        double sensorAlpha = Locator.locate("alphas").path("sensorAction").getNode(root).asDouble();

        ObjectNode report = objectMapper.createObjectNode();
        report.put("ppoEpsilon", ppoEpsilon);
        report.put("sensorActionTemperature", sensorTemperature);
        report.put("moveTemperature", moveTemperature);
        report.put("moveSize", moveSize);
        report.put("sensorActionSize", sensorSize);
        report.put("eta", eta);
        report.put("moveAlpha", moveAlpha);
        report.put("sensorActionAlpha", sensorAlpha);
        return report;
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
     * Generates a report YAML file
     *
     * @throws IOException in case of error
     */
    protected void generateReportYaml() throws IOException {
        String modelPath = args.getString("model");
        JsonNode root = fromFile(new File(modelPath, "agent.yml"));
        JsonNode report = extractReportYaml(root);
        this.reportPath = new File(args.getString("reportPath"));
        File resultFile = new File(reportPath, "report.yml");
        resultFile.getParentFile().mkdirs();
        objectMapper.writeValue(resultFile, report);
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
        generateReportYaml();
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
