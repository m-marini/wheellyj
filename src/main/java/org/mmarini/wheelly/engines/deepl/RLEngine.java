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

package org.mmarini.wheelly.engines.deepl;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.schedulers.Timed;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.model.*;
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.model.Utils.normalizeDegAngle;
import static org.nd4j.linalg.factory.Nd4j.hstack;
import static org.nd4j.linalg.factory.Nd4j.scalar;

/**
 * The engine choose the action basing on deep learning algorithm.
 * The actor-critic algorithm is used.
 * The actions lay in a 4 dimension space:
 * - binary value for halt command
 * - integer value in (-180, 179) range with the direction of the robot
 * - real speed value in (-1, 1) range with the speed of robot
 * - integer value in (-90, 90) range with the direction of the sensor
 * Each dimension is processed by a single actor at produce the output value
 * There are 2 types of actors:
 * - The discrete actor uses a set of output values to compute the probability of each output value
 * - The gaussian actor uses 2 set of output values (mu, sigma) to compute the normal distribution probability of the output value
 * - Each actor uses a parametric normalizer and de-normalizer function to convert the neural outputs to the desired value.
 * - The normalized output value of the network is in (-1, 1) range.
 * The critic produces the common residual advantage that evaluates the state, action pair for each step used to fit the network.
 */
public class RLEngine implements InferenceEngine {
    public static final int HALT_OFFSET = 0;
    public static final int DIRECTION_OFFSET = 1;
    public static final int SPEED_OFFSET = 2;
    public static final int SENSOR_OFFSET = 3;
    public static final String PERFORMANCE_KEY = "performance";
    private static final Logger logger = LoggerFactory.getLogger(RLEngine.class);

    public static InferenceEngine create(RLEngineConf config, Random random, ActorCriticAgent agent) {
        return new RLEngine(config, random, agent);
    }

    public static INDArray createKpi(Map<String, Object> map, double reward) {
        INDArray score = (INDArray) map.get("score");
        INDArray v0Star = (INDArray) map.get("v0*");
        INDArray avg = (INDArray) map.get("newAverage");
        INDArray delta = (INDArray) map.get("delta");
        INDArray j0 = (INDArray) map.get("J0");
        INDArray j1 = (INDArray) map.get("J1");
        INDArray alphas = (INDArray) map.get("alpha*");
        INDArray h = hstack((INDArray[]) map.get("h"));
        //INDArray hStar = hstack((INDArray[]) map.get("h*"));
        return hstack(Nd4j.create(new float[]{(float) reward}, 1, 1),
                score.reshape(1, 1),
                v0Star.reshape(1, 1),
                delta.reshape(1, 1),
                avg.reshape(1, 1),
                j0.reshape(1, 1),
                j1.reshape(1, 1),
                alphas.reshape(1, alphas.length()),
                h);
    }

    /**
     * Returns the command by decoding the action signals
     *
     * @param status  the status of robot
     * @param actions the actions of robot
     */
    static Tuple2<MotionCommand, Integer> decodeAction(Timed<MapStatus> status, INDArray actions) {
        boolean halt = actions.getDouble(HALT_OFFSET) > 0;
        int direction = normalizeDegAngle(status.value().getWheelly().getRobotDeg() + actions.getInt(DIRECTION_OFFSET));
        double speed = min(max(actions.getDouble(SPEED_OFFSET), -1), 1);
        int sensor = min(max(actions.getInt(SENSOR_OFFSET), -90), 90);
        return halt
                ? Tuple2.of(HaltCommand.create(), sensor)
                : Tuple2.of(MoveCommand.create(direction, speed), sensor);
    }

    /**
     * Returns the encoded action.
     * 4D vector with
     * <ul>
     * <li> value 1 if action is halt command </li>
     * <li>  robot direction DEG </li>
     * <li>  speed of robot (-1 ... 1) </li>
     * <li>  sensor direction DEG </li>
     * </li>
     * </ul>
     *
     * @param command the command
     */
    public static INDArray encodeAction(Tuple2<MotionCommand, Integer> command) {
        MotionCommand ma = command._1;
        INDArray result = Nd4j.zeros(1, 4);
        if (ma instanceof HaltCommand) {
            result.putScalar(0, 1);
        } else {
            result.putScalar(1, ((MoveCommand) ma).direction);
            result.putScalar(2, ((MoveCommand) ma).speed);
        }
        result.putScalar(3, command._2);
        return result;
    }

