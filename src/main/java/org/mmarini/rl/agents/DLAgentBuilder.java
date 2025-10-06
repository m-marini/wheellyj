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

import com.fasterxml.jackson.databind.JsonNode;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.distribution.TruncatedNormalDistribution;
import org.deeplearning4j.nn.conf.graph.ElementWiseVertex;
import org.deeplearning4j.nn.conf.graph.GraphVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.nn.weights.WeightInitDistribution;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.wheelly.apis.Utils;
import org.mmarini.wheelly.apis.WheellyJsonSchemas;
import org.mmarini.yaml.Locator;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.IUpdater;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.mmarini.rl.agents.DLAgent.*;

/**
 * Builds DL Agent
 */
public class DLAgentBuilder {
    public static final String SCHEMA_NAME = "https://mmarini.org/wheelly/dl-agent-builder-schema-0.1";
    public static final String INPUTS_ID = "inputs";
    public static final String OUTPUTS_ID = "outputs";
    public static final String HIDDEN_ID = "hiddens";
    public static final String VERTICES_ID = "vertices";
    public static final String PATH_ID = "path";
    public static final String LEARNING_RATE_ID = "learningRate";
    public static final String BETA_1_ID = "beta1";
    public static final String BETA_2_ID = "beta2";
    public static final String EPSILON_ID = "epsilon";
    public static final String UPDATER_ID = "updater";
    public static final String SGD_ID = "Sgd";
    public static final String ADAM_ID = "Adam";
    public static final String GRAPH_BUILDER_ID = "graphBuilder";
    public static final String TYPE_ID = "type";
    public static final String SIZE_ID = "size";
    public static final String FEED_FORWARD_ID = "FeedForward";
    public static final String CONVOLUTIONAL_ID = "Convolutional";
    public static final String HEIGHT_ID = "height";
    public static final String WIDTH_ID = "width";
    public static final String DEPTH_ID = "depth";
    public static final String OUTPUT_LAYER_ID = "OutputLayer";
    public static final String N_OUT_ID = "nOut";
    public static final String ACTIVATION_ID = "activation";
    public static final String IDENTITY_ID = "Identity";
    public static final String LOSS_FUNCTION_ID = "lossFunction";
    public static final String SOFT_MAX_ID = "SoftMax";
    public static final String SQUARED_LOSS_ID = "SquaredLoss";
    public static final String TANH = "Tanh";
    public static final String RELU_ID = "Relu";
    public static final String DROP_OUT_ID = "dropOut";
    public static final String RMS_DECAY_ID = "rmsDecay";
    public static final String OPTIMIZATION_ALGO_ID = "optimizationAlgo";
    public static final String LBFGS_ID = "Lbfgs";
    public static final String CONJUCATE_GRADIENT_ID = "ConjucateGradient";
    public static final String LINE_GRADIENT_DESCENT_ID = "LineGradientDescent";
    public static final String WEIGHT_INIT_ID = "weightInit";
    public static final String TRUNCATED_NORMAL_DISTRIBUTION_ID = "TruncatedNormalDistribution";
    public static final String MEAN_ID = "mean";
    public static final String STDDEV_ID = "stddev";
    public static final String RMS_PROP_ID = "RmsProp";
    public static final String L1_ID = "l1";
    public static final String L2_ID = "l2";
    public static final String ZERO_PADDING_LAYER_ID = "ZeroPaddingLayer";
    public static final String PADDING_ID = "padding";
    public static final String CONVOLUTION_LAYER_ID = "ConvolutionLayer";
    public static final String KERNEL_SIZE_ID = "kernelSize";
    public static final String STRIDE_ID = "stride";
    public static final String BATCH_NORMALIZATION_LAYER_ID = "BatchNormalizationLayer";
    public static final String ACTIVATION_LAYER_ID = "ActivationLayer";
    public static final String SUBSAMPLING_LAYER_ID = "SubsamplingLayer";
    public static final String POOLING_TYPE_ID = "poolingType";
    public static final String MAX_ID = "Max";
    public static final String PNORM_ID = "PNorm";
    public static final String AVG_ID = "Avg";
    public static final String SUM_ID = "Sum";
    public static final String ELEMENT_WISE_VERTEX_ID = "ElementWiseVertex";
    public static final String COSINE_PROXIMITY_ID = "CosineProximity";
    public static final String NEGATIVE_LOG_LIKELIHOOD_ID = "NegativeLogLikelihood";
    public static final String HINGE_ID = "Hinge";
    public static final String KL_DIVERGENCE_ID = "KLDivergence";
    public static final String MCXENT_ID = "Mcxent";
    public static final String MEAN_ABSOLUTE_ERROR_ID = "MeanAbsoluteError";
    public static final String MSE_ID = "Mse";
    public static final String MEAN_SQUARED_LOGARITHMIC_ERROR_ID = "MeanSquaredLogarithmicError";
    public static final String MEAN_ABSOLUTE_PERCENTAGE_ERROR_ID = "MeanAbsolutePercentageError";
    public static final String POISSON_ID = "Poisson";
    public static final String RECONSTRUCTION_CROSSENTROPY_ID = "ReconstructionCrossentropy";
    public static final String SPARSE_MCXENT_ID = "SparseMcxent";
    public static final String SQUARED_HINGE_ID = "SquaredHinge";
    public static final String WASSERSTEIN_ID = "Wasserstein";
    public static final String XENT_ID = "Xent";
    public static final String FILTERS_ID = "filters";
    public static final String CONVOLUTION_RES_NET_BLOCK_ID = "ConvolutionResNetBlock";
    public static final String IDENTITY_RES_NET_BLOCK_ID = "IdentityResNetBlock";
    public static final String FORMAT_ID = "format";
    private static final String STOCHASTIC_GRADIENT_DESCENT_ID = "StochasticGradientDescent";
    private static final Map<String, SubsamplingLayer.PoolingType> poolingTypeMap = Map.of(
            AVG_ID, SubsamplingLayer.PoolingType.AVG,
            MAX_ID, SubsamplingLayer.PoolingType.MAX,
            PNORM_ID, SubsamplingLayer.PoolingType.PNORM,
            SUM_ID, SubsamplingLayer.PoolingType.SUM
    );
    private static final Map<String, Activation> activationMap = Map.of(
            IDENTITY_ID, Activation.IDENTITY,
            SOFT_MAX_ID, Activation.SOFTMAX,
            TANH, Activation.TANH,
            RELU_ID, Activation.RELU
    );
    private static final Map<String, LossFunctions.LossFunction> lossFunctionMap = Map.ofEntries(
            Map.entry(COSINE_PROXIMITY_ID, LossFunctions.LossFunction.COSINE_PROXIMITY),
            Map.entry(MCXENT_ID, LossFunctions.LossFunction.MCXENT),
            Map.entry(HINGE_ID, LossFunctions.LossFunction.HINGE),
            Map.entry(KL_DIVERGENCE_ID, LossFunctions.LossFunction.KL_DIVERGENCE),
            Map.entry(L1_ID, LossFunctions.LossFunction.L1),
            Map.entry(L2_ID, LossFunctions.LossFunction.L2),
            Map.entry(MEAN_ABSOLUTE_ERROR_ID, LossFunctions.LossFunction.MEAN_ABSOLUTE_ERROR),
            Map.entry(MEAN_ABSOLUTE_PERCENTAGE_ERROR_ID, LossFunctions.LossFunction.MEAN_ABSOLUTE_PERCENTAGE_ERROR),
            Map.entry(MEAN_SQUARED_LOGARITHMIC_ERROR_ID, LossFunctions.LossFunction.MEAN_SQUARED_LOGARITHMIC_ERROR),
            Map.entry(MSE_ID, LossFunctions.LossFunction.MSE),
            Map.entry(NEGATIVE_LOG_LIKELIHOOD_ID, LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD),
            Map.entry(POISSON_ID, LossFunctions.LossFunction.POISSON),
            Map.entry(RECONSTRUCTION_CROSSENTROPY_ID, LossFunctions.LossFunction.RECONSTRUCTION_CROSSENTROPY),
            Map.entry(SPARSE_MCXENT_ID, LossFunctions.LossFunction.SPARSE_MCXENT),
            Map.entry(SQUARED_LOSS_ID, LossFunctions.LossFunction.SQUARED_LOSS),
            Map.entry(SQUARED_HINGE_ID, LossFunctions.LossFunction.SQUARED_HINGE),
            Map.entry(WASSERSTEIN_ID, LossFunctions.LossFunction.WASSERSTEIN),
            Map.entry(XENT_ID, LossFunctions.LossFunction.XENT)
    );
    private static final Map<String, OptimizationAlgorithm> optimizationAlgoMap = Map.of(
            CONJUCATE_GRADIENT_ID, OptimizationAlgorithm.CONJUGATE_GRADIENT,
            LBFGS_ID, OptimizationAlgorithm.LBFGS,
            LINE_GRADIENT_DESCENT_ID, OptimizationAlgorithm.LINE_GRADIENT_DESCENT,
            STOCHASTIC_GRADIENT_DESCENT_ID, OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT
    );
    private static final Map<String, GraphVertex> vertexMap = Map.of(
            ELEMENT_WISE_VERTEX_ID, new ElementWiseVertex(ElementWiseVertex.Op.Add)
    );
    private static final Map<String, CNN2DFormat> cnn2DFormatMap = Map.of(
            CNN2DFormat.NCHW.name(), CNN2DFormat.NCHW,
            CNN2DFormat.NHWC.name(), CNN2DFormat.NHWC
    );

