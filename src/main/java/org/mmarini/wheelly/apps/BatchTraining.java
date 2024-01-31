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
import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.Observable;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.BatchTrainer;
import org.mmarini.rl.agents.TDAgent;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.yaml.Locator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;

import static java.lang.String.format;
import static org.mmarini.wheelly.swing.Utils.createFrame;
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

    protected final JFrame frame;
    protected Namespace args;
    private RobotEnvironment environment;

    /**
     *
     */
    public BatchTraining() {
        this.frame = createFrame(Messages.getString("Wheelly.title"), new JPanel());
        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE)
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_OPENED)
                .doOnNext(this::handleWindowOpened)
                .subscribe();
        Observable.mergeArray(
                        SwingObservable.window(frame, SwingObservable.WINDOW_ACTIVE))
                .filter(ev -> ev.getID() == WindowEvent.WINDOW_CLOSING)
                .doOnNext(this::handleWindowClosing)
                .subscribe();
    }

    private void handleWindowClosing(WindowEvent windowEvent) {
        environment.shutdown();
        frame.dispose();
    }

    /**
     * Handles the windows opened
     * Initializes the agent
     *
     * @param e the event
     */
    private void handleWindowOpened(WindowEvent e) {
    }

    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            logger.atInfo().log("Creating environment ...");
            JsonNode config = fromFile(this.args.getString("config"));
            this.environment = AppYaml.envNullControllerFromJson(config, Locator.root(), BATCH_SCHEMA_YML);
            Locator agentLocator = Locator.locate(Locator.locate("agent").getNode(config).asText());
            if (agentLocator.getNode(config).isMissingNode()) {
                throw new IllegalArgumentException(format("Missing node %s", agentLocator));
            }
            logger.atInfo().log("Creating agent ...");
            Agent agent = Agent.fromConfig(config, agentLocator, environment);
            int numTrainIterations1 = Locator.locate("numTrainIterations1").getNode(config).asInt();
            int numTrainIterations2 = Locator.locate("numTrainIterations2").getNode(config).asInt();
            BatchTrainer trainer = BatchTrainer.create((TDAgent) agent,
                    numTrainIterations1,
                    numTrainIterations2);
            logger.atInfo().log("Preparing for train ...");
//            frame.setVisible(true);
            trainer.prepare(new File(this.args.getString("dataset")));
            logger.atInfo().log("Training ...");
            for (int i = 0; i < 10; i++) {
                trainer.train();
            }
            logger.atInfo().log("Completed.");
            frame.dispose();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("IO exception");
            System.exit(1);
        }
    }
}
