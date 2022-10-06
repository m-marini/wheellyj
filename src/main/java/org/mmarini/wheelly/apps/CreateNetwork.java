/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
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
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.constraint.MinMaxNormConstraint;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.mmarini.Tuple2;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.IUpdater;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static org.mmarini.wheelly.engines.deepl.Yaml.network;
import static org.mmarini.yaml.Utils.fromFile;

public class CreateNetwork {
    private static final Logger logger = LoggerFactory.getLogger(CreateNetwork.class);

    static {
        Nd4j.zeros(1);
    }

    private static Activation createActivation(String name) {
        switch (name) {
            case "SOFTPLUS":
                return Activation.SOFTPLUS;
            case "RELU":
                return Activation.RELU;
            case "TANH":
                return Activation.TANH;
            case "HARDTANH":
                return Activation.HARDTANH;
            case "SIGMOID":
                return Activation.SIGMOID;
            case "HARDSIGMOID":
                return Activation.HARDSIGMOID;
            default:
                throw new IllegalArgumentException(format("Wrong activation %s", name));
        }
    }

    static Map<String, String[]> createInputNamesByName(int[][] shortcuts, int noHiddens, int noOutputs) {
        Map<String, Set<String>> result = new HashMap<>();
        // Set output layers
        for (int i = 0; i < noOutputs; i++) {
            result.put("O" + i, new HashSet<>(Set.of("L" + noHiddens)));
        }
        // Set hidden layers
        for (int i = 0; i < noHiddens; i++) {
            result.put("L" + (i + 1), new HashSet<>(Set.of("L" + i)));
        }
        for (int[] shortcut : shortcuts) {
            String from = "L" + shortcut[0];
            int n = shortcut[1];
            if (n >= noHiddens) {
                // To output layers
                for (int i = 0; i < noOutputs; i++) {
                    String to = "O" + i;
                    result.compute(to,
                            (key, oldValue) -> {
                                if (oldValue == null) {
                                    return new HashSet<>(Set.of(from));
                                } else {
                                    oldValue.add(from);
                                    return oldValue;
                                }
                            });
                }
            } else {
                String to = "L" + n;
                result.compute(to,
                        (key, oldValue) -> {
                            if (oldValue == null) {
                                return new HashSet<>(Set.of(from));
                            } else {
                                oldValue.add(from);
                                return oldValue;
                            }
                        });
            }
        }
        return result.entrySet().stream()
                .map(e -> {
                    Set<String> value = e.getValue();
                    String[] newValue = value.stream().sorted().toArray(String[]::new);
                    return Map.entry(e.getKey(), newValue);
                })
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue));
    }

    static Map<String, Integer> createNumInputsByName(Map<String, Integer> noNodes, Map<String, String[]> layerInputs) {

        return Tuple2.stream(layerInputs)
                .map(t -> {
                    String[] inputs = t._2;
                    int n = Arrays.stream(inputs).mapToInt(noNodes::get).sum();
                    return t.setV2(n);
                })
                .collect(Tuple2.toMap());
    }

    static Map<String, Integer> createNumNodesByName(int noInputs, int[] noHiddens, int[] noOutputs) {
        Map<String, Integer> result = new HashMap<>();
        result.put("L0", noInputs);
        for (int i = 0; i < noHiddens.length; i++) {
            result.put("L" + (i + 1), noHiddens[i]);
        }
        for (int i = 0; i < noOutputs.length; i++) {
            result.put("O" + i, noOutputs[i]);
        }
        return result;
    }

    private static IUpdater createUpdater(String name, double learningRate) {
        switch (name) {
            case "Sgd":
                return new Sgd(learningRate);
        }
        throw new IllegalArgumentException(format("Wrong updater %s", name));
    }

    public static void main(String[] args) throws IOException {
        logger.info("Create network");
        if (args.length < 1) {
            throw new IllegalArgumentException("Missing configuration file");
        }
        new CreateNetwork(args[0]).run();
        logger.info("Completed.");
    }

    private final String file;


    protected CreateNetwork(String file) {
        this.file = file;
    }

    private void run() throws IOException {
        logger.info("Loading config {} ...", file);
        JsonNode root = fromFile(file);
        network().apply(Locator.root()).accept(root);

        double learningRate = Locator.locate("learningRate").getNode(root).asDouble();
        double maxAbsParams = Locator.locate("maxAbsParameters").getNode(root).asDouble();
        double maxAbsGradient = Locator.locate("maxAbsGradient").getNode(root).asDouble();
        double dropOut = Locator.locate("dropOut").getNode(root).asDouble();
        int noInputs = Locator.locate("numInputs").getNode(root).asInt();
        IUpdater updater = createUpdater(Locator.locate("updater").getNode(root).asText(), learningRate);
        int[] noHiddens = Locator.locate("numHiddens").elements(root)
                .map(l -> l.getNode(root))
                .mapToInt(JsonNode::asInt)
                .toArray();
        int[] noOutputs = Locator.locate("numOutputs").elements(root)
                .map(l -> l.getNode(root))
                .mapToInt(JsonNode::asInt)
                .toArray();
        Activation activation = createActivation(Locator.locate("activation").getNode(root).asText());
        int[][] shortcuts = Locator.locate("shortcuts").elements(root)
                .map(loc ->
                        loc.elements(root)
                                .map(l -> l.getNode(root))
                                .mapToInt(JsonNode::asInt)
                                .toArray())
                .toArray(int[][]::new);

        ComputationGraphConfiguration.GraphBuilder builder = new NeuralNetConfiguration.Builder().
                weightInit(WeightInit.XAVIER).
                updater(updater).
                optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).
                constrainAllParameters(new MinMaxNormConstraint(-maxAbsParams, maxAbsParams, 1)).
                gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue).
                gradientNormalizationThreshold(maxAbsGradient).
                graphBuilder().
                addInputs("L0");

        Map<String, Integer> noNodes = createNumNodesByName(noInputs, noHiddens, noOutputs);
        Map<String, String[]> layerInputs = createInputNamesByName(shortcuts, noHiddens.length, noOutputs.length);
        Map<String, Integer> noLayerInputs = createNumInputsByName(noNodes, layerInputs);

        // add hidden layer
        for (int i = 0; i < noHiddens.length; i++) {
            String name = "L" + (i + 1);
            DenseLayer layer = new DenseLayer.Builder()
                    .nIn(noLayerInputs.get(name))
                    .nOut(noNodes.get(name))
                    .activation(activation)
                    .dropOut(dropOut)
                    .build();
            builder.addLayer(name, layer, layerInputs.get(name));
        }

        // Add output layers
        for (int i = 0; i < noOutputs.length; i++) {
            String name = "O" + i;
            OutputLayer layer = new OutputLayer.Builder()
                    .nIn(noLayerInputs.get(name))
                    .nOut(noNodes.get(name))
                    .lossFunction(LossFunctions.LossFunction.MSE)
                    .activation(Activation.TANH)
                    .build();
            builder.addLayer(name, layer, layerInputs.get(name));
        }

        String[] outNames = IntStream.range(0, noOutputs.length).mapToObj(i -> "O" + i).toArray(String[]::new);
        builder = builder.setOutputs(outNames);
        ComputationGraph net = new ComputationGraph(builder.build());
        net.init();
        File outFile = new File(Locator.locate("file").getNode(root).asText());
        logger.info("Writing model {} ...", outFile);
        ModelSerializer.writeModel(net, outFile, false);
    }
}