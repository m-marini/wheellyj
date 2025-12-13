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
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Sgd;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mmarini.Utils.deleteRecursive;
import static org.mmarini.wheelly.TestFunctions.matrixCloseTo;
import static org.mmarini.wheelly.TestFunctions.matrixShape;
import static org.mmarini.wheelly.envs.DLActionFunction.MOVE_ACTION_ID;
import static org.mmarini.wheelly.envs.DLActionFunction.SENSOR_ACTION_ID;
import static org.mmarini.wheelly.envs.DLStateFunction.MAP_SIGNAL_ID;

class DLAgentTest {
    public static final int NUM_SAMPLES = 2;
    public static final int SEED = 1234;
    public static final int NUM_CHANNELS = 4;
    public static final int GRID_SIZE = 9;
    public static final int NUM_EPOCHS = 10;
    public static final int NUM_MOVEMENT_COMMANDS = NUM_EPOCHS;
    public static final int NUM_SENSOR_COMMANDS = 7;
    public static final double ETA = 1e-3;
    public static final float REWARD = 0.5F;
    public static final double EPSILON = 1e-6;
    public static final float ALPHA = 1F;
    public static final float BETA = 0.8F;
    public static final float REWAORD0 = 0F;
    public static final int NUM_STEPS = NUM_EPOCHS;
    public static final int BATCH_SIZE = 5;
    public static final File FILE = new File("tmp/model");
    static final Logger logger = LoggerFactory.getLogger(DLAgent.class);

    static {
        Nd4j.getRandom().setSeed(SEED);
    }

    static ComputationGraphConfiguration build(
            int numChannels, int gridWidth, int gridHeight,
            int numMovementCommands, int numSensorCommands) {
        return new NeuralNetConfiguration.Builder()
                .updater(new Sgd(ETA))
                .weightInit(WeightInit.XAVIER)
                .graphBuilder()
                .addInputs(MAP_SIGNAL_ID)
                .setInputTypes(new InputType.InputTypeConvolutional(gridHeight, gridWidth, numChannels))
                .addLayer(DLAgent.CRITIC_ID,
                        new OutputLayer.Builder()
                                .nOut(1)
                                .activation(Activation.IDENTITY)
                                .lossFunction(LossFunctions.LossFunction.SQUARED_LOSS)
                                .build(),
                        MAP_SIGNAL_ID
                )
                .addLayer(MOVE_ACTION_ID,
                        new OutputLayer.Builder()
                                .nOut(numMovementCommands)
                                .activation(Activation.SOFTMAX)
                                .build(),
                        MAP_SIGNAL_ID
                )
                .addLayer(SENSOR_ACTION_ID,
                        new OutputLayer.Builder()
                                .nOut(numSensorCommands)
                                .activation(Activation.SOFTMAX)
                                .build(),
                        MAP_SIGNAL_ID
                )
                .setOutputs(DLAgent.CRITIC_ID, MOVE_ACTION_ID, SENSOR_ACTION_ID)
                .build();
    }

    static ExecutionResult createResult(double reward) {
        Map<String, Signal> s0 = Map.of(
                MAP_SIGNAL_ID, new ArraySignal(Nd4j.rand(1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE))
        );
        Map<String, Signal> s1 = Map.of(
                MAP_SIGNAL_ID, new ArraySignal(Nd4j.rand(1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE))
        );
        Map<String, Signal> actions = Map.of(
                MOVE_ACTION_ID, IntSignal.create(Nd4j.getRandom().nextInt(NUM_MOVEMENT_COMMANDS)),
                SENSOR_ACTION_ID, IntSignal.create(Nd4j.getRandom().nextInt(NUM_SENSOR_COMMANDS))
        );
        return new ExecutionResult(s0, actions, reward, s1);
    }

    private DLAgent agent;
    private List<ExecutionResult> trajectory;

    @BeforeEach
    void setUp() throws IOException {
        Map<String, SignalSpec> stateSpec = Map.of(MAP_SIGNAL_ID, new IntSignalSpec(new long[]{NUM_CHANNELS, GRID_SIZE, GRID_SIZE}, NUM_SAMPLES));
        Map<String, SignalSpec> actionSpec = Map.of(
                MOVE_ACTION_ID, new IntSignalSpec(new long[]{1, 1}, NUM_MOVEMENT_COMMANDS),
                SENSOR_ACTION_ID, new IntSignalSpec(new long[]{1, 1}, NUM_SENSOR_COMMANDS)
        );
        ComputationGraphConfiguration conf = build(NUM_CHANNELS, GRID_SIZE, GRID_SIZE, NUM_MOVEMENT_COMMANDS, NUM_SENSOR_COMMANDS);
        logger.atInfo().log("{}", conf.toYaml());
        ComputationGraph net = new ComputationGraph(conf);
        net.init();

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);
        this.agent = DLAgent.create(stateSpec, actionSpec, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHA, BETA, FILE);

