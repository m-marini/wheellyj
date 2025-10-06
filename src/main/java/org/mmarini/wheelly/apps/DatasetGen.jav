/*
 * Copyright (c) 2025 Marco Marini, marco.marini@mmarini.org
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

import com.fasterxml.jackson.databind.JsonNode;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.Signal;
import org.mmarini.wheelly.apis.InferenceFileReader;
import org.mmarini.wheelly.apis.RobotCommands;
import org.mmarini.wheelly.apis.WorldModel;
import org.mmarini.wheelly.envs.WorldEnvironment;
import org.mmarini.swing.Messages;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class DatasetGen {
    private static final Logger logger = LoggerFactory.getLogger(DatasetGen.class);

    static {
        try (INDArray ignored = Nd4j.zeros(1)) {
        }
    }

    /**
     * Returns the argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(BatchTraining.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Generates the training datasets.");
        parser.addArgument("-c", "--config")
                .setDefault("batch.yml")
                .help("specify yaml configuration file");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("inferenceFile")
                .required(true)
                .help("specify inference file");
        return parser;
    }

    /**
     * Application entry point
     *
     * @param args the command arguments
     */
    public static void main(String[] args) {
        ArgumentParser parser = createParser();
        try {
            new DatasetGen(parser.parseArgs(args)).run();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (Throwable e) {
            logger.atError().setCause(e).log("Exception");
            System.exit(1);
        }
    }

    private final Namespace args;
    private InferenceFileReader inferenceFile;
    private WorldEnvironment worldEnvironment;

    /**
     * Creates the application
     *
     * @param args the parsed command line arguments
     */
    public DatasetGen(Namespace args) {
        this.args = requireNonNull(args);
    }

    /**
     * Returns the signal map
     *
     * @param worldModel the world model
     */
    private Map<String, Signal> generateInputs(WorldModel worldModel) {
        return worldEnvironment.state(worldModel);
    }

    private void generateReward(WorldModel worldModel, RobotCommands robotCommands) {
    }

    /**
     * Runs the application
     */
    private void run() {
        try {
            JsonNode config = org.mmarini.yaml.Utils.fromFile(args.getString("config"));
            this.worldEnvironment = WorldEnvironment.create(config, Locator.root());
            this.inferenceFile = InferenceFileReader.fromFile(new File(args.getString("inferenceFile")));
            Tuple2<WorldModel, RobotCommands> record;
            while ((record = inferenceFile.readRecord()) != null) {
                writeSignals(generateInputs(record._1));
            }
            inferenceFile.close();
        } catch (IOException e) {
            logger.atError().setCause(e).log("Error reading inference file {}", args.getString("inferenceFile"));
        }
    }

    private void writeSignals(Map<String, Signal> signals) {

    }
}
