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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.CompletableSubject;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.BatchTrainer;
import org.mmarini.rl.agents.KpiBinWriter;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.swing.KpisPanel;
import org.mmarini.wheelly.swing.LearnPanel;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run the batch training of agent from kpis files
 */
public class BatchTraining {
    private static final String BATCH_SCHEMA_YML = "https://mmarini.org/wheelly/batch-schema-0.3";
    private static final int KPIS_CAPACITY = 1000;
    private static final Logger logger = LoggerFactory.getLogger(BatchTraining.class);

    static {
        Nd4j.zeros(1);
    }

    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(BatchTraining.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-k", "--kpis")
                .setDefault("")
                .help("specify kpis path");
        parser.addArgument("-c", "--config")
                .setDefault("batch.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-l", "--labels")
                .setDefault("default")
                .help("specify kpi label regex comma separated (all for all kpi, batch for batch training kpis)");
        parser.addArgument("-n", "--no-backup")
                .action(Arguments.storeTrue())
                .help("no backup of network file");
        parser.addArgument("-w", "--windows")
                .action(Arguments.storeTrue())
                .help("use multiple windows");
        parser.addArgument("dataset")
                .required(true)
                .help("specify dataset path");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new BatchTraining(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Exception");
            System.exit(1);
        }
    }

    protected final Namespace args;
    private final CompletableSubject completed;
    private final JTextField infoBar;
    private final KpisPanel kpisPanel;
    private final JProgressBar recordBar;
    private final LearnPanel learnPanel;
    private BatchTrainer trainer;
    private KpiBinWriter kpiWriter;
    private List<JFrame> allFrames;
    private Agent agent;
    private int numEpochs;

    /**
     * Creates the application
     *
     * @param args the parsed command line arguments
     */
    public BatchTraining(Namespace args) {
        this.args = requireNonNull(args);
        this.completed = CompletableSubject.create();
        this.learnPanel = new LearnPanel();
        this.infoBar = new JTextField();
        this.kpisPanel = new KpisPanel();
        this.recordBar = new JProgressBar(JProgressBar.HORIZONTAL);
        init();
    }

    /**
     * Returns the content
     *
     */
    private Component createContent() {
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        kpisPanel.addActionKpis(this.agent);
        content.add(kpisPanel, BorderLayout.CENTER);
        content.add(infoBar, BorderLayout.NORTH);
        content.add(recordBar, BorderLayout.SOUTH);
        return content;
    }

    /**
     * Creates multi frames
     */
    private void createFrames() {
        JFrame frame = createFrame(Messages.getString("BatchTraining.title"),
                createContent());

        JFrame learnFrame = learnPanel.createFrame();
        this.allFrames = List.of(frame, learnFrame);

        layHorizontally(frame, learnFrame);
    }

    /**
     * Creates the application single frame
     */
    private void createSingleFrames() {
        // Create the frame
        JTabbedPane tabPanel = new JTabbedPane();
        tabPanel.addTab(Messages.getString("BatchTraining.tabPanel.kpis"), createContent());
        tabPanel.addTab(Messages.getString("BatchTraining.tabPanel.learn"),
                new GridLayoutHelper<>(new JPanel())
                        .modify("insets,10 center").add(learnPanel)
                        .getContainer());

        JFrame frame = createFrame(Messages.getString("BatchTraining.title"), tabPanel);

        this.allFrames = List.of(frame);

        center(frame);

        allFrames.forEach(f -> {
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        });
    }

    /**
     * Handles the kpis
     *
     * @param kpis the kpis
     */
    private void handleKpis(Map<String, INDArray> kpis) {
        kpisPanel.addKpis(kpis);
        INDArray counters = kpis.get("counters");
        if (counters != null) {
            long epoch = counters.getLong(0, 0);
            long step = counters.getLong(0, 2);
            long numSteps = counters.getLong(0, 3);
            info("Epoch/step %d/%d of %d/%d",
                    epoch,
                    step,
                    numEpochs,
                    numSteps
            );
        }
    }