    /**
     * Creates the convolution resnet block that increases the number of channels
     * <p>
     * The block is composed of two branches
     * <pre>
     *     id_01                  id_11
     *     Convolution filter[0]  Convolution filter[2]
     *     kernel = [1 x 1]       kernel = [1 x 1]
     *     stride = [stride]      stride = [stride]
     *
     *     id_batch_01            id_batch_11
     *     Batch Normalization    Batch Normalization
     *
     *     id_relu_01
     *     RELU
     *
     *     id_02
     *     Convolution filter[1]
     *     kernel = kernel
     *     stride = [1x1]
     *     mode=same
     *
     *     id_batch_02
     *     Batch Normalization
     *
     *     id_relu_02
     *     RELU
     *
     *     id_03
     *     Convolution filter[2]
     *     kernel = kernel
     *     stride = [1x1]
     *
     *     id_batch_03
     *     Batch Normalization
     *
     *                     id_add
     *                     Element Wise Add
     *                     (id_batch_03, id_batch_11)
     *
     *                     id
     *                     RELU
     * </pre>
     *
     * @param builder    the builder
     * @param id         the layer id (out)
     * @param input      the input layer
     * @param kernelSize the kernel size (height, width)
     * @param stride     the stride (height, width)
     * @param filters    the number of filters for each of the 3 layers [a, b, c]
     */
    static ComputationGraphConfiguration.GraphBuilder buildConvResNetBlock(ComputationGraphConfiguration.GraphBuilder builder, String id, String input, int[] kernelSize, int[] stride, int[] filters) {
        String batchName = id + "_batch";
        String reluName = id + "_relu";
        String addName = id + "_add";

        return builder.addLayer(id + "_01",
                        new ConvolutionLayer.Builder(new int[]{1, 1}, stride)
                                .nOut(filters[0]).build(),
                        input)
                .addLayer(batchName + "_01", new BatchNormalization(), id + "_01")
                .addLayer(reluName + "_01",
                        new ActivationLayer.Builder().activation(Activation.RELU).build(),
                        batchName + "_01")

                .addLayer(id + "_02",
                        new ConvolutionLayer.Builder(kernelSize).nOut(filters[1])
                                .convolutionMode(ConvolutionMode.Same).build(),
                        reluName + "_01")
                .addLayer(batchName + "_02", new BatchNormalization(), id + "_02")
                .addLayer(reluName + "_02",
                        new ActivationLayer.Builder().activation(Activation.RELU).build(),
                        batchName + "_02")

                .addLayer(id + "_03",
                        new ConvolutionLayer.Builder(new int[]{1, 1}).nOut(filters[2]).build(),
                        reluName + "_02")
                .addLayer(batchName + "_03", new BatchNormalization(), id + "_03")

                // shortcut
                .addLayer(id + "_11",
                        new ConvolutionLayer.Builder(new int[]{1, 1}, stride).nOut(filters[2]).build(),
                        input)
                .addLayer(batchName + "_11", new BatchNormalization(), id + "_11")

                .addVertex(addName, new ElementWiseVertex(ElementWiseVertex.Op.Add), batchName + "_03",
                        batchName + "_11")
                .addLayer(id, new ActivationLayer.Builder().activation(Activation.RELU).build(),
                        addName);
    }

