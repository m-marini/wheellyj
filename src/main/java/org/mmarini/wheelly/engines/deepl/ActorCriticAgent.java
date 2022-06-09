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

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.mmarini.Tuple2;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.mmarini.wheelly.engines.deepl.FunctionBuilder.decay;
import static org.nd4j.linalg.factory.Nd4j.hstack;
import static org.nd4j.linalg.factory.Nd4j.scalar;
import static org.nd4j.linalg.ops.transforms.Transforms.pow;

/**
 * The actor-critic agent generates the action of robot basing on reinforcement learning
 * The interactions with the inference engine is based on INDArray values.
 * Any map with the robot environment (robot status, action status) is managed by the engine.
 */
public class ActorCriticAgent {
    private static final Logger logger = LoggerFactory.getLogger(ActorCriticAgent.class);

    public static ActorCriticAgent create(ActorCriticAgentConf config, ComputationGraph agentModel, INDArray alpha, INDArray avg) {
        return new ActorCriticAgent(config, agentModel, alpha, avg);
    }

    private final ActorCriticAgentConf config;
    private final ComputationGraph agentModel;
    private INDArray alpha;
    private INDArray avg;
    private long saveTime;

    protected ActorCriticAgent(ActorCriticAgentConf config, ComputationGraph agentModel, INDArray alpha, INDArray avg) {
        this.config = config;
        this.agentModel = requireNonNull(agentModel);
        this.alpha = alpha;
        this.avg = avg;
        saveTime = System.currentTimeMillis() + config.getSaveInterval();
    }

    /**
     * Returns the action for a signals set
     *
     * @param signals the normalized signals to network
     * @param random  the random generator
     */
    public INDArray chooseAction(INDArray signals, Random random) {
        INDArray[] netOutputs = agentModel.output(signals);
        INDArray[] actions = config.getActors().stream()
                .map(a -> a.chooseAction(netOutputs, random))
                .toArray(INDArray[]::new);
        return hstack(actions);
    }

    /**
     * Returns the dictionary of training data
     *
     * @param feedback the feedback
     */
    public Map<String, Object> computeLabels(Feedback feedback) {
        Map<String, Object> result = new HashMap<>();
        INDArray s0 = feedback.getS0();
        INDArray actions = feedback.getAction();
        double reward = feedback.getReward();
        INDArray s1 = feedback.getS1();

        INDArray[] outputs0 = agentModel.output(s0);
        INDArray[] outputs1 = agentModel.output(s1);

        INDArray v0 = getV(outputs0);
        INDArray v1 = getV(outputs1);

        INDArray target = v1.add(reward).subi(avg);
        double dt = feedback.getInterval();
        INDArray newV0 = decay(avg, target, dt / config.getValueDecay());


        //val delta = target.sub(v0)
        INDArray delta = newV0.sub(v0);
        //val newAvg = rewardDecay * avg + (1 - rewardDecay) * reward
        INDArray newAvg = decay(avg, scalar(reward), dt / config.getRewardDecay());

        // Critic Label
        INDArray criticLabel = config.normalizeActionValue(newV0);
        INDArray jc = pow(criticLabel.sub(outputs0[0].getScalar(0)), 2);

        // Compute actor labels
        List<Actor> actors = config.getActors();
        for (Actor actor : actors) {
            Map<String, Object> map = actor.computeLabels(outputs0, actions, delta, alpha, dt);
            result.putAll(map);
        }

        Stream<INDArray> actorLabels = getActorValues(result, "labels");
        INDArray alphaStars = getActorValuesAsArray(result, "alpha*");

        INDArray ja1 = getActorValuesAsArray(result, "J");
        INDArray ja = ja1.sum();
        INDArray[] h = getActorValues(result, "h").toArray(INDArray[]::new);
        INDArray[] hStar = getActorValues(result, "h*").toArray(INDArray[]::new);

        // Merge critic and actors labels
        INDArray[] labels = Stream.concat(
                        Stream.of(criticLabel.reshape(1,1)),
                        actorLabels)
                .toArray(INDArray[]::new);

        result.put("outputs0", outputs0);
        result.put("outputs1", outputs1);
        result.put("avg", avg);
        result.put("v0", v0);
        result.put("v1", v1);
        result.put("newAverage", newAvg);
        result.put("v0*", newV0);
        result.put("delta", delta);
        result.put("score", pow(delta, 2));
        result.put("alpha*", alphaStars);
        result.put("labels", labels);
        result.put("Jc", jc);
        result.put("J", ja.add(jc));
        result.put("h", h);
        result.put("h*", hStar);
        return result;
    }

    /**
     * Returns the fit ACAgent, the average reward and the score
     * Optimizes the policy based on the feedback for a single feedback
     *
     * @param feedback the feedback from the last step
     * @param random   the random generator
     */
    public Map<String, Object> directLearn(Feedback feedback, Random random) {
        Map<String, Object> map = computeLabels(feedback);
        INDArray[] labels = (INDArray[]) map.get("labels");

        INDArray[] inputs = new INDArray[]{feedback.getS0()};
        agentModel.fit(inputs, labels);

        if (saveTime >= System.currentTimeMillis()) {
            saveTime = System.currentTimeMillis() + config.getSaveInterval();
            saveModel();
        }

        Map<String, Object> map1 = computeLabels(feedback);

        avg = (INDArray) map1.get("newAverage");
        alpha = (INDArray) map1.get("alpha*");
        map1.put("J0", map.get("J"));
        map1.put("J1", map1.get("J"));
        return map1;
    }

    /**
     * Returns the fit agent and the score
     * Optimizes the policy based on the feedback and model/planning
     *
     * @param feedback the feedback from the last step
     * @param random   the random generator
     */
    public Tuple2<ActorCriticAgent, INDArray> fit(Feedback feedback, Random random) {
        return null;
    }

    private <T> Stream<T> getActorValues(Map<String, Object> values, String key) {
        return config.getActors().stream()
                .map(a -> (T) values.get(format("%s(%d)", key, a.getDimension())));
    }

    private INDArray getActorValuesAsArray(Map<String, Object> map, String key) {
        INDArray[] arrs = this.<INDArray>getActorValues(map, key)
                .map(x -> x.reshape(1))
                .toArray(INDArray[]::new);
        return hstack(arrs);
    }

    public ComputationGraph getAgentModel() {
        return agentModel;
    }

    /**
     * Returns the estimation of state value (denormalized critic output)
     *
     * @param outputs the neural network outputs
     */
    private INDArray getV(INDArray[] outputs) {
        return config.denormalizeActionValue(outputs[0].getScalar(0));
    }

    private void saveModel() {
        config.getSaveFile().ifPresent(file -> {
            try {
                ModelSerializer.writeModel(agentModel, file, false);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    /**
     * Returns the score for a feedback
     *
     * @param feedback the feedback from the last step
     */
    public INDArray score(Feedback feedback) {
        return null;
    }
}
