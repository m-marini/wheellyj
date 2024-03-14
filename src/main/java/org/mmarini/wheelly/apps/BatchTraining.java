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
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.BatchTrainer;
import org.mmarini.rl.agents.KpiBinWriter;
import org.mmarini.rl.agents.Serde;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.wheelly.swing.KpisPanel;
import org.mmarini.wheelly.swing.LearnPanel;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class BatchTraining {
    private static final String BATCH_SCHEMA_YML = "https://mmarini.org/wheelly/batch-schema-0.1";
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
            new BatchTraining(parser.parseArgs(args)).start();
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
    private String pathFile;
    private BatchTrainer trainer;
    private boolean backUp;
    private KpiBinWriter kpiWriter;
    private TDNetwork network;

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
     * @param actions the action keys
     */
    private Component createContent(String... actions) {
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        kpisPanel.setKeys(actions);
        content.add(kpisPanel, BorderLayout.CENTER);
        content.add(infoBar, BorderLayout.NORTH);
        content.add(recordBar, BorderLayout.SOUTH);
        return content;
    }

    private void createFrames() {
        List<String> outputs = network.sinkLayers();

        // Create the frame
        String[] actions = outputs.stream()
                .filter(Predicate.not("critic"::equals))
                .toArray(String[]::new);

        JFrame frame = createFrame(Messages.getString("BatchTraining.title"),
                createContent(actions));

        JFrame learnFrame = learnPanel.createFrame();

        List<JFrame> allFrames = List.of(frame, learnFrame);

        layHorizontally(frame, learnFrame);

        allFrames.forEach(f -> {
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        });

        Completable.fromAction(this::runBatch)
                .subscribeOn(Schedulers.computation())
                .doOnComplete(() -> allFrames.forEach(Window::dispose))
                .subscribe();
    }

    /**
     * Handles shutdown
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
     * Returns the alphas from agent yaml file
     *
     * @throws IOException in case of error
     */
    private Map<String, Float> loadAlphas() throws IOException {
        JsonNode spec = Utils.fromFile(new File(pathFile, "agent.yml"));
        return Locator.locate("alphas").propertyNames(spec)
                .map(Tuple2.map2(l -> (float) l.getNode(spec).asDouble()))
                .collect(Tuple2.toMap());
    }

    /**
     * Returns the network from agent path
     *
     * @param random the random number generator
     * @throws IOException in case of error
     */
    private TDNetwork loadNetwork(Random random) throws IOException {
        JsonNode spec = Utils.fromFile(new File(pathFile, "agent.yml"));
        File file = new File(pathFile, "agent.bin");
        Map<String, INDArray> props = Serde.deserialize(file);
        Locator locator = Locator.root();
        return TDNetwork.fromJson(spec, locator.path("network"), props, random);
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

        }
        // Runs the training session
        info("Training ...");
        trainer.train();
        info("Training completed.");
    }

    /**
     * Saves the network
     *
     * @param network the network
     */
    private void saveNetwork(TDNetwork network) {
        File file = new File(pathFile, "agent.bin");
        if (!args.getBoolean("no_backup") && !backUp) {
            // rename network file to back up
            String backupFileName = format("agent-%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS.bin", Calendar.getInstance());
            file.renameTo(new File(file.getParentFile(), backupFileName));
            backUp = true;
            logger.atInfo().log("Backup at {}", backupFileName);
        }

        info("Saving network ...");
        Map<String, INDArray> props = new HashMap<>(network.parameters());
        props.put("avgReward", Nd4j.createFromArray(trainer.avgReward()));
        try {
            Serde.serizalize(file, props);
            logger.atInfo().log("Saved network in {}", file.getCanonicalPath());
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error saving network");
        }
    }

    /**
     * Starts the training
     */
    protected void start() throws Exception {

        // Load configuration
        JsonNode config = fromFile(this.args.getString("config"));
        JsonSchemas.instance().validateOrThrow(config, BATCH_SCHEMA_YML);

        // Creates random number generator
        Random random = Nd4j.getRandomFactory().getNewRandomInstance();
        long seed = Locator.locate("seed").getNode(config).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }

        // Loads network
        info("Load network ...");
        this.pathFile = Locator.locate("modelPath").getNode(config).asText();
        this.network = loadNetwork(random);

        // Create the batch trainer
        int numTrainIterations1 = Locator.locate("numTrainIterations1").getNode(config).asInt();
        int numTrainIterations2 = Locator.locate("numTrainIterations2").getNode(config).asInt();
        long batchSize = Locator.locate("batchSize").getNode(config).asLong(Long.MAX_VALUE);
        Map<String, Float> alphas = loadAlphas();
        this.trainer = BatchTrainer.create(network,
                alphas,
                0,
                numTrainIterations1,
                numTrainIterations2,
                (int) batchSize,
                this::saveNetwork
        );
        learnPanel.setLearningRates(alphas);
        trainer.readInfo()
                .observeOn(Schedulers.io())
                .doOnNext(this::info)
                .subscribe();
        trainer.readKpis()
                .observeOn(Schedulers.io())
                .doOnNext(kpisPanel::addKpis)
                .subscribe();
        trainer.readCounter()
                .observeOn(Schedulers.io())
                .map(Number::intValue)
                .doOnNext(recordBar::setValue)
                .subscribe();
        learnPanel.readLearningRates()
                .doOnNext(rates -> trainer.alphas(rates))
                .subscribe();

        Thread hook = new Thread(this::handleShutdown);
        Runtime.getRuntime().addShutdownHook(hook);

        createFrames();
    }

}
