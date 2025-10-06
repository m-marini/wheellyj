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
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.AgentRL;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.*;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.wheelly.apis.WorldModeller;
import org.mmarini.wheelly.envs.EnvironmentApi;
import org.mmarini.wheelly.envs.RewardFunction;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mmarini.yaml.Utils.fromFile;

/**
 * Application to generate network graph with mermaid syntax
 */
public class PrintNetChart {
    private static final Logger logger = LoggerFactory.getLogger(PrintNetChart.class);

    static {
        Nd4j.zeros(1);
    }

    /**
     * Returns the command line argument parser
     */
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(PrintNetChart.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("wheelly.yml")
                .help("specify controller yaml configuration file");
        parser.addArgument("-o", "--output")
                .setDefault("output.md")
                .help("specify markdown output file");
        return parser;
    }

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new PrintNetChart().start(args);
    }

    protected Namespace args;
    private AgentRL agent;
    private PrintWriter output;

    /**
     * Creates the application
     */
    public PrintNetChart() {
    }

    /**
     * Prints the agent
     */
    private void printAgent() {
        AbstractAgentNN ag = (AbstractAgentNN) this.agent;
        output.println("## Network");
        output.println();
        Map<String, SignalSpec> state = ag.processor().spec();
        printNet(ag.network(), state);
    }

    /**
     * Prints the network
     *
     * @param network the network
     * @param state   the state
     */
    private void printNet(TDNetwork network, Map<String, SignalSpec> state) {
        new NetworkPrinter(output, network, state).print();
    }

    /**
     * Starts the application
     *
     * @param args the arguments
     */
    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            JsonNode config = fromFile(this.args.getString("config"));
            WheellyJsonSchemas.instance().validateOrThrow(config, Wheelly.WHEELLY_SCHEMA_YML);

            logger.atInfo().log("Creating robot ...");
            RobotApi robot = AppYaml.robotFromJson(config);

            logger.atInfo().log("Creating controller ...");
            RobotControllerApi controller = AppYaml.controllerFromJson(config);
            controller.connectRobot(robot);

            logger.atInfo().log("Creating world modeller ...");
            WorldModeller worldModeller = AppYaml.modellerFromJson(config);
            worldModeller.setRobotSpec(robot.robotSpec());
            worldModeller.connectController(controller);

            logger.atInfo().log("Creating RL environment ...");
            EnvironmentApi environment = AppYaml.envFromJson(config);
            environment.connect(worldModeller);

            logger.atInfo().log("Create reward function ...");
            RewardFunction rewardFunc = AppYaml.rewardFromJson(config);
            environment.setRewardFunc(rewardFunc);

            logger.atInfo().log("Creating agent ...");
            Function<WithSignalsSpec, Agent> agentBuilder = Agent.fromFile(
                    new File(Locator.locate("agent").getNode(config).asText()));
            this.agent = (AgentRL) agentBuilder.apply(environment);

            environment.connect(agent);

            /*
            RobotApi robot = AppYaml.robotFromJson(config);

            RobotControllerApi controller = AppYaml.controllerFromJson(config);
            controller.connectRobot(robot);

            WorldModeller modeller = AppYaml.modellerFromJson(config);
            modeller.connectController(controller);

            EnvironmentApi environment = AppYaml.envFromJson(config);
            environment.connect(modeller);

            logger.atInfo().log("Creating agent");
            Function<WithSignalsSpec, Agent> builder = Agent.fromFile(new File(config.path("agent").asText()));
            this.agent = builder.apply(environment);
            environment.connect(agent);
*/
            String outputFilename = this.args.getString("output");
            logger.atInfo().log("Creating {}", outputFilename);
            try {
                this.output = new PrintWriter(new FileWriter(outputFilename));
            } catch (IOException e) {
                logger.atError().addArgument(outputFilename).setCause(e).log("Error writing {}");
                System.exit(1);
            }
            printAgent();
            output.close();

        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        } catch (IOException e) {
            logger.atError().setCause(e).log("IO Error");
            System.exit(1);
        }
    }

    /**
     * Prints the network
     */
    static class NetworkPrinter {
        final PrintWriter output;
        final TDNetwork network;
        final Map<String, String> dictionary;
        final Map<String, SignalSpec> state;
        private final List<String> labels;

        /**
         * Creates the network printer
         *
         * @param output  the output writer
         * @param network the network
         * @param state   the input state specification
         */
        NetworkPrinter(PrintWriter output, TDNetwork network, Map<String, SignalSpec> state) {
            this.output = output;
            this.network = network;
            this.state = state;
            // Sorts the layer in forward order
            this.labels = new ArrayList<>(network.sourceLayers().stream().sorted().toList());
            labels.addAll(network.forwardSequence());
            // Create the dictionary to map the layer to graph layer node id
            this.dictionary = org.mmarini.Utils.zipWithIndex(List.copyOf(labels))
                    .map(t -> Tuple2.of(t._2, "L" + t._1))
                    .collect(Tuple2.toMap());
        }

        /**
         * Prints the network
         */
        void print() {
            // Prints the preamble
            output.println("```mermaid");
            output.println("graph TB");
            // Prints the layer node
            labels.forEach(this::printLayer);
            // Print the node connections
            for (TDLayer layer : network.layers().values()) {
                String layerId = dictionary.get(layer.name());
                for (String input : layer.inputs()) {
                    String inputId = dictionary.get(input);
                    output.printf("%s --> %s", inputId, layerId);
                    output.println();
                }
            }
            // Prints the epilogue
            output.println("```");
        }

        /**
         * Print the layer graph node
         *
         * @param name the layer id
         */
        private void printLayer(String name) {
            output.print(dictionary.get(name));
            output.print("[\"`");
            output.print(name);
            TDLayer layer = network.layers().get(name);
            switch (layer) {
                case TDDense tdDense2 -> {
                    output.printf("\nDense(%d)",
                            network.size(name));
                    float dropOut = tdDense2.dropOut();
                    if (dropOut != 1F) {
                        output.printf("\ndropOut=%.3f", dropOut);
                    }
                }
                case TDConcat ignored -> output.print("\nConcat");
                case TDSum ignored -> output.print("\nSum");
                case TDTanh ignored -> output.print("\nTanh");
                case TDRelu ignored -> output.print("\nRelu");
                case TDLinear tdLinear2 -> output.printf("\nLin(%.3f, %.3f)",
                        tdLinear2.bias(),
                        tdLinear2.weight());
                case TDSoftmax tdSoftmax2 -> output.printf("\nSoftmax(%.3f)",
                        tdSoftmax2.temperature());
                case TDDropOut tdDropout -> output.printf("\nDropout(%.3f)", tdDropout.dropOut());
                case null -> {
                    SignalSpec spec = state.get(name);
                    output.printf("\nInput(%d)", spec.size());
                }
                default -> {
                }
            }
            output.println("`\"]");
        }
    }
}
