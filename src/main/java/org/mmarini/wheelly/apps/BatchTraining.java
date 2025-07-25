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
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.swing.GridLayoutHelper;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.WorldModeller;
import org.mmarini.wheelly.batch.SignalGenerator;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.envs.WorldEnvironment;
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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.*;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run the batch training of agent from kpis files
 */
public class BatchTraining {
    private static final String BATCH_SCHEMA_YML = "https://mmarini.org/wheelly/batch-schema-0.4";
    private static final int KPIS_CAPACITY = 1000;
    private static final Logger logger = LoggerFactory.getLogger(BatchTraining.class);

    static {
        try (INDArray ignored = Nd4j.zeros(1)) {
        }
    }

    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(BatchTraining.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-c", "--config")
                .setDefault("batch.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-k", "--kpis")
                .setDefault("")
                .help("specify kpis path");
        parser.addArgument("-l", "--labels")
                .setDefault("default")
                .help("specify kpi label regex comma separated (all for all kpi, batch for batch training kpis)");
        parser.addArgument("-n", "--no-backup")
                .action(Arguments.storeTrue())
                .help("no backup of network file");
        parser.addArgument("-t", "--temp")
                .setDefault("tmp")
                .help("specify the working path file");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-w", "--windows")
                .action(Arguments.storeTrue())
                .help("use multiple windows");
        parser.addArgument("dataset")
                .required(true)
                .help("specify dataset");
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
    private final JProgressBar progressBar;
    private final JTextField infoBar;
    private final KpisPanel kpisPanel;
    private final CompletableSubject kpiCompleted;
    private final LearnPanel learnPanel;
    private BatchTrainer trainer;
    private KpiBinWriter kpiWriter;
    private List<JFrame> allFrames;
    private AbstractAgentNN agent;
    private int numEpochs;
    private SignalGenerator signalsGenerator;

    /**
     * Creates the application
     *
     * @param args the parsed command line arguments
     */
    public BatchTraining(Namespace args) {
        this.args = requireNonNull(args);
        this.kpiCompleted = CompletableSubject.create();
        this.learnPanel = new LearnPanel();
        this.infoBar = new JTextField();
        this.kpisPanel = new KpisPanel();
        this.progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
        initUI();
    }

    /**
     * Returns the content
     */
    private Component createContent() {
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        kpisPanel.addActionKpis(this.agent);
        content.add(kpisPanel, BorderLayout.CENTER);
        content.add(infoBar, BorderLayout.NORTH);
        content.add(progressBar, BorderLayout.SOUTH);
        return content;
    }

    /**
     * Creates the application context
     *
     * @throws IOException in case of error
     */
    private void createContext() throws IOException {
        // Load configuration
        JsonNode config = loadConfiguration();

        // Creates robot
        RobotApi robot = AppYaml.robotFromJson(config);

        // Creates controller
        RobotControllerApi controller = AppYaml.controllerFromJson(config);
        controller.connectRobot(robot);

        // Creates modeller
        WorldModeller modeller = AppYaml.modellerFromJson(config);
        modeller.setRobotSpec(robot.robotSpec());
        modeller.connectController(controller);

        // Creates environment
        EnvironmentApi env = AppYaml.envFromJson(config);
        WorldEnvironment environment;
        if (env instanceof WorldEnvironment we) {
            environment = we;
        } else {
            throw new IllegalArgumentException(format(
                    "Environment must be %s (%s)",
                    WorldEnvironment.class.getSimpleName(),
                    env.getClass().getSimpleName()
            ));
        }
        environment.connect(modeller);
        // Load reward function
        RewardFunction rewardFunc = AppYaml.rewardFromJson(config);
        environment.setRewardFunc(rewardFunc);

        // Creates agent
        Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        Agent ag = agentBuilder.apply(environment);
        if (ag instanceof AbstractAgentNN aa) {
            this.agent = aa;
            this.agent = aa.setPostTrainKpis(true);
        } else {
            throw new IllegalArgumentException(format(
                    "Environment must be %s (%s)",
                    AbstractAgentNN.class.getSimpleName(),
                    ag.getClass().getSimpleName()
            ));
        }
        environment.connect(agent);

        // Creates random number generator
        Random random = Nd4j.getRandomFactory().getNewRandomInstance();
        long seed = Locator.locate("seed").getNode(config).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }

        // Creates the signal generator
        TDNetwork net = agent.network();
        String[] s0Labels = net.sourceLayers().toArray(String[]::new);
        Predicate<String> criterion = Predicate.not("critic"::equals);
        String[] actionLabels = net.sinkLayers().stream()
                .filter(criterion)
                .toArray(String[]::new);
        this.signalsGenerator = new SignalGenerator(new File(this.args.getString("dataset")),
                modeller, environment, agent,
                new File(args.getString("temp")),
                s0Labels,
                actionLabels);

        // Creates the batch trainer
        numEpochs = Locator.locate("numEpochs").getNode(config).asInt(agent.numEpochs());
        this.trainer = BatchTrainer.create(agent, numEpochs);

        // Creates the kpi writer
        String kpiPath = this.args.getString("kpis");
        if (!kpiPath.isEmpty()) {
            this.kpiWriter = KpiBinWriter.createFromLabels(
                    new File(kpiPath),
                    this.args.getString("label"));
        }
    }

    /**
     * Creates the event flow
     */
    private void createFlow() {
        signalsGenerator.readInfo()
                .observeOn(Schedulers.io())
                .sample(1000L, TimeUnit.MILLISECONDS)
                .doOnNext(this::onGeneratorInfo)
                .subscribe();
        trainer.readInfo()
                .observeOn(Schedulers.io())
                .sample(1000L, TimeUnit.MILLISECONDS)
                .doOnNext(this::onTrainingInfo)
                .subscribe();
        trainer.readKpis()
                .observeOn(Schedulers.io())
                .doOnNext(this::onKpis)
                .subscribe();
        learnPanel.readActionAlphas()
                .doOnNext(rates -> trainer.alphas(rates))
                .subscribe();
        learnPanel.readEtas()
                .doOnNext(eta -> trainer.eta(eta))
                .subscribe();

        // reads kpis if activated
        if (kpiWriter != null) {
            this.trainer.readKpis()
                    .observeOn(Schedulers.io())
                    .onBackpressureBuffer(KPIS_CAPACITY, true)
                    .doOnNext(kpiWriter::write)
                    .doOnError(ex -> {
                        logger.atError().setCause(ex).log("Error on kpis");
                        info("Error on kpis %s", ex.getMessage());
                    })
                    .doOnComplete(() -> {
                        kpiWriter.close();
                        kpiCompleted.onComplete();
                    })
                    .subscribe();
        } else {
            kpiCompleted.onComplete();
        }
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
    private void initUI() {
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        infoBar.setHorizontalAlignment(JTextField.CENTER);
        infoBar.setFont(infoBar.getFont().deriveFont(Font.BOLD));
        infoBar.setEditable(false);
    }

    /**
     * Returns the validated configuration
     */
    private JsonNode loadConfiguration() throws IOException {
        File configFile = new File(this.args.getString("config"));
        JsonNode config = fromFile(configFile);
        JsonSchemas.instance().validateOrThrow(config, BATCH_SCHEMA_YML);
        return config;
    }

    /**
     * Process the signal generator info
     *
     * @param info the info
     */
    private void onGeneratorInfo(SignalGenerator.GeneratorInfo info) {
        int percentage = (int) (info.processedBytes() * 100 / info.totalBytes());
        info(Messages.getString("BatchTraining.generatorInfo.text"),
                info.numProcessedRecords(), info.processedBytes(), info.totalBytes(), percentage);
        progressBar.setValue(percentage);
    }

    /**
     * Handles the kpis
     *
     * @param kpis the kpis
     */
    private void onKpis(Map<String, INDArray> kpis) {
        kpisPanel.addKpis(kpis);
    }

    /**
     * Handle shutdown
     */
    private void onShutdown() {
        info("Shutting down ...");
        signalsGenerator.stop();
        trainer.stop();
        if (kpiCompleted != null) {
            kpiCompleted.blockingAwait();
        }
        info("Shutting down completed");
    }

    /**
     * Handles training info
     *
     * @param info the info
     */
    private void onTrainingInfo(BatchTrainer.TrainingInfo info) {
        int percentage = (int) (info.processedRecords() * 100 / info.totalRecords());
        info(Messages.getString("BatchTraining.trainingInfo." + info.text() + ".text"),
                info.epoch(),
                info.processedRecords(), info.totalRecords(),
                percentage,
                numEpochs);
        progressBar.setValue(percentage);
    }

    /**
     * Starts the training
     */
    protected void run() throws Exception {
        // Create the application context
        createContext();

        learnPanel.setActionAlphas(agent.alphas());
        learnPanel.setEta(agent.eta());

        // Creates the event flow
        createFlow();

        Thread hook = new Thread(this::onShutdown);
        Runtime.getRuntime().addShutdownHook(hook);

        // Create the windows
        if (args.getBoolean("windows")) {
            createFrames();
        } else {
            createSingleFrames();
        }
        allFrames.forEach(f -> {
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        });

        // Creates and start the batch
        Completable batchCompleted = Completable.fromAction(this::runBatch)
                .subscribeOn(Schedulers.computation())
                .doOnError(ex -> {
                    logger.atError().setCause(ex).log("Error running batch");
                    info("Error running batch %s", ex.getMessage());
                })
                .doOnComplete(() ->
                        allFrames.forEach(Window::dispose));
        batchCompleted.subscribe();
    }

    /**
     * Runs the batch
     *
     * @throws Exception in case of error
     */
    private void runBatch() throws Exception {
        // Creates input datasets
        signalsGenerator.generate();

        // Prepares for training
        progressBar.setValue(0);
        info("Preparing for training ...");
        trainer.validate(new File(this.args.getString("temp")));

        // Runs the training session
        progressBar.setValue(0);
        info("Training ...");
        trainer.train();
        info("Training completed.");
    }
}