    /**
     * Handle shutdown
     */
    private void handleShutdown() {
        info("Shutting down ...");
        trainer.stop();
        completed.blockingAwait();
        info("Shutting down completed");
    }

    /**
     * Show info
     *
     * @param fmt  the format text
     * @param args the arguments
     */
    private void info(String fmt, Object... args) {
        String msg = format(fmt, args);
        infoBar.setText(msg);
        logger.atInfo().log(msg);
    }

    /**
     * Initializes the application
     */
    private void init() {
        recordBar.setValue(0);
        recordBar.setStringPainted(true);
        infoBar.setHorizontalAlignment(JTextField.CENTER);
        infoBar.setFont(infoBar.getFont().deriveFont(Font.BOLD));
        infoBar.setEditable(false);
    }

    /**
     * Starts the training
     */
    protected void run() throws Exception {

        // Load configuration
        File configFile = new File(this.args.getString("config"));
        JsonNode config = fromFile(configFile);
        RobotEnvironment environment = AppYaml.envFromJson(config, BATCH_SCHEMA_YML);

        // Creates random number generator
        Random random = Nd4j.getRandomFactory().getNewRandomInstance();
        long seed = Locator.locate("seed").getNode(config).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }

        // Loads agent
        this.agent = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()),
                environment);

        if (agent instanceof AbstractAgentNN aa) {
            this.agent = aa.setPostTrainKpis(true);
        }

        // Create the batch trainer
        numEpochs = Locator.locate("numEpochs").getNode(config).asInt(agent.numEpochs());
        this.trainer = BatchTrainer.create(agent, numEpochs);
        learnPanel.setActionAlphas(agent.alphas());
        learnPanel.setEta(agent.eta());
        trainer.readInfo()
                .observeOn(Schedulers.io())
                .doOnNext(this::info)
                .subscribe();
        trainer.readKpis()
                .observeOn(Schedulers.io())
                .doOnNext(this::handleKpis)
                .subscribe();
        trainer.readCounter()
                .observeOn(Schedulers.io())
                .map(Number::intValue)
                .doOnNext(recordBar::setValue)
                .subscribe();
        learnPanel.readActionAlphas()
                .doOnNext(rates -> trainer.alphas(rates))
                .subscribe();
        learnPanel.readEtas()
                .doOnNext(eta -> trainer.eta(eta))
                .subscribe();

        Thread hook = new Thread(this::handleShutdown);
        Runtime.getRuntime().addShutdownHook(hook);

        if (args.getBoolean("windows")) {
            createFrames();
        } else {
            createSingleFrames();
        }

        Completable.fromAction(this::runBatch)
                .subscribeOn(Schedulers.computation())
                .doOnComplete(() -> allFrames.forEach(Window::dispose))
                .subscribe();

        allFrames.forEach(f -> {
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        });
    }

    /**
     * Runs the batch
     *
     * @throws Exception in case of error
     */
    private void runBatch() throws Exception {
        // Prepares for training
        info("Preparing for training ...");
        trainer.validate(new File(this.args.getString("dataset")));
        recordBar.setMaximum((int) trainer.numRecords());
        trainer.prepare();

        // reads kpis if activated
        String kpiPath = this.args.getString("kpis");
        this.kpiWriter = kpiPath.isEmpty()
                ? null
                : KpiBinWriter.createFromLabels(
                new File(kpiPath),
                this.args.getString("label"));

        if (kpiWriter != null) {
            this.trainer.readKpis()
                    .observeOn(Schedulers.io())
                    .onBackpressureBuffer(KPIS_CAPACITY, true)
                    .doOnNext(kpiWriter::write)
                    .doOnComplete(() -> {
                        kpiWriter.close();
                        completed.onComplete();
                    })
                    .doOnError(ex -> logger.atError().setCause(ex).log("Error on kpis"))
                    .subscribe();

        } else {
            completed.onComplete();
        }
        // Runs the training session
        info("Training ...");
        trainer.train();
        info("Training completed.");
    }
}