    /**
     * Creates the convolution resnet block that maintains the number of channels
     * <p>
     * The block is composed of
     * <pre>
     *     id_1
     *     Convolution filter[0]
     *     kernel = [1 x 1]
     *     stride = [1 x 1]
     *
     *     id_batch_1
     *     Batch Normalization
     *
     *     id_relu_1
     *     RELU
     *
     *     id_2
     *     Convolution filter[1]
     *     kernel = kernel
     *     stride = [1x1]
     *     mode=same
     *
     *     id_batch_2
     *     Batch Normalization
     *
     *     id_relu_2
     *     RELU
     *
     *     id_3
     *     Convolution filter[2]
     *     kernel = [1x1]
     *     stride = [1x1]
     *
     *     id_batch_3
     *     Batch Normalization
     *
     *                     id_add
     *                     Element Wise Add
     *                     (id_batch_3, input)
     *
     *                     id
     *                     RELU
     * </pre>
     *
     * @param builder    the builder
     * @param id         the layer id (out)
     * @param input      the input layer
     * @param kernelSize the kernel size (height, width)
     * @param filters    the number of filters for each of the 3 layers [a, b, c]
     */
    static ComputationGraphConfiguration.GraphBuilder buildIdentityResNetBlock(ComputationGraphConfiguration.GraphBuilder builder, String id, String input,
                                                                               int[] kernelSize, int[] filters) {
        String batchName = id + "_batch";
        String reluName = id + "_relu";
        String addName = id + "_short";

        return builder.addLayer(id + "_1",
                        new ConvolutionLayer.Builder(new int[]{1, 1})
                                .nOut(filters[0])
                                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                                .build(),
                        input)
                .addLayer(batchName + "_1", new BatchNormalization(), id + "_1")
                .addLayer(reluName + "_1",
                        new ActivationLayer.Builder().activation(Activation.RELU).build(),
                        batchName + "_1")

                .addLayer(id + "_2",
                        new ConvolutionLayer.Builder(kernelSize)
                                .nOut(filters[1])
                                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                                .convolutionMode(ConvolutionMode.Same)
                                .build(),
                        reluName + "_1")
                .addLayer(batchName + "_2", new BatchNormalization(), id + "_2")
                .addLayer(reluName + "_2",
                        new ActivationLayer.Builder().activation(Activation.RELU).build(),
                        batchName + "_2")

                .addLayer(id + "_3",
                        new ConvolutionLayer.Builder(new int[]{1, 1})
                                .nOut(filters[2])
                                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                                .build(),
                        reluName + "_2")
                .addLayer(batchName + "_3", new BatchNormalization(), id + "_3")

                .addVertex(addName, new ElementWiseVertex(ElementWiseVertex.Op.Add),
                        input, batchName + "_3")
                .addLayer(id, new ActivationLayer.Builder().activation(Activation.RELU).build(),
                        addName);
    }

