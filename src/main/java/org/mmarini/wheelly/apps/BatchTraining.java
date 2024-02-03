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
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.rl.agents.BatchTrainer;
import org.mmarini.rl.agents.Serde;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.yaml.Locator;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class BatchTraining {
    public static final String BATCH_SCHEMA_YML = "https://mmarini.org/wheelly/batch-schema-0.1";
    private static final Logger logger = LoggerFactory.getLogger(BatchTraining.class);

    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(BatchTraining.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of batch training.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("batch.yml")
                .help("specify yaml configuration file");
        parser.addArgument("dataset")
                .required(true)
                .help("specify dataset path");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new BatchTraining().start(args);
    }

    protected Namespace args;
    private String pathFile;
    private BatchTrainer trainer;

    /**
     * Returns the network from agent path
     *
     * @param random the ranom number generator
     * @throws IOException in case of error
     */
    private TDNetwork loadNetwork(Random random) throws IOException {
        JsonNode spec = Utils.fromFile(new File(pathFile, "agent.yml"));
        File file = new File(pathFile, "agent.bin");
        Map<String, INDArray> props = Serde.deserialize(file);
        String backupFileName = format("agent-%1$tY%1$tm%1$td-%1$tH%1$tM%1$tS.bin", Calendar.getInstance());
        file.renameTo(new File(file.getParentFile(), backupFileName));
        logger.atInfo().log("Backup at {}", backupFileName);
        Locator locator = Locator.root();
        return TDNetwork.create(spec, locator.path("network"), "network", props, random);
    }

    /**
     * Saves the network
     *
     * @param network the network
     */
    private void saveNetwork(TDNetwork network) {
        logger.atInfo().log("Saving network ...");
        Map<String, INDArray> props = new HashMap<>(network.getProps("network"));
        props.put("avgReward", Nd4j.createFromArray(trainer.avgReward()));
        try {
            File file = new File(pathFile, "agent.bin");
            Serde.serizalize(file, props);
            logger.atInfo().log("Saved network in {}", file.getCanonicalPath());
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error saving network");
        }
    }

    /**
     * Starts the training
     *
     * @param args the command line arguments
     */
    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            // Parses the arguments
            this.args = parser.parseArgs(args);

            // Load configuration
            JsonNode config = fromFile(this.args.getString("config"));
            JsonSchemas.instance().validateOrThrow(config, BATCH_SCHEMA_YML);

            // Creates random number generator
            Random random = Nd4j.getRandom();
            long seed = Locator.locate("seed").getNode(config).asLong(0);
            if (seed > 0) {
                random.setSeed(seed);
            }

            // Loads network
            logger.atInfo().log("Load network ...");
            this.pathFile = Locator.locate("modelPath").getNode(config).asText();
            TDNetwork network = loadNetwork(random);

            // Create the batch trainer
            int numTrainIterations1 = Locator.locate("numTrainIterations1").getNode(config).asInt();
            int numTrainIterations2 = Locator.locate("numTrainIterations2").getNode(config).asInt();
            float learningRate = (float) Locator.locate("learningRate").getNode(config).asDouble();
            long batchSize = Locator.locate("batchSize").getNode(config).asLong(Long.MAX_VALUE);
            this.trainer = BatchTrainer.create(network,
                    learningRate,
                    0,
                    numTrainIterations1,
                    numTrainIterations2,
                    batchSize,
                    random,
                    this::saveNetwork);

            // Preapare for training
            logger.atInfo().log("Preparing for training ...");
            trainer.prepare(new File(this.args.getString("dataset")));

            // Runs the training session
            logger.atInfo().log("Training ...");
            trainer.train();

            logger.atInfo().log("Completed.");

        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }
}
