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

package org.mmarini.wheelly.rlagents;

import com.google.common.io.PatternFilenameFilter;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mmarini.wheelly.gyms.ExecutionResult;
import org.mmarini.wheelly.gyms.SignalSpec;
import org.mmarini.wheelly.gyms.WithSignalsSpec;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Utils.deleteRecursive;
import static org.mmarini.wheelly.TestFunctions.matrixShape;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.HEAD_ID;
import static org.mmarini.wheelly.gyms.MacroActionsAdapter.MOVE_ID;
import static org.mmarini.wheelly.gyms.MacroActionsStateFunction.MAP_SIGNAL_ID;
import static org.mmarini.wheelly.rlagents.NNRLTrainingDataGenerator.CRITIC_ID;

class RLAgentTest {
    public static final int SEED = 1234;
    public static final int NUM_CHANNELS = 4;
    public static final int GRID_SIZE = 9;
    public static final int NUM_EPOCHS = 10;
    public static final int NUM_MOVEMENT_COMMANDS = 10;
    public static final int NUM_SENSOR_COMMANDS = 7;
    public static final double ETA = 1e-3;
    public static final float REWARD = 0.5F;
    public static final double EPSILON = 1e-6;
    public static final Map<String, Float> ALPHAS = Map.of(
            HEAD_ID, 1F,
            MOVE_ID, 1F);
    public static final float BETA = 0.8F;
    public static final float REWARD0 = 0F;
    public static final int NUM_STEPS = 10;
    public static final int BATCH_SIZE = 5;
    public static final File FILE = new File("tmp/model");

    static {
        Nd4j.getRandom().setSeed(SEED);
    }

    static ComputationGraphConfiguration build() {
        return new NeuralNetConfiguration.Builder()
                .updater(new Sgd(ETA))
                .weightInit(WeightInit.XAVIER)
                .graphBuilder()
                .addInputs(MAP_SIGNAL_ID)
                .setInputTypes(InputType.convolutional(GRID_SIZE, GRID_SIZE, NUM_CHANNELS))
                .addLayer(CRITIC_ID,
                        new OutputLayer.Builder()
                                .nOut(1)
                                .activation(Activation.IDENTITY)
                                .lossFunction(LossFunctions.LossFunction.SQUARED_LOSS)
                                .build(),
                        MAP_SIGNAL_ID
                )
                .addLayer(MOVE_ID,
                        new OutputLayer.Builder()
                                .nOut(NUM_MOVEMENT_COMMANDS)
                                .activation(Activation.SOFTMAX)
                                .build(),
                        MAP_SIGNAL_ID
                )
                .addLayer(HEAD_ID,
                        new OutputLayer.Builder()
                                .nOut(NUM_SENSOR_COMMANDS)
                                .activation(Activation.SOFTMAX)
                                .build(),
                        MAP_SIGNAL_ID
                )
                .setOutputs(CRITIC_ID, MOVE_ID, HEAD_ID)
                .build();
    }

    static ExecutionResult createResult(double reward) {
        Map<String, INDArray> s0 = Map.of(
                MAP_SIGNAL_ID, Nd4j.rand(1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE)
        );
        Map<String, INDArray> s1 = Map.of(
                MAP_SIGNAL_ID, Nd4j.rand(1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE)
        );
        Map<String, INDArray> actions = Map.of(
                MOVE_ID, Nd4j.createFromArray(Nd4j.getRandom().nextInt(NUM_MOVEMENT_COMMANDS)).reshape(1, 1),
                HEAD_ID, Nd4j.createFromArray(Nd4j.getRandom().nextInt(NUM_SENSOR_COMMANDS)).reshape(1, 1)
        );
        return new ExecutionResult(s0, actions, reward, s1);
    }