        this.trajectory = IntStream.range(0, NUM_STEPS)
                .mapToObj(i -> createResult(i * REWARD / (NUM_STEPS - 1)))
                .toList();
        deleteRecursive(FILE);
    }

    @AfterEach
    void tearDown() {
        agent.close();
    }

    @Test
    void testAct() {
        // Given an input state signals
        Map<String, Signal> states = Map.of(
                MAP_SIGNAL_ID, new ArraySignal(Nd4j.rand(2, NUM_CHANNELS, GRID_SIZE, GRID_SIZE))
        );

        // When act
        Map<String, Signal> actions = agent.act(states);

        // Then the output actions states ...
        assertThat(actions, hasKey(MOVE_ACTION_ID));
        assertThat(actions, hasKey(SENSOR_ACTION_ID));

        INDArray moveAry = actions.get(MOVE_ACTION_ID).toINDArray();
        assertThat(moveAry, matrixShape(2, 1));
        assertThat(moveAry.getInt(0), greaterThanOrEqualTo(0));
        assertThat(moveAry.getInt(0), lessThan(NUM_MOVEMENT_COMMANDS));
        assertThat(moveAry.getInt(1), greaterThanOrEqualTo(0));
        assertThat(moveAry.getInt(1), lessThan(NUM_MOVEMENT_COMMANDS));

        INDArray sensorAry = actions.get(SENSOR_ACTION_ID).toINDArray();
        assertThat(sensorAry, matrixShape(2, 1));
        assertThat(sensorAry.getInt(0), greaterThanOrEqualTo(0));
        assertThat(sensorAry.getInt(0), lessThan(NUM_SENSOR_COMMANDS));
        assertThat(sensorAry.getInt(1), greaterThanOrEqualTo(0));
        assertThat(sensorAry.getInt(1), lessThan(NUM_SENSOR_COMMANDS));
    }

    @Test
    void testBackup() throws IOException {
        agent.save();
        agent.backup();
        File[] list = FILE.listFiles(new PatternFilenameFilter("agent.yml-.*\\.yml"));
        assertThat(list, arrayWithSize(greaterThan(0)));
        list = FILE.listFiles(new PatternFilenameFilter("model-.*\\.zip"));
        assertThat(list, arrayWithSize(greaterThan(0)));
    }

    @Test
    void testClearTrajectory() {
        agent = agent.observe(createResult(0));
        agent = agent.observe(createResult(0));
        assertThat(agent.trajectory(), hasSize(2));

        // When observe result twice
        agent = agent.clearTrajectory();
        // Then ...
        assertThat(agent.trajectory(), empty());
    }

    @Test
    void testComputeNewPolicy() {
        INDArray policy = Nd4j.createFromArray(
                0.25F, 0.25F, 0.25F, 0.25F,
                0.25F, 0.25F, 0.25F, 0.25F
        ).reshape(2, 4);
        INDArray deltas = Nd4j.createFromArray(
                1F, 0F, 0F, 0F,
                0F, 0F, 0F, 1F).reshape(2, 4);
        INDArray newPolicy = DLAgent.computeNewPolicy(policy, deltas);

        assertThat(newPolicy, matrixCloseTo(new long[]{2, 4}, 1e-4,
                0.4754F, 0.1749F, 0.1749F, 0.1749F,
                0.1749F, 0.1749F, 0.1749F, 0.4754F
        ));
    }

    @Test
    void testCreateActionMasks() {
        // When create action masks
        DLAgent.Trajectory traj = agent.createTrajectory(trajectory);
        INDArray mask = DLAgent.createActionMasks(traj.actions().get(MOVE_ACTION_ID), NUM_MOVEMENT_COMMANDS);
        assertThat(mask, matrixShape(NUM_EPOCHS, NUM_MOVEMENT_COMMANDS));
    }

    @Test
    void testCreateTrajectory() {
        // Given ...
        while (!agent.isReadyForTrain()) {
            agent = agent.observe(createResult(0));
        }

        // When ...
        DLAgent.Trajectory tr = agent.createTrajectory();

        // Then ...
        Map<String, INDArray> states = tr.states();
        assertThat(states, hasKey(MAP_SIGNAL_ID));
        assertThat(states.get(MAP_SIGNAL_ID), matrixShape(NUM_STEPS + 1, NUM_CHANNELS, GRID_SIZE, GRID_SIZE));

        // And
        INDArray rewards = tr.rewards();
        assertThat(rewards, matrixShape(NUM_STEPS, 1));

        // And
        Map<String, INDArray> actions = tr.actions();
        assertThat(actions, hasKey(MOVE_ACTION_ID));
        assertThat(actions.get(MOVE_ACTION_ID), matrixShape(NUM_STEPS, 1));
        assertThat(actions, hasKey(SENSOR_ACTION_ID));
        assertThat(actions.get(SENSOR_ACTION_ID), matrixShape(NUM_STEPS, 1));
    }

    @Test
    void testMissingAction() {
        Map<String, SignalSpec> stateSpec = Map.of(MAP_SIGNAL_ID, new IntSignalSpec(new long[]{NUM_CHANNELS, GRID_SIZE, GRID_SIZE}, NUM_SAMPLES));
        Map<String, SignalSpec> actionSpec = Map.of(
                MOVE_ACTION_ID, new IntSignalSpec(new long[]{1, 1}, NUM_MOVEMENT_COMMANDS),
                SENSOR_ACTION_ID, new IntSignalSpec(new long[]{1, 1}, NUM_SENSOR_COMMANDS),
                "missing1", new IntSignalSpec(new long[]{1, 1}, NUM_SENSOR_COMMANDS),
                "missing2", new IntSignalSpec(new long[]{1, 1}, NUM_SENSOR_COMMANDS)
        );
        ComputationGraphConfiguration conf = build(NUM_CHANNELS, GRID_SIZE, GRID_SIZE, NUM_MOVEMENT_COMMANDS, NUM_SENSOR_COMMANDS);
        ComputationGraph net = new ComputationGraph(conf);

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> DLAgent.create(stateSpec, actionSpec, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHA, BETA, FILE));
        assertThat(ex.getMessage(), matchesPattern("Missing output layers \\[missing1, missing2]"));
    }

    @Test
    void testMissingState() {
        Map<String, SignalSpec> stateSpec = Map.of();
        Map<String, SignalSpec> actionSpec = Map.of(
                MOVE_ACTION_ID, new IntSignalSpec(new long[]{1, 1}, NUM_MOVEMENT_COMMANDS),
                SENSOR_ACTION_ID, new IntSignalSpec(new long[]{1, 1}, NUM_SENSOR_COMMANDS)
        );
        ComputationGraphConfiguration conf = build(NUM_CHANNELS, GRID_SIZE, GRID_SIZE, NUM_MOVEMENT_COMMANDS, NUM_SENSOR_COMMANDS);
        ComputationGraph net = new ComputationGraph(conf);

        Random random = Nd4j.getRandomFactory().getNewRandomInstance(SEED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> DLAgent.create(stateSpec, actionSpec, net, random, NUM_EPOCHS, NUM_STEPS, BATCH_SIZE, ALPHA, BETA, FILE));
        assertThat(ex.getMessage(), matchesPattern("Missing input layers \\[map]"));
    }

    @Test
    void testNotReadyForTrain() {
        for (int i = 0; i < NUM_STEPS - 1; i++) {
            agent = agent.observe(createResult(0));
        }
        assertThat(agent.trajectory(), hasSize(NUM_STEPS - 1));

        // When clip trajectory
        boolean ready = agent.isReadyForTrain();
        // Then ...
        assertFalse(ready);
    }

    @Test
    void testObserve() {
        // When observe result twice
        agent = agent.observe(createResult(0));
        agent = agent.observe(createResult(0));

        // Then ...
        assertThat(agent.trajectory(), hasSize(2));
    }

    @Test
    void testProcessRewards() {
        // When create average
        INDArray rewards = Nd4j.ones(4, 1);
        INDArray prediction = Nd4j.ones(BATCH_SIZE, 1).muli(0.5);
        Tuple2<INDArray, Float> t = DLAgent.processRewards(rewards, prediction, REWAORD0, 0.5F);
        INDArray deltas = t._1;
        float avg = t._2;
        assertEquals(0.9375F, avg);
        assertThat(deltas, matrixCloseTo(new long[]{4, 1}, EPSILON,
                1, 0.5F, 0.25F, 0.125F));
    }

    @Test
    void testReadyForTrain() {
        Map<String, Signal> s0 = Map.of(
                MAP_SIGNAL_ID, new ArraySignal(Nd4j.rand(2, NUM_CHANNELS, GRID_SIZE, GRID_SIZE))
        );
        Map<String, Signal> s1 = Map.of(
                MAP_SIGNAL_ID, new ArraySignal(Nd4j.rand(2, NUM_CHANNELS, GRID_SIZE, GRID_SIZE))
        );
        Map<String, Signal> actions = Map.of(
                MOVE_ACTION_ID, IntSignal.create(0),
                SENSOR_ACTION_ID, IntSignal.create(0)
        );
        double reward = 0;
        ExecutionResult result = new ExecutionResult(s0, actions, reward, s1);
        for (int i = 0; i < NUM_STEPS; i++) {
            agent = agent.observe(result);
        }
        assertThat(agent.trajectory(), hasSize(NUM_STEPS));

        // When clip trajectory
        boolean ready = agent.isReadyForTrain();
        // Then ...
        assertTrue(ready);
    }

    @Test
    void testSaveAndLoad() throws IOException {
        agent.save();
        DLAgent newAgent = DLAgent.fromFile(FILE, Nd4j.getRandom());
        assertThat(newAgent.network().getConfiguration().getNetworkInputs(),
                hasItems(agent.network().getConfiguration().getNetworkInputs().toArray(String[]::new)));
        assertThat(newAgent.network().getConfiguration().getNetworkOutputs(),
                hasItems(agent.network().getConfiguration().getNetworkOutputs().toArray(String[]::new)));
    }

    @Test
    void testTrain() {
        while (!agent.isReadyForTrain()) {
            agent = agent.observe(createResult(0));
        }
        // When train
        agent.trainByTrajectory();
    }
}