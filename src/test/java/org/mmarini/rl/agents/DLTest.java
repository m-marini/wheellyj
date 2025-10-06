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

package org.mmarini.rl.agents;

import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.ConvolutionLayer;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.EmbeddingLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.Test;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class DLTest {
    public static final int NUM_CHANNELS = 3;
    public static final double ETA = 0.05;
    public static final int NUM_OUT = 20;
    public static final int KERNEL_SIZE = 3;
    public static final int NUM_SAMPLES = 4;
    public static final int IMG_SIZE = 9;
    public static final int NUM_OUTS = 10;
    public static final int NUM_EPOCHS = 10;
    public static final int BATCH_SIZE = 2;
    private static final Logger logger = LoggerFactory.getLogger(DLTest.class);

    static {
        Nd4j.zeros(1);
    }

    @Test
    void testConvForward2In2Out() {
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new Sgd(ETA))
                .graphBuilder()
                .addInputs("MAP", "IN")
                .setInputTypes(InputType.convolutional(IMG_SIZE, IMG_SIZE, NUM_CHANNELS),
                        InputType.feedForward(1))
                .addLayer("L1",
                        new ConvolutionLayer.Builder(KERNEL_SIZE, KERNEL_SIZE)
                                .weightInit(WeightInit.XAVIER)
                                .nIn(NUM_CHANNELS)
                                .stride(1, 1)
                                .nOut(NUM_OUT)
                                .activation(Activation.RELU)
                                .build(),
                        "MAP")
                .addLayer("L2",
                        new ConvolutionLayer.Builder(KERNEL_SIZE, KERNEL_SIZE)
                                .weightInit(WeightInit.XAVIER)
                                .nIn(NUM_OUT)
                                .stride(1, 1)
                                .nOut(1)
                                .activation(Activation.RELU)
                                .build(),
                        "L1")
                .addLayer("MAP1",
                        new DenseLayer.Builder()
                                .nOut(NUM_EPOCHS)
                                .build(),
                        "L2")
                .addVertex("MERGED", new MergeVertex(), "MAP1", "IN")
                .addLayer("OUT1", new OutputLayer.Builder()
                                .activation(Activation.TANH)
                                .lossFunction(LossFunctions.LossFunction.SQUARED_LOSS)
                                .nOut(1)
                                .build(),
                        "MERGED")
                .addLayer("OUT", new OutputLayer.Builder()
                                .activation(Activation.SOFTMAX)
                                .nOut(NUM_OUTS).build(),
                        "MERGED")
                .setOutputs("OUT1", "OUT")
                .build();

        ComputationGraph net = new ComputationGraph(conf);
        net.init();


        INDArray map = Nd4j.rand(NUM_SAMPLES, NUM_CHANNELS, IMG_SIZE, IMG_SIZE);
        INDArray in = Nd4j.rand(NUM_SAMPLES, 1);
        INDArray[] inputs = new INDArray[]{map, in};

        INDArray[] res = net.output(inputs);

        assertNotNull(res);
        assertEquals(BATCH_SIZE, res.length);
        long[] shape = res[0].shape();
        assertArrayEquals(new long[]{NUM_SAMPLES, 1}, shape);
        shape = res[1].shape();
        assertArrayEquals(new long[]{NUM_SAMPLES, NUM_OUTS}, shape);
    }

    @Test
    void testConvForward2Out() {
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new Sgd(ETA))
                .graphBuilder()
                .addInputs("map")
                .setInputTypes(InputType.convolutional(IMG_SIZE, IMG_SIZE, NUM_CHANNELS))
                .addLayer("L1",
                        new ConvolutionLayer.Builder(KERNEL_SIZE, KERNEL_SIZE)
                                .weightInit(WeightInit.XAVIER)
                                .nIn(NUM_CHANNELS)
                                .stride(1, 1)
                                .nOut(NUM_OUT)
                                .activation(Activation.RELU)
                                .build(),
                        "map")
                .addLayer("L2",
                        new ConvolutionLayer.Builder(KERNEL_SIZE, KERNEL_SIZE)
                                .weightInit(WeightInit.XAVIER)
                                .nIn(NUM_OUT)
                                .stride(1, 1)
                                .nOut(1)
                                .activation(Activation.RELU)
                                .build(),
                        "L1")
                .addLayer("out1", new OutputLayer.Builder()
                                .activation(Activation.TANH)
                                .lossFunction(LossFunctions.LossFunction.SQUARED_LOSS)
                                .nOut(1)
                                .build(),
                        "L2")
                .addLayer("out", new OutputLayer.Builder()
                                .activation(Activation.SOFTMAX)
                                .nOut(NUM_OUTS).build(),
                        "L2")
                .setOutputs("out1", "out")
                .build();
        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        INDArray map = Nd4j.rand(NUM_SAMPLES, NUM_CHANNELS, IMG_SIZE, IMG_SIZE);
        INDArray[] inputs = new INDArray[]{map};
        INDArray[] res = net.output(inputs);

        assertNotNull(res);
        assertEquals(BATCH_SIZE, res.length);
        long[] shape = res[0].shape();
        assertArrayEquals(new long[]{NUM_SAMPLES, 1}, shape);
        shape = res[1].shape();
        assertArrayEquals(new long[]{NUM_SAMPLES, NUM_OUTS}, shape);
    }

    @Test
    void testEmbedded() {
        ComputationGraphConfiguration conf = new NeuralNetConfiguration.Builder()
                .updater(new Sgd(ETA))
                .graphBuilder()
                .addInputs("in")
                .setInputTypes(InputType.feedForward(10))
                .addLayer("embedding",
                        new EmbeddingLayer.Builder()
                                .nOut(10)
                                .build(),
                        "in")
                .addLayer("out", new OutputLayer.Builder()
                                .activation(Activation.IDENTITY)
                                .lossFunction(LossFunctions.LossFunction.SQUARED_LOSS)
                                .nOut(10)
                                .build(),
                        "embedding")
                .setOutputs("out")
                .build();

        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        INDArray in = Nd4j.zeros(10, 1).castTo(DataType.INT8);
        for (int i = 0; i < 9; i++) {
            in.putScalar(i, 0, i);
        }
        INDArray[] inputs = new INDArray[]{in};

        INDArray[] res = net.output(inputs);

        assertNotNull(res);
        assertEquals(1, res.length);
        long[] shape = res[0].shape();
        assertArrayEquals(new long[]{10, 10}, shape);
    }
}
