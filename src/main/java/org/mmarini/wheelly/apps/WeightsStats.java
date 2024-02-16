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

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.jetbrains.annotations.NotNull;
import org.mmarini.rl.agents.TDAgentSingleNN;
import org.mmarini.wheelly.swing.Messages;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Runs the dump of weights statistic
 */
public class WeightsStats {
    private static final Logger logger = LoggerFactory.getLogger(WeightsStats.class);

    static {
        Nd4j.zeros(1);
    }

    @NotNull
    private static ArgumentParser createParser() {
        ArgumentParser parser = ArgumentParsers.newFor(WeightsStats.class.getName()).build()
                .defaultHelp(true)
                .version(Messages.getString("Wheelly.title"))
                .description("Dump the weights statistic");
        parser.addArgument("-v", "--version")
                .action(Arguments.version())
                .help("show current version");
        parser.addArgument("modelPath")
                .help("specify the model path");
        return parser;
    }

    /**
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new WeightsStats().start(args);
    }

    protected Namespace args;

    private void start(String[] args) {
        ArgumentParser parser = createParser();
        try {
            this.args = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        String modelPath = this.args.getString("modelPath");
        Random random = Nd4j.getRandom();
        try {
            TDAgentSingleNN agent = TDAgentSingleNN.load(new File(modelPath), Integer.MAX_VALUE, random);
            Stats criticWeightStats = new Stats();
            Stats criticBiasStats = new Stats();
            Stats policyWeightStats = new Stats();
            Stats policyBiasStats = new Stats();
            for (Map.Entry<String, INDArray> entry : agent.props().entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("policy.")) {
                    if (key.endsWith(".w")) {
                        policyWeightStats.update(entry.getValue());
                    } else if (key.endsWith(".b")) {
                        policyBiasStats.update(entry.getValue());
                    }
                } else if (key.startsWith("critic.")) {
                    if (key.endsWith(".w")) {
                        criticWeightStats.update(entry.getValue());
                    } else if (key.endsWith(".b")) {
                        criticBiasStats.update(entry.getValue());
                    }
                }
            }
            System.out.println();
            System.out.printf("Model: %s%n", modelPath);
            System.out.println();
            System.out.println("Policy");
            System.out.printf("  %d weights%n", policyWeightStats.count);
            System.out.printf("  Min value: %g%n", policyWeightStats.min);
            System.out.printf("  Max value: %g%n", policyWeightStats.max);
            System.out.printf("  %d bias%n", policyBiasStats.count);
            System.out.printf("  Min value: %g%n", policyBiasStats.min);
            System.out.printf("  Max value: %g%n", policyBiasStats.max);
            System.out.println();
            System.out.println("Critic");
            System.out.printf("  %d weights%n", criticWeightStats.count);
            System.out.printf("  Min value: %g%n", criticWeightStats.min);
            System.out.printf("  Max value: %g%n", criticWeightStats.max);
            System.out.printf("  %d bias%n", criticBiasStats.count);
            System.out.printf("  Min value: %g%n", criticBiasStats.min);
            System.out.printf("  Max value: %g%n", criticBiasStats.max);
            System.out.println();

        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    static class Stats {
        public float min;
        public float max;
        public int count;

        public Stats() {
            this(Float.MAX_VALUE, -Float.MAX_VALUE, 0);
        }

        public Stats(float min, float max, int count) {
            this.min = min;
            this.max = max;
            this.count = count;
        }

        public void update(INDArray values) {
            INDArray v = Nd4j.toFlattened(values);
            count += (int) v.length();
            max = Math.max(max, v.maxNumber().floatValue());
            min = Math.min(min, v.minNumber().floatValue());
        }
    }
}