    /**
     * Returns the agent from spec
     *
     * @param root the spec document
     * @param env  the environment
     */
    public static DLAgent create(JsonNode root, WithSignalsSpec env) throws IOException {
        Random random = Nd4j.getRandom();
        DLAgent agent = new DLAgentBuilder(root).build(random);
        agent.validate(env.stateSpec(), env.actionSpec());
        return agent;
    }

    /**
     * Returns the builder of agant
     *
     * @param root the configuration
     * @param file the configuration file
     */
    public static Function<WithSignalsSpec, DLAgent> create(JsonNode root, File file) {
        return env -> {
            try {
                return create(root, env);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
    private final JsonNode root;
    private final Map<String, Function<Locator, Layer>> layerMap;
    private final Map<String, Function<Locator, InputType>> inputTypeMap;
    private final Map<String, Function<Locator, IUpdater>> updaterMap;
    private final Map<String, Consumer<Locator>> netWeigthInitMap;
    private final Map<String, BiConsumer<String, Locator>> blockMap;
    private ComputationGraphConfiguration.GraphBuilder graphBuilder;
    private NeuralNetConfiguration.Builder nnBuilder;

    /**
     * Creates the builder
     *
     * @param root the root json node
     */
    public DLAgentBuilder(JsonNode root) {
        this.root = root;
        this.inputTypeMap = Map.of(
                CONVOLUTIONAL_ID, this::buildConvolutionalInput,
                FEED_FORWARD_ID, this::buildFeedForwardInput
        );
        this.layerMap = Map.of(
                ACTIVATION_LAYER_ID, this::buildActivationLayer,
                BATCH_NORMALIZATION_LAYER_ID, this::buildBatchNormalizationLayer,
                CONVOLUTION_LAYER_ID, this::buildConvolutionLayer,
                OUTPUT_LAYER_ID, this::buildOutputLayer,
                SUBSAMPLING_LAYER_ID, this::buildSubsamplingLayer,
                ZERO_PADDING_LAYER_ID, this::buildZeroPaddingLayer
        );
        this.netWeigthInitMap = Map.of(
                TRUNCATED_NORMAL_DISTRIBUTION_ID, this::buildTruncatedNormalDistribution,
                "Xavier", this::buildXavier
        );
        this.updaterMap = Map.of(
                ADAM_ID, this::buildAdam,
                SGD_ID, this::buildSgd,
                RMS_PROP_ID, this::buildRmsProp
        );
        this.blockMap = Map.of(
                CONVOLUTION_RES_NET_BLOCK_ID, this::buildConvResNetBlock,
                IDENTITY_RES_NET_BLOCK_ID, this::buildIdentityResNetBlock
        );
    }

    /**
     * Returns the agent
     *
     * @param random the random number generator
     */
    public DLAgent build(Random random) throws IOException {
        WheellyJsonSchemas.instance().validateOrThrow(root, SCHEMA_NAME);
        String path = Locator.locate(PATH_ID).getNode(root).asText();
        File filePath = new File(path);
        if (filePath.exists()) {
            return DLAgent.fromFile(filePath, random);
        }
        ComputationGraphConfiguration conf = buildConf(Locator.locate("network"));
        ComputationGraph network = new ComputationGraph(conf);
        network.init();
        int numEpochs = Locator.locate(NUM_EPOCHS_ID).getNode(root).asInt();
        int trajectorySize = Locator.locate(TRAJECTORY_SIZE_ID).getNode(root).asInt();
        int batchSize = Locator.locate(BATCH_SIZE_ID).getNode(root).asInt();
        float alpha = (float) Locator.locate(ALPHA_ID).getNode(root).asDouble();
        float beta = (float) Locator.locate(BETA_ID).getNode(root).asDouble();
        return new DLAgent(filePath, network, random, numEpochs, trajectorySize, batchSize, alpha, beta, List.of(), 0F);
    }

    private ActivationLayer buildActivationLayer(Locator locator) {
        ActivationLayer.Builder builder = new ActivationLayer.Builder();
        Activation activation = getOptByMap(locator.path(ACTIVATION_ID), activationMap);
        if (activation != null) {
            builder = builder.activation(activation);
        }
        return builder.build();
    }

    private IUpdater buildAdam(Locator locator) {
        Adam.Builder builder = Adam.builder();
        JsonNode eta = locator.path(LEARNING_RATE_ID).getNode(root);
        if (!eta.isMissingNode()) {
            builder.learningRate(eta.asDouble());
        }
        JsonNode beta1 = locator.path(BETA_1_ID).getNode(root);
        if (!beta1.isMissingNode()) {
            builder.beta1(beta1.asDouble());
        }
        JsonNode beta2 = locator.path(BETA_2_ID).getNode(root);
        if (!beta2.isMissingNode()) {
            builder.beta2(beta1.asDouble());
        }
        JsonNode epsilon = locator.path(EPSILON_ID).getNode(root);
        if (!beta2.isMissingNode()) {
            builder.epsilon(epsilon.asDouble());
        }
        return builder.build();
    }

    private Layer buildBatchNormalizationLayer(Locator locator) {
        return new BatchNormalization.Builder().build();
    }

    private void buildBlock(String id, Locator locator) {
        String type = locator.path(TYPE_ID).getNode(root).asText();
        BiConsumer<String, Locator> builder = blockMap.get(type);
        if (builder == null) {
            throw new IllegalArgumentException(format("Invalid type \"%s\" at %s",
                    locator.path(TYPE_ID).getNode(root).asText(), locator.path(TYPE_ID)));
        }
        builder.accept(id, locator);
    }

    private void buildBlocks(Locator locator) {
        locator.propertyNames(root)
                .forEach(this::buildBlock);
    }

    /**
     * Returns the computeation graph configuration
     *
     * @param locator the json locator
     */
    private ComputationGraphConfiguration buildConf(Locator locator) {
        this.graphBuilder = buildNNConf(locator).graphBuilder();
        Locator graphBuilderLocator = locator.path(GRAPH_BUILDER_ID);
        List<Tuple2<String, InputType>> inputs = buildInputs(graphBuilderLocator.path(INPUTS_ID)).toList();
        String[] inputIds = inputs.stream()
                .map(Tuple2::getV1)
                .toArray(String[]::new);
        InputType[] inputTypes = inputs.stream()
                .map(Tuple2::getV2)
                .toArray(InputType[]::new);
        graphBuilder = graphBuilder
                .addInputs(inputIds)
                .setInputTypes(inputTypes);

        List<Def<Layer>> outputs = buildLayers(graphBuilderLocator.path(OUTPUTS_ID)).toList();
        List<Def<Layer>> hiddens = buildLayers(graphBuilderLocator.path(HIDDEN_ID)).toList();
        List<Def<GraphVertex>> vertices = buildVertices(graphBuilderLocator.path(VERTICES_ID)).toList();
        for (Def<Layer> layerDef : outputs) {
            graphBuilder = graphBuilder.addLayer(layerDef.id, layerDef.layer, layerDef.inputs);
        }
        for (Def<Layer> layerDef : hiddens) {
            graphBuilder = graphBuilder.addLayer(layerDef.id, layerDef.layer, layerDef.inputs);
        }
        for (Def<GraphVertex> layerDef : vertices) {
            graphBuilder = graphBuilder.addVertex(layerDef.id, layerDef.layer, layerDef.inputs);
        }
        buildBlocks(graphBuilderLocator.path("blocks"));
        graphBuilder = graphBuilder.setOutputs(
                outputs.stream().map(Def::id).toArray(String[]::new)
        );
        return graphBuilder.build();
    }

    /**
     * Builds convolution block
     *
     * @param id      the block id
     * @param locator the JSON locator
     */
    private void buildConvResNetBlock(String id, Locator locator) {
        String[] inputs = Utils.loadStringArray(root, locator.path(INPUTS_ID));
        int[] kernelSize = Utils.loadIntArray(root, locator.path(KERNEL_SIZE_ID));
        int[] stride = Utils.loadIntArray(root, locator.path(STRIDE_ID));
        int[] filters = Utils.loadIntArray(root, locator.path(FILTERS_ID));
        graphBuilder = buildConvResNetBlock(graphBuilder, id, inputs[0], kernelSize, stride, filters);
    }

    private Layer buildConvolutionLayer(Locator locator) {
        int[] kernelSize = Utils.loadIntArray(root, locator.path(KERNEL_SIZE_ID));
        ConvolutionLayer.Builder builder = new ConvolutionLayer.Builder(kernelSize);
        int[] stride = Utils.loadIntArray(root, locator.path(STRIDE_ID));
        if (stride != null) {
            builder = builder.stride(stride);
        }
        return builder
                .nOut(locator.path(N_OUT_ID).getNode(root).asInt())
                .build();
    }

    private InputType buildConvolutionalInput(Locator locator) {
        CNN2DFormat format = getOptByMap(locator.path(FORMAT_ID), cnn2DFormatMap);
        return format != null
                ? InputType.convolutional(
                locator.path(HEIGHT_ID).getNode(root).asLong(),
                locator.path(WIDTH_ID).getNode(root).asLong(),
                locator.path(DEPTH_ID).getNode(root).asLong(),
                format)
                : InputType.convolutional(
                locator.path(HEIGHT_ID).getNode(root).asLong(),
                locator.path(WIDTH_ID).getNode(root).asLong(),
                locator.path(DEPTH_ID).getNode(root).asLong());
    }

    private InputType buildFeedForwardInput(Locator locator) {
        return InputType.feedForward(
                locator.path(SIZE_ID).getNode(root).asLong()
        );
    }

    private void buildIdentityResNetBlock(String id, Locator locator) {
        String[] inputs = Utils.loadStringArray(root, locator.path(INPUTS_ID));
        int[] kernelSize = Utils.loadIntArray(root, locator.path(KERNEL_SIZE_ID));
        int[] filters = Utils.loadIntArray(root, locator.path(FILTERS_ID));
        graphBuilder = buildIdentityResNetBlock(graphBuilder, id, inputs[0], kernelSize, filters);
    }

    private InputType buildInput(Locator locator) {
        return buildRequiredByMap(locator, inputTypeMap);
    }

    private Stream<Tuple2<String, InputType>> buildInputs(Locator locator) {
        return locator.propertyNames(root)
                .mapValues(this::buildInput)
                .tuples();
    }

    private Def<Layer> buildLayer(String id, Locator locator) {
        String[] inputs = locator.path(INPUTS_ID).elements(root)
                .map(l -> l.getNode(root).asText())
                .toArray(String[]::new);
        return new Def<>(id, buildRequiredByMap(locator, layerMap), inputs);
    }

    private Stream<Def<Layer>> buildLayers(Locator locator) {
        return locator.propertyNames(root)
                .mapToObj(this::buildLayer);
    }

    /**
     * Returns the Neural network configuration
     *
     * @param locator the JSON locator
     */
    private NeuralNetConfiguration.Builder buildNNConf(Locator locator) {
        this.nnBuilder = new NeuralNetConfiguration.Builder()
                .miniBatch(true)
                .cacheMode(CacheMode.NONE)
                .trainingWorkspaceMode(WorkspaceMode.ENABLED)
                .inferenceWorkspaceMode(WorkspaceMode.ENABLED)
                .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                .convolutionMode(ConvolutionMode.Truncate);
        IUpdater updater = buildOptByMap(locator.path(UPDATER_ID), updaterMap);
        if (updater != null) {
            nnBuilder.updater(updater);
        }
        Consumer<Locator> weightInit = getOptByMap(locator.path(WEIGHT_INIT_ID), netWeigthInitMap);
        if (weightInit != null) {
            weightInit.accept(locator);
        }
        Activation activation = getOptByMap(locator.path(ACTIVATION_ID), activationMap);
        if (activation != null) {
            nnBuilder = nnBuilder.activation(activation);
        }
        JsonNode dropOutNode = locator.path(DROP_OUT_ID).getNode(root);
        if (!dropOutNode.isMissingNode()) {
            nnBuilder = nnBuilder.dropOut(dropOutNode.asDouble());
        }
        OptimizationAlgorithm oa = getOptByMap(locator.path(OPTIMIZATION_ALGO_ID), optimizationAlgoMap);
        if (oa != null) {
            nnBuilder = nnBuilder.optimizationAlgo(oa);
        }
        JsonNode l1 = locator.path(L1_ID).getNode(root);
        if (!l1.isMissingNode()) {
            nnBuilder = nnBuilder.l1(l1.asDouble());
        }
        JsonNode l2 = locator.path(L2_ID).getNode(root);
        if (!l2.isMissingNode()) {
            nnBuilder = nnBuilder.l2(l2.asDouble());
        }
        return nnBuilder;
    }

    private <T> T buildOptByMap(Locator locator, Map<String, Function<Locator, T>> map) {
        String type = locator.path(TYPE_ID).getNode(root).asText();
        Function<Locator, T> builder = map.get(type);
        return builder != null
                ? builder.apply(locator)
                : null;
    }

    private Layer buildOutputLayer(Locator locator) {
        OutputLayer.Builder builder = new OutputLayer.Builder()
                .nOut(locator.path(N_OUT_ID).getNode(root).asInt());
        Activation activation = getOptByMap(locator.path(ACTIVATION_ID), activationMap);
        if (activation != null) {
            builder = builder.activation(activation);
        }
        LossFunctions.LossFunction lossFunction = getOptByMap(locator.path(LOSS_FUNCTION_ID), lossFunctionMap);
        if (lossFunction != null) {
            builder = builder.lossFunction(lossFunction);
        }
        JsonNode dropOut = locator.path(DROP_OUT_ID).getNode(root);
        if (!dropOut.isMissingNode()) {
            builder = builder.dropOut(dropOut.asDouble());
        }
        return builder.build();
    }

    private <T> T buildRequiredByMap(Locator locator, Map<String, Function<Locator, T>> map) {
        T result = buildOptByMap(locator, map);
        if (result == null) {
            throw new IllegalArgumentException(format("Invalid type \"%s\" at %s",
                    locator.path(TYPE_ID).getNode(root).asText(), locator));
        }
        return result;
    }

    private IUpdater buildRmsProp(Locator locator) {
        RmsProp.Builder builder = RmsProp.builder();
        JsonNode learningRate = locator.path(LEARNING_RATE_ID).getNode(root);
        if (!learningRate.isMissingNode()) {
            builder.learningRate(learningRate.asDouble());
        }
        JsonNode rmsDecay = locator.path(RMS_DECAY_ID).getNode(root);
        if (!rmsDecay.isMissingNode()) {
            builder.rmsDecay(rmsDecay.asDouble());
        }
        JsonNode epsilon = locator.path(EPSILON_ID).getNode(root);
        if (!rmsDecay.isMissingNode()) {
            builder.epsilon(epsilon.asDouble());
        }
        return builder.build();
    }

    private IUpdater buildSgd(Locator locator) {
        Sgd.Builder builder = Sgd.builder();
        JsonNode etaNode = locator.path(LEARNING_RATE_ID).getNode(root);
        if (!etaNode.isMissingNode()) {
            builder.learningRate(etaNode.asDouble());
        }
        return builder.build();
    }

    private SubsamplingLayer buildSubsamplingLayer(Locator locator) {
        int[] kernelSize = Utils.loadIntArray(root, locator.path(KERNEL_SIZE_ID));
        SubsamplingLayer.Builder builder = new SubsamplingLayer.Builder(kernelSize);
        int[] stride = Utils.loadIntArray(root, locator.path(STRIDE_ID));
        if (stride != null) {
            builder = builder.stride(stride);
        }
        SubsamplingLayer.PoolingType poolingType = getOptByMap(locator.path(POOLING_TYPE_ID), poolingTypeMap);
        builder = builder.poolingType(poolingType);
        SubsamplingLayer layer = builder.build();
        return layer;
    }

    private void buildTruncatedNormalDistribution(Locator locator) {
        double mean = locator.path(MEAN_ID).getNode(root).asDouble();
        double stddev = locator.path(STDDEV_ID).getNode(root).asDouble();
        nnBuilder = nnBuilder.weightInit(
                new WeightInitDistribution(new TruncatedNormalDistribution(mean, stddev)));
    }

    private Def<GraphVertex> buildVertex(String id, Locator locator) {
        String[] inputs = locator.path(INPUTS_ID).elements(root)
                .map(l -> l.getNode(root).asText())
                .toArray(String[]::new);
        return new Def<>(id, getRequiredByMap(locator.path(TYPE_ID), vertexMap), inputs);
    }

    private Stream<Def<GraphVertex>> buildVertices(Locator locator) {
        return locator.propertyNames(root)
                .mapToObj(this::buildVertex);
    }

    private void buildXavier(Locator locator) {
        nnBuilder = nnBuilder.weightInit(WeightInit.XAVIER);
    }

    private Layer buildZeroPaddingLayer(Locator locator) {
        int[] padding = Utils.loadIntArray(root, locator.path(PADDING_ID));
        return new ZeroPaddingLayer.Builder(padding).build();
    }

    private <T> T getOptByMap(Locator locator, Map<String, T> map) {
        String key = locator.getNode(root).asText();
        return map.get(key);
    }

    private <T> T getRequiredByMap(Locator locator, Map<String, T> map) {
        T result = getOptByMap(locator, map);
        if (result == null) {
            throw new IllegalArgumentException(format("Invalid type \"%s\" at %s",
                    locator.path(TYPE_ID).getNode(root).asText(), locator));
        }
        return result;
    }

    private record Def<T>(String id, T layer, String... inputs) {
    }
}