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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mmarini.Tuple2;
import org.mmarini.rl.envs.*;
import org.mmarini.yaml.Utils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mmarini.wheelly.envs.DLStateFunction.MAP_SIGNAL_ID;

class MDPConvTest {
    public static final int SEED = 1234;
    public static final int NUM_CHANNELS = 4;
    public static final int GRID_SIZE = 125;
    public static final double ETA = 1e-3;
    public static final double EPSILON = 1e-6;
    static final Logger logger = LoggerFactory.getLogger(DLAgent.class);
    private static final Map<String, Signal>[] ACTIONS = new Map[]{Map.of("action", IntSignal.create(0)),
            Map.of("action", IntSignal.create(1))
    };
    private final static Map<String, Signal>[] STATES = new Map[]{
            createState0(),
            createState1(),
    };

    static {
        Nd4j.getRandom().setSeed(SEED);
    }

    private static ExecutionResult createExecutionResult(int s0, int action, double reward, int s1) {
        return new ExecutionResult(STATES[s0], ACTIONS[action], reward, STATES[s1]);
    }

    private static Map<String, Signal> createState0() {
        INDArray map = Nd4j.zeros(1, GRID_SIZE, GRID_SIZE, NUM_CHANNELS);
        for (int i = 0; i < GRID_SIZE; i++) {
            map.putScalar(new long[]{0, i, 0, 0}, 1);
        }
        return Map.of(
                "map", new ArraySignal(map)
        );
    }

    private static Map<String, Signal> createState1() {
        INDArray map = Nd4j.zeros(1, GRID_SIZE, GRID_SIZE, NUM_CHANNELS);
        for (int i = 0; i < GRID_SIZE; i++) {
            map.putScalar(new long[]{0, 0, i, 1}, 1);
        }
        return Map.of(
                "map", new ArraySignal(map)
        );
    }

    private DLAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        org.mmarini.Utils.deleteRecursive(new File("tmp"));
        Map<String, SignalSpec> stateSpec = Map.of(MAP_SIGNAL_ID, new IntSignalSpec(new long[]{NUM_CHANNELS, GRID_SIZE, GRID_SIZE}, 2));
        Map<String, SignalSpec> actionSpec = Map.of("action", new IntSignalSpec(new long[]{1, 1}, 2));
        WithSignalsSpec env = new WithSignalsSpec() {
            @Override
            public Map<String, SignalSpec> actionSpec() {
                return actionSpec;
            }

            @Override
            public Map<String, SignalSpec> stateSpec() {
                return stateSpec;
            }
        };
        agent = DLAgentBuilder.create(Utils.fromResource("/org.mmarini.rl.agents.MdpConvTest/resnet13.yml"), env);
    }

    @AfterEach
    void tearDown() {
        if (agent != null) {
            agent.close();
        }
    }

    // @Test
    void testTrain() {
        while (!agent.isReadyForTrain()) {
            agent = agent.observe(createExecutionResult(0, 1, 0, 0))
                    .observe(createExecutionResult(0, 0, 1, 1))
                    .observe(createExecutionResult(1, 0, 0, 1))
                    .observe(createExecutionResult(1, 1, 1, 0));
        }
        // Then
        Map<String, Signal> in = Map.of(
                "map", new ArraySignal(Nd4j.vstack(
                        STATES[0].get("map").toINDArray(),
                        STATES[1].get("map").toINDArray()))
        );
        Map<String, INDArray> out0 = agent.predictFromState(in).collect(Tuple2.toMap());

        // When train
        agent.save();
        Map<String, INDArray> out = agent.predictFromState(in).collect(Tuple2.toMap());
        logger.atInfo().log("{} layers, {} parameters",
                agent.network().getNumLayers(),
                agent.network().numParams());
        logger.atInfo().log("prediction pre train {}", out);

        long t0 = System.currentTimeMillis();
        agent = agent.trainByTrajectory();
        long t1 = System.currentTimeMillis();
        logger.atInfo().log("Training time {} s", (t1 - t0 + 500) / 1000);

        // Then
        out = agent.predictFromState(in).collect(Tuple2.toMap());
        logger.atInfo().log("prediction post train {}", out);
        float avg = agent.avgReward();
        logger.atInfo().log("average reward {}", avg);

        assertThat((double) avg, closeTo(0.5, 0.5));
        assertThat(out.get("action").getDouble(0, 0), greaterThanOrEqualTo(2D / 3));
        assertThat(out.get("action").getDouble(1, 1), greaterThanOrEqualTo(2D / 3));
        assertThat(out.get("action").getDouble(0, 0), greaterThanOrEqualTo(out0.get("action").getDouble(0, 0)));
        assertThat(out.get("action").getDouble(1, 1), greaterThanOrEqualTo(out0.get("action").getDouble(1, 1)));
    }
}