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
import org.jetbrains.annotations.NotNull;
import org.mmarini.Tuple2;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.TDAgentSingleNN;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.nets.*;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mmarini.yaml.Utils.fromFile;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class PrintNetChart {
    private static final Logger logger = LoggerFactory.getLogger(PrintNetChart.class);

    static {
        Nd4j.zeros(1);
    }

    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(PrintNetChart.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-c", "--config")
                .setDefault("print-net.yml")
                .help("specify controller yaml configuration file");
        parser.addArgument("-o", "--output")
                .setDefault("output.md")
                .help("specify markdown output file");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new PrintNetChart().start(args);
    }

    protected Namespace args;
    private Agent agent;
    private PrintWriter output;

    /**
     *
     */
    public PrintNetChart() {
    }

    /**
     * Prints the agent
     */
    private void printAgent() {
        TDAgentSingleNN ag = (TDAgentSingleNN) this.agent;
        output.println("## network");
        output.println();
        Map<String, SignalSpec> state = ag.processor().getSpec();
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
            logger.atInfo().log("Creating environment");
            JsonNode config = fromFile(this.args.getString("config"));
            RobotEnvironment environment = AppYaml.envFromJson(config, Locator.root(), Wheelly.WHEELLY_SCHEMA_YML);

            logger.atInfo().log("Creating agent");
            this.agent = Agent.fromConfig(config, Locator.locate("agent"), environment);
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

    static class NetworkPrinter {
        final PrintWriter output;
        final TDNetwork network;
        final Map<String, String> dictionary;
        final Map<String, SignalSpec> state;

        NetworkPrinter(PrintWriter output, TDNetwork network, Map<String, SignalSpec> state) {
            this.output = output;
            this.network = network;
            this.state = state;
            Set<String> labels = new HashSet<>(network.getSourceLabels());
            labels.addAll(network.layers().keySet());
            this.dictionary = org.mmarini.Utils.zipWithIndex(List.copyOf(labels))
                    .map(t -> Tuple2.of(t._2, "L" + t._1))
                    .collect(Tuple2.toMap());
        }

        void print() {
            output.println("```mermaid");
            output.println("graph TB");
            dictionary.keySet().forEach(this::printLayer);

            network.getInputs().forEach((layer, inputs) -> {
                String gLayer = dictionary.get(layer);
                for (String input : inputs) {
                    String gInput = dictionary.get(input);
                    output.printf("%s --> %s", gInput, gLayer);
                    output.println();
                }
            });

            output.println("```");
        }

        private void printLayer(String name) {
            output.print(dictionary.get(name));
            output.print("[\"");
            output.print(name);
            TDLayer layer = network.layers().get(name);
            if (layer != null) {
                if (layer instanceof TDDense) {
                    output.printf("\\nDense(%d)",
                            ((TDDense) layer).getEb().size(1));
                } else if (layer instanceof TDConcat) {
                    output.print("\\nConcat");
                } else if (layer instanceof TDSum) {
                    output.print("\\nSum");
                } else if (layer instanceof TDTanh) {
                    output.print("\\nTanh");
                } else if (layer instanceof TDRelu) {
                    output.print("\\nRelu");
                } else if (layer instanceof TDLinear) {
                    output.printf("\\nLin(%.3f, %.3f)",
                            ((TDLinear) layer).getB(),
                            ((TDLinear) layer).getW());
                } else if (layer instanceof TDSoftmax) {
                    output.printf("\\nSoftmax(%.3f)",
                            ((TDSoftmax) layer).getTemperature());
                } else if (layer instanceof TDDropOut) {
                    output.print("\\nDropout");
                }
                float dropOut = layer.getDropOut();
                if (dropOut != 1F) {
                    output.printf("\\ndropOut=%.3f", dropOut);
                }
            } else {
                SignalSpec spec = state.get(name);
                output.printf("\\nInput(%d)", spec.getSize());
            }
            output.println("\"]");
        }
    }
}
