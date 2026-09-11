/*
 * Copyright (c) 2026 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.rl.agents;

import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.BatchNormalization;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.TextTable;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class NNTest {

    public static final long SEED = 12345L;
    public static final double LEARNING_RATE = 1e-1;
    public static final int NUM_EPOCHS = 10;
    public static final int DATA_SIZE = 1000;
    static final Logger logger = LoggerFactory.getLogger(NNTest.class);
    private ComputationGraph network;
    private INDArray[] inputs;
    private INDArray[] labels;
    private INDArray[] states;

    @BeforeEach
    void setUp() {
        // Given a network classifier
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .miniBatch(true)
                .cacheMode(CacheMode.NONE)
                .trainingWorkspaceMode(WorkspaceMode.ENABLED)
                .inferenceWorkspaceMode(WorkspaceMode.ENABLED)
                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                .seed(SEED)
                .updater(new Sgd(LEARNING_RATE))
                .graphBuilder()
                .addInputs("input")
                .setOutputs("output")
                .setInputTypes(InputType.feedForward(2))
                .addLayer("batch",
                        new BatchNormalization.Builder()
                                .build(),
                        "input"
                )
                .addLayer("dense",
                        new DenseLayer.Builder()
                                .nOut(20)
                                .activation(Activation.RELU)
                                .weightInit(WeightInit.RELU)
                                .build(),
                        "batch"
                )
                .addLayer("output",
                        new OutputLayer.Builder()
                                .nOut(2)
                                .activation(Activation.SOFTMAX)
                                .weightInit(WeightInit.XAVIER)
                                .lossFunction(LossFunctions.LossFunction.MCXENT)
                                .build(),
                        "dense"
                ).build();
        this.network = new ComputationGraph(conf);
        network.init();

        // And a dataset to learn
        INDArray in = Nd4j.zeros(DATA_SIZE, 2);
        Random random = new Random(SEED);
        for (int i = 0; i < DATA_SIZE; i++) {
            in.putScalar(i, random.nextInt(2), 1f);
        }

        this.states = new INDArray[]{
                Nd4j.createFromArray(
                        1f, 0f,
                        0f, 1f
                ).reshape(2, 2)
        };
        this.inputs = new INDArray[]{in};
        this.labels = inputs;
    }

    @Test
    void testClassifier() {
        INDArray preOutput = network.output(states)[0];

        for (int i = 0; i < NUM_EPOCHS; i++) {
            network.fit(inputs, labels);
        }

        INDArray postOutput = network.output(states)[0];

        for (String line : new TextTable()
                .headers(1, "pi(a0|s0)", "pi(a1|s0)", "pi(a0|s1)'", "pi(a1|s1)'")
                .set(0, 0, "Pre")
                .format(0, 1, "%.3f", preOutput.getFloat(0, 0))
                .format(0, 2, "%.3f", preOutput.getFloat(0, 1))
                .format(0, 3, "%.3f", preOutput.getFloat(1, 0))
                .format(0, 4, "%.3f", preOutput.getFloat(1, 1))
                .set(1, 0, "Post")
                .format(1, 1, "%.3f", postOutput.getFloat(0, 0))
                .format(1, 2, "%.3f", postOutput.getFloat(0, 1))
                .format(1, 3, "%.3f", postOutput.getFloat(1, 0))
                .format(1, 4, "%.3f", postOutput.getFloat(1, 1))
                .build()) {
            logger.atDebug().log("{}", line);
        }
        assertTrue(preOutput.getFloat(0, 0) < postOutput.getFloat(0, 0));
        assertTrue(preOutput.getFloat(1, 1) < postOutput.getFloat(1, 1));
    }
}