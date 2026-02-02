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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.deeplearning4j.core.storage.StatsStorage;
import org.deeplearning4j.ui.api.UIServer;
import org.deeplearning4j.ui.model.stats.StatsListener;
import org.deeplearning4j.ui.model.storage.InMemoryStatsStorage;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.*;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.batch.BatchTrainer;
import org.mmarini.wheelly.batch.ProgressInfo;
import org.mmarini.wheelly.envs.DLEnvironment;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.wheelly.swing.KpisPanel;
import org.mmarini.wheelly.swing.Utils;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.layHorizontally;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run the batch training of agent from kpis files
 */
public class BatchTraining {
    public static final int PROGRESS_INTERVAL = 1000;
    public static final String STAT_PAGE = "http://localhost:9000/train/overview";
    private static final String BATCH_SCHEMA_YML = "https://mmarini.org/wheelly/batch-schema-0.4";
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
        parser.addArgument("-u", "--ui")
                .action(Arguments.storeTrue())
                .help("open performance page");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("path")
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
    private final JProgressBar progressBar;
    private final JTextField infoBar;
    private BatchTrainer trainer;
    private JFrame frame;
    private final KpisPanel kpisPanel;
    private int numEpochs;
    private BatchAgent agent;
    private DLEnvironment environment;
    private StatsListener statsListener;

    /**
     * Creates the application
     *
     * @param args the parsed command line arguments
     */
    public BatchTraining(Namespace args) {
        this.args = requireNonNull(args);
        this.infoBar = new JTextField();
        this.progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
        this.kpisPanel = new KpisPanel();
        initUI();
    }

    /**
     * Returns the content
     */
    private Component createContent() {
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        content.add(infoBar, BorderLayout.NORTH);
        content.add(new JScrollPane(kpisPanel), BorderLayout.CENTER);
        content.add(progressBar, BorderLayout.SOUTH);
        return content;
    }

    /**
     * Creates the application context
     *
     * @throws IOException in case of error
     */
    private void createContext() throws Throwable {
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
        if (env instanceof DLEnvironment we) {
            this.environment = we;
        } else {
            throw new IllegalArgumentException(format(
                    "Environment must be %s (%s)",
                    DLEnvironment.class.getSimpleName(),
                    env.getClass().getSimpleName()
            ));
        }
        environment.connect(modeller);
        // Load reward function
        RewardFunction rewardFunc = AppYaml.rewardFromJson(config);
        environment.setRewardFunc(rewardFunc);

        // Creates agent

        logger.atInfo().log("Creating agent ...");
        Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        this.agent = (BatchAgent) agentBuilder.apply(environment);
        environment.connect(agent);

        kpisPanel.addActionColumns(environment.actionSpec()
                .keySet().stream().sorted().toArray(String[]::new));

        // Creates random number generator
        Random random = Nd4j.getRandomFactory().getNewRandomInstance();
        long seed = Locator.locate("seed").getNode(config).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }

        numEpochs = Locator.locate("numEpochs").getNode(config).asInt(agent.numEpochs());
        if (agent instanceof DLAgent dlAgent) {
            if (args.getBoolean("ui")) {
                UIServer uiServer = UIServer.getInstance();
                StatsStorage statsStorage = new InMemoryStatsStorage();
                uiServer.attach(statsStorage);
                this.statsListener = new StatsListener(statsStorage);
                dlAgent.network().setListeners(statsListener);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    try {
                        Desktop.getDesktop().browse(new URI(STAT_PAGE));
                    } catch (URISyntaxException e) {
                        logger.atError().setCause(e).log("Error opening stats page");
                    }
                }
            }
            dlAgent.readKpis()
                    .observeOn(Schedulers.computation())
                    .subscribe(this::onKpis);
        }
    }

    /**
     * Creates multi frames
     */
    private void createFrame() {
        frame = Utils.createFrame(Messages.getString("BatchTraining.title"),
                createContent());
        layHorizontally(frame);
    }

    /**
     * Returns the validated configuration
     */
    private JsonNode loadConfiguration() throws IOException {
        File configFile = new File(this.args.getString("config"));
        JsonNode config = fromFile(configFile);
        WheellyJsonSchemas.instance().validateOrThrow(config, BATCH_SCHEMA_YML);
        return config;
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
     * Handles the batch completion
     */
    private void onBatchCompletion() {
        logger.atInfo().log("Batch completed");
        frame.dispose();
        System.exit(0);
    }

    /**
     * Handles the batch error
     *
     * @param ex the error
     */
    private void onBatchError(Throwable ex) {
        logger.atError().setCause(ex).log("Error running batch");
        info("Error running batch %s", ex.getMessage());
    }

    /**
     * Handles the kpis
     *
     * @param kpis the kpis
     */
    private void onKpis(TrainingKpis kpis) {
        try {
            kpisPanel.print(kpis);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Error on print kpis");
        }
    }

    /**
     * Handles the progress info event
     *
     * @param progressInfo the event
     */
    private void onProgress(ProgressInfo progressInfo) {
        int max = progressInfo.maximumValue();
        if (max > 0) {
            int percentage = progressInfo.progress() * 100 / max;
            info(Messages.getString("BatchTraining.progressInfoWithPerc.text"),
                    progressInfo.message(),
                    progressInfo.progress(),
                    progressInfo.maximumValue(),
                    percentage);
            progressBar.setValue(percentage);
        } else {
            info(Messages.getString("BatchTraining.progressInfo.text"),
                    progressInfo.message(),
                    progressInfo.progress());
            progressBar.setValue(0);
        }
    }

    /**
     * Handles window close event
     *
     * @param e the event
     */
    private void onWindowClose(WindowEvent e) {
        logger.atInfo().log("Window closed");
        trainer.stop();
    }

    /**
     * Starts the training
     */
    protected void run() throws Throwable {
        // Create the application context
        createContext();

        // Create the windows
        createFrame();
        //f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        frame.setVisible(true);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClose(e);
            }
        });

        // Creates and start the batch
        Completable.fromAction(this::runBatch)
                .subscribeOn(Schedulers.computation())
                .subscribe(this::onBatchCompletion,
                        this::onBatchError);
        logger.atInfo().log("Run completed");
    }

    /**
     * Runs the batch
     *
     * @throws Exception in case of error
     */
    private void runBatch() throws Exception {

        // Creates the batch trainer
        File path = new File(args.getString("path"));
        Map<String, BinArrayFile> stateFiles = environment.stateSpec().keySet()
                .stream()
                .map(key ->
                        Tuple2.of(key, new BinArrayFile(new File(path, key + ".bin"))))
                .collect(Tuple2.toMap());
        Map<String, BinArrayFile> actionMaskFiles = environment.actionSpec().keySet()
                .stream()
                .map(key ->
                        Tuple2.of(key, new BinArrayFile(new File(path, key + ".bin"))))
                .collect(Tuple2.toMap());
        BinArrayFile rewardFile = new BinArrayFile(new File(path, "rewards.bin"));
        long size = rewardFile.size();
        if (agent instanceof DLAgent dlAgent) {
            DLListener listener = new DLListener((int) (size * numEpochs));
            if (statsListener != null) {
                dlAgent.network().setListeners(statsListener, listener);
            } else {
                dlAgent.network().setListeners(listener);
            }
            /*
            listener.readProgressInfo()
                    .observeOn(Schedulers.computation())
                    .throttleLatest(PROGRESS_INTERVAL, TimeUnit.MILLISECONDS)
                    .subscribe(this::onProgress);

             */
        }
        this.trainer = new BatchTrainer(agent, stateFiles, actionMaskFiles, rewardFile);
        trainer.readProgressInfo()
                .observeOn(Schedulers.computation())
                .throttleLatest(PROGRESS_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribe(this::onProgress);
        // Runs the training session
        progressBar.setValue(0);
        info("Training ...");
        trainer.train(numEpochs);
        info("Training completed.");
    }
}