    private RLAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        ComputationGraphConfiguration conf = build();
        // logger.atDebug().log("yaml network {}", conf.toYaml());
        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        this.agent = RLAgent.create(FILE, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHAS, BETA, REWARD, false);
        deleteRecursive(FILE);
    }

    @AfterEach
    void tearDown() {
        agent.close();
    }

    @Test
    void testAct() {
        // Given an input state signals
        Map<String, INDArray> states = Map.of(
                MAP_SIGNAL_ID, Nd4j.rand(2, NUM_CHANNELS, GRID_SIZE, GRID_SIZE)
        );

        // When act
        Map<String, INDArray> actions = agent.act(states);

        // Then the output actions states ...
        assertThat(actions, hasKey(MOVE_ID));
        assertThat(actions, hasKey(HEAD_ID));

        INDArray moveAry = actions.get(MOVE_ID);
        assertThat(moveAry, matrixShape(2, 1));
        assertThat(moveAry.getInt(0), greaterThanOrEqualTo(0));
        assertThat(moveAry.getInt(0), lessThan(NUM_MOVEMENT_COMMANDS));
        assertThat(moveAry.getInt(1), greaterThanOrEqualTo(0));
        assertThat(moveAry.getInt(1), lessThan(NUM_MOVEMENT_COMMANDS));

        INDArray sensorAry = actions.get(HEAD_ID);
        assertThat(sensorAry, matrixShape(2, 1));
        assertThat(sensorAry.getInt(0), greaterThanOrEqualTo(0));
        assertThat(sensorAry.getInt(0), lessThan(NUM_SENSOR_COMMANDS));
        assertThat(sensorAry.getInt(1), greaterThanOrEqualTo(0));
        assertThat(sensorAry.getInt(1), lessThan(NUM_SENSOR_COMMANDS));
    }

    @Test
    void testBackup() {
        agent.save();
        agent.backup();
        File[] list = FILE.listFiles(new PatternFilenameFilter("agent-.*\\.yml"));
        assertThat(list, arrayWithSize(greaterThan(0)));
        list = FILE.listFiles(new PatternFilenameFilter("model-.*\\.zip"));
        assertThat(list, arrayWithSize(greaterThan(0)));
    }

    @Test
    void testMissingAction() {
        WithSignalsSpec withSpec = new WithSignalsSpec() {
            @Override
            public Map<String, SignalSpec> actionSpec() {
                return Map.of(
                        MOVE_ID, SignalSpec.create(1, NUM_MOVEMENT_COMMANDS),
                        HEAD_ID, SignalSpec.create(1, NUM_SENSOR_COMMANDS),
                        "missing1", SignalSpec.create(1, 1),
                        "missing2", SignalSpec.create(1, 1)
                );
            }

            @Override
            public Map<String, SignalSpec> stateSpec() {
                return Map.of(MAP_SIGNAL_ID, SignalSpec.create(1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE));
            }
        };
        ComputationGraphConfiguration conf = build();
        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        agent = RLAgent.create(FILE, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHAS, BETA, REWARD, false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                agent.validate(withSpec));
        assertThat(ex.getMessage(), matchesPattern("Missing output layers \\[missing1, missing2]"));
    }

    @Test
    void testMissingState() {
        WithSignalsSpec withSpec = new WithSignalsSpec() {

            @Override
            public Map<String, SignalSpec> actionSpec() {
                return Map.of(
                        MOVE_ID, SignalSpec.create(1, 1),
                        HEAD_ID, SignalSpec.create(1, 1)
                );
            }

            @Override
            public Map<String, SignalSpec> stateSpec() {
                return Map.of();
            }
        };
        ComputationGraphConfiguration conf = build();
        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        agent = RLAgent.create(FILE, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHAS, BETA, REWARD, false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                agent.validate(withSpec)
        );
        assertThat(ex.getMessage(), matchesPattern("Missing input layers \\[map]"));
    }

    @Test
    void testObserve() {
        ComputationGraph net = agent.network();
        // When observe result twice
        agent.observe(createResult(0));
        agent.observe(createResult(0));

        // Then ...
        assertEquals(2, agent.status().trajectoryBuffer().size());
        assertSame(net, agent.network());
    }

    @Test
    void testSaveAndLoad() throws IOException {
        agent.save();
        RLAgent newAgent = RLAgent.fromFile(FILE, Nd4j.getRandom());
        assertThat(newAgent.network().getConfiguration().getNetworkInputs(),
                hasItems(agent.network().getConfiguration().getNetworkInputs().toArray(String[]::new)));
        assertThat(newAgent.network().getConfiguration().getNetworkOutputs(),
                hasItems(agent.network().getConfiguration().getNetworkOutputs().toArray(String[]::new)));
    }

    @Test
    void testTrain() {
        ComputationGraph net = agent.network();
        for (int i = 0; i < NUM_STEPS; i++) {
            agent.observe(createResult(0));
        }
        // When train
        assertNotSame(net, agent.network());
        assertEquals(0, agent.status().trajectoryBuffer().size());
    }

    @Test
    void testWrongAction() {
        WithSignalsSpec withSpec = new WithSignalsSpec() {

            @Override
            public Map<String, SignalSpec> actionSpec() {
                return Map.of(
                        MOVE_ID, SignalSpec.create(1, NUM_MOVEMENT_COMMANDS - 1),
                        HEAD_ID, SignalSpec.create(1, NUM_SENSOR_COMMANDS - 1)
                );
            }

            @Override
            public Map<String, SignalSpec> stateSpec() {
                return Map.of(MAP_SIGNAL_ID, SignalSpec.create(1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE));
            }
        };
        ComputationGraphConfiguration conf = build();
        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        agent = RLAgent.create(FILE, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHAS, BETA, REWARD, false);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                agent.validate(withSpec));
        assertThat(ex.getMessage(), matchesPattern("Wrong output size: \\[head] expected \\[1, 7] actual \\[1, 6]"));
    }
}