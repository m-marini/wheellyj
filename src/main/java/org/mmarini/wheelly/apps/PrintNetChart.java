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
import org.mmarini.rl.agents.TDAgent;
import org.mmarini.rl.envs.SignalSpec;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.rl.nets.*;
import org.mmarini.wheelly.apis.RobotApi;
import org.mmarini.wheelly.apis.RobotControllerApi;
import org.mmarini.wheelly.envs.RobotEnvironment;
import org.mmarini.wheelly.swing.Messages;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mmarini.yaml.schema.Validator.*;

/**
 * Run a test to check for robot environment with random behavior agent
 */
public class PrintNetChart {
    private static final Logger logger = LoggerFactory.getLogger(PrintNetChart.class);
    private static final Validator BASE_CONFIG = objectPropertiesRequired(Map.of(
            "version", string(values("0.4")),
            "active", string(),
            "configurations", object()
    ), List.of("version", "active", "configurations"));


    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(PrintNetChart.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Run a session of interaction between robot and environment.");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("-r", "--robot")
                .setDefault("robot.yml")
                .help("specify robot yaml configuration file");
        parser.addArgument("-c", "--controller")
                .setDefault("controller.yml")
                .help("specify controller yaml configuration file");
        parser.addArgument("-e", "--env")
                .setDefault("env.yml")
                .help("specify environment yaml configuration file");
        parser.addArgument("-a", "--agent")
                .setDefault("agent.yml")
                .help("specify agent yaml configuration file");
        parser.addArgument("-o", "--output")
                .setDefault("output.md")
                .help("specify markdown output file");
        return parser;
    }

    /**
     * Returns an object instance from configuration file
     *
     * @param <T>        the returned object class
     * @param file       the filename
     * @param args       the builder additional arguments
     * @param argClasses the builder additional argument classes
     */
    public static <T> T fromConfig(String file, Object[] args, Class<?>[] argClasses) {
        try {
            JsonNode config = Utils.fromFile(file);
            BASE_CONFIG.apply(Locator.root()).accept(config);
            String active = Locator.locate("active").getNode(config).asText();
            Locator baseLocator = Locator.locate("configurations").path(active);
            return Utils.createObject(config, baseLocator, args, argClasses);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
     * Returns the agent
     *
     * @param env the environment
     */
    protected Agent createAgent(WithSignalsSpec env) {
        return fromConfig(args.getString("agent"), new Object[]{env}, new Class[]{WithSignalsSpec.class});
    }

    /**
     * Returns the environment
     */
    protected RobotEnvironment createEnvironment() {
        RobotApi robot = fromConfig(args.getString("robot"), new Object[0], new Class[0]);
        RobotControllerApi controller = fromConfig(args.getString("controller"), new Object[]{robot}, new Class[]{RobotApi.class});
        return fromConfig(args.getString("env"), new Object[]{controller}, new Class[]{RobotControllerApi.class});
    }

    private void printAgent() {
        TDAgent ag = (TDAgent) this.agent;
        output.println("## Critic");
        output.println();
        Map<String, SignalSpec> state = ag.getProcessor().getSpec();
        printNet(ag.getCritic(), state);
        output.println();
        output.println("## Policy");
        output.println();
        printNet(ag.getPolicy(), state);
    }

    private void printNet(TDNetwork network, Map<String, SignalSpec> state) {
        new NetworkPrinter(output, network, state).print();
    }

    protected void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
            logger.atInfo().log("Creating environment");
            RobotEnvironment environment = createEnvironment();

            logger.atInfo().log("Creating agent");
            this.agent = createAgent(environment);
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
            labels.addAll(network.getLayers().keySet());
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
            TDLayer layer = network.getLayers().get(name);
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