    public static RLEngine fromJson(JsonNode root, Locator locator) throws IOException {
        RLEngineConf config = RLEngineConf.fromJson(root, locator);
        List<Number> x = locator.path("actors").elements(root)
                .map(l -> (float) l.path("alpha").getNode(root).asDouble())
                .collect(Collectors.toList());
        INDArray alphas = Nd4j.create(x);
        ActorCriticAgentConf agentConf = ActorCriticAgentConf.fromJson(root, locator);
        File agentFile = new File(locator.path("agentFile").getNode(root).asText());
        ComputationGraph agentModel = loadNetwork(agentFile,
                config.getNumInputs(),
                agentConf.getNumOutputs());
        INDArray avg = scalar((float) locator.path("averageReward").getNode(root).asDouble(0));
        ActorCriticAgent agent = ActorCriticAgent.create(agentConf, agentModel, alphas, avg);
        Random random = Nd4j.getRandomFactory().getNewRandomInstance();
        return new RLEngine(config, random, agent);
    }

    static ComputationGraph loadNetwork(File file, int noInputs, int[] noOutputs) throws IOException {
        logger.info("Loading {} ...", file);
        ComputationGraph net = ModelSerializer.restoreComputationGraph(file, true);
        // Validate
        long n = net.layerInputSize(0);
        if (n != noInputs) {
            throw new IllegalArgumentException(format("Network %s with wrong (%s) input number: expected %d",
                    file, n, noInputs));
        }
        long m = net.getNumOutputArrays();
        if (m != noOutputs.length) {
            throw new IllegalArgumentException(format("Network %s with wrong (%d) output layers: expected %d",
                    file, m, noOutputs.length));
        }
        return net;
    }

    private final RLEngineConf config;
    private final Random random;
    private final ActorCriticAgent agent;
    private Timed<MapStatus> prevStatus;
    private INDArray prevSignals;
    private INDArray prevAction;

    /**
     * Creates the deep learning engine
     *
     * @param config the agent configuration
     * @param random the random generator
     * @param agent  the reinforcement learning agent
     */
    protected RLEngine(RLEngineConf config, Random random, ActorCriticAgent agent) {
        this.random = requireNonNull(random);
        this.config = requireNonNull(config);
        this.agent = requireNonNull(agent);
    }

    Feedback createFeedback(Timed<MapStatus> s1) {
        double reward = config.reward(prevStatus, s1);
        double dt = (s1.time(TimeUnit.MILLISECONDS) - prevStatus.time(TimeUnit.MILLISECONDS)) * 1e-3;
        INDArray inputs = config.encode(s1);
        return Feedback.create(prevSignals, prevAction, reward, inputs, dt);
    }

    public Feedback createFeedback(Timed<MapStatus> s0, Tuple2<MotionCommand, Integer> action, Timed<MapStatus> s1) {
        double reward = config.reward(s0, s1);
        INDArray in0 = config.encode(s0);
        INDArray in1 = config.encode(s1);
        INDArray a0 = encodeAction(action);
        double dt = (s1.time(TimeUnit.MILLISECONDS) - s0.time(TimeUnit.MILLISECONDS)) * 1e-3;
        return Feedback.create(in0, a0, reward, in1, dt);
    }

    public ActorCriticAgent getAgent() {
        return agent;
    }

    public RLEngineConf getConfig() {
        return config;
    }

    public Random getRandom() {
        return random;
    }

    @Override
    public RLEngine init(InferenceMonitor monitor) {
        return this;
    }

    @Override
    public Tuple2<MotionCommand, Integer> process(Timed<MapStatus> status, InferenceMonitor monitor) {
        INDArray inputs;
        if (prevStatus == null) {
            inputs = config.encode(status);
        } else {
            Feedback feedback = createFeedback(status);
            Map<String, Object> map = agent.directLearn(feedback, random);
            INDArray kpi = createKpi(map, feedback.getReward());
            monitor.put(PERFORMANCE_KEY, kpi);
            inputs = feedback.getS1();
        }
        INDArray action = agent.chooseAction(inputs, random);
        prevStatus = status;
        prevAction = action;
        prevSignals = inputs;
        return decodeAction(status, action);
    }
}
