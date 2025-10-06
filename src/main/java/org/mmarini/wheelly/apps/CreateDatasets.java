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
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.*;
import org.mmarini.wheelly.batch.DatasetBuilder;
import org.mmarini.wheelly.batch.ProgressInfo;
import org.mmarini.wheelly.envs.DLEnvironment;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.swing.Utils.center;
import static org.mmarini.wheelly.swing.Utils.createFrame;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run the batch training of agent from kpis files
 */
public class CreateDatasets {
    public static final int PROGRESS_INTERVAL = 1000;
    private static final String BATCH_SCHEMA_YML = "https://mmarini.org/wheelly/batch-schema-0.4";
    private static final Logger logger = LoggerFactory.getLogger(CreateDatasets.class);

    static {
        try (INDArray ignored = Nd4j.zeros(1)) {
        }
    }

    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(CreateDatasets.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Create datasets from inference file.");
        parser.addArgument("-c", "--config")
                .setDefault("batch.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-t", "--temp")
                .setDefault("tmp")
                .help("specify the working path file");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
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
            new CreateDatasets(parser.parseArgs(args)).run();
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
    private BatchAgent agent;
    private WorldModeller modeller;
    private DLEnvironment environment;
    private JFrame frame;

    /**
     * Creates the application
     *
     * @param args the parsed command line arguments
     */
    public CreateDatasets(Namespace args) {
        this.args = requireNonNull(args);
        this.infoBar = new JTextField();
        this.progressBar = new JProgressBar(JProgressBar.HORIZONTAL);
        initUI();
    }

    /**
     * Returns the content
     */
    private Component createContent() {
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        content.add(infoBar, BorderLayout.CENTER);
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
        this.modeller = AppYaml.modellerFromJson(config);
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
        Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                new File(Locator.locate("agent").getNode(config).asText()));
        agent = (BatchAgent) agentBuilder.apply(environment);
        environment.connect(agent);

        // Creates random number generator
        Random random = Nd4j.getRandomFactory().getNewRandomInstance();
        long seed = Locator.locate("seed").getNode(config).asLong(0);
        if (seed > 0) {
            random.setSeed(seed);
        }
    }

    /**
     * Creates multi frames
     */
    private void createFrames() {
        this.frame = createFrame(Messages.getString("CreateDatasets.title"),
                createContent());
        this.frame.setSize(400, 80);
        center(frame);
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
        WheellyJsonSchemas.instance().validateOrThrow(config, BATCH_SCHEMA_YML);
        return config;
    }

    /**
     * Handles progress messages
     *
     * @param progressInfo progress message
     */
    private void onProgress(ProgressInfo progressInfo) {
        int max = progressInfo.maximumValue();
        if (max > 0) {
            int percentage = progressInfo.progress() * 100 / max;
            info(Messages.getString("CreateDatasets.progressInfoWithPerc.text"),
                    progressInfo.message(),
                    progressInfo.progress(),
                    progressInfo.maximumValue(),
                    percentage);
            progressBar.setValue(percentage);
        } else {
            info(Messages.getString("CreateDatasets.progressInfo.text"),
                    progressInfo.message(),
                    progressInfo.progress());
            progressBar.setValue(0);
        }
    }

    /**
     * Handle shutdown
     */
    private void onShutdown() {
        info("Shutting down ...");
        info("Shutting down completed");
    }

    /**
     * Starts the training
     */
    protected void run() throws Exception {
        // Create the application context
        createContext();

        Thread hook = new Thread(this::onShutdown);
        Runtime.getRuntime().addShutdownHook(hook);

        // Create the windows
        createFrames();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Creates and start the batch
        Completable batchCompleted = Completable.fromAction(this::runBatch)
                .subscribeOn(Schedulers.computation())
                .doOnError(ex -> {
                    logger.atError().setCause(ex).log("Error running batch");
                    info("Error running batch %s", ex.getMessage());
                })
                .doOnComplete(() ->
                        frame.dispose());
        batchCompleted.subscribe();
    }

    /**
     * Runs the batch
     *
     * @throws Exception in case of error
     */
    private void runBatch() throws Exception {
        // Creates the batch trainer
        progressBar.setValue(0);
        InferenceFileReader reader = InferenceFileReader.fromFile(new File(this.args.getString("dataset")));
        DatasetBuilder builder = new DatasetBuilder(
                reader,
                modeller,
                new File(this.args.getString("temp")),
                environment);
        builder.readProgressInfo()
                .subscribeOn(Schedulers.computation())
                .throttleLatest(PROGRESS_INTERVAL, TimeUnit.MILLISECONDS)
                .subscribe(this::onProgress);
        builder.build();
    }
}
