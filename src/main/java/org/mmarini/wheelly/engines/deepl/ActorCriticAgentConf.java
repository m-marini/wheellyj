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
import org.mmarini.yaml.schema.Locator;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static java.util.stream.IntStream.concat;

public class ActorCriticAgentConf {
    public static final long DEFAULT_SAVE_INTERVAL = 10000;

    private static final Logger logger = LoggerFactory.getLogger(ActorCriticAgentConf.class);

    public static ActorCriticAgentConf fromJson(JsonNode root, Locator locator) {
        Yaml.agentConf().apply(locator).accept(root);
        double rewardDecay = locator.path("rewardDecay").getNode(root).asDouble();
        double valueDecay = locator.path("rewardDecay").getNode(root).asDouble();
        List<Actor> actors = Actor.fromArray(root, locator.path("actors"));
        String saveFile = locator.path("saveFile").getNode(root).asText(null);
        INDArray rewardRange = Yaml.range(locator.path("rewardRange").getNode(root));
        UnaryOperator<INDArray> denormalizerReward = FunctionBuilder.clipAndDenormalize(
                rewardRange.getDouble(0),
                rewardRange.getDouble(1));
        UnaryOperator<INDArray> normalizerReward = FunctionBuilder.clipAndNormalize(
                rewardRange.getDouble(0),
                rewardRange.getDouble(1));
        long saveInterval1 = locator.path("saveInterval").getNode(root).asLong(DEFAULT_SAVE_INTERVAL);
        return new ActorCriticAgentConf(rewardDecay, valueDecay,
                denormalizerReward, normalizerReward,
                actors,
                saveFile != null ? new File(saveFile) : null, saveInterval1);
    }

    private final double rewardDecay;
    private final double valueDecay;
    private final List<Actor> actors;
    private final File saveFile;
    private final long saveInterval;
    private final UnaryOperator<INDArray> denormalizerReward;
    private final UnaryOperator<INDArray> normalizerReward;

    protected ActorCriticAgentConf(double rewardDecay, double valueDecay,
                                   UnaryOperator<INDArray> denormalizerReward, UnaryOperator<INDArray> normalizerReward,
                                   List<Actor> actors, File saveFile, long saveInterval) {
        this.rewardDecay = rewardDecay;
        this.valueDecay = valueDecay;
        this.denormalizerReward = denormalizerReward;
        this.normalizerReward = normalizerReward;
        this.actors = actors;
        this.saveFile = saveFile;
        this.saveInterval = saveInterval;
    }

    /**
     * Returns the denormalize action value (advanced average rewards)
     *
     * @param value the normalize action value in (-1,1) range
     */
    public INDArray denormalizeActionValue(INDArray value) {
        return denormalizerReward.apply(value);
    }

    public List<Actor> getActors() {
        return actors;
    }

    public int[] getNumOutputs() {
        return concat(
                IntStream.of(1), // Critic output
                actors.stream().mapToInt(Actor::getNumOutputs) // actors output
        ).toArray();
    }

    public double getRewardDecay() {
        return rewardDecay;
    }

    public Optional<File> getSaveFile() {
        return Optional.ofNullable(saveFile);
    }

    public long getSaveInterval() {
        return saveInterval;
    }

    public double getValueDecay() {
        return valueDecay;
    }

    /**
     * Returns the normalized action value
     *
     * @param value the denormalize action value (advanced average rewards)
     */
    public INDArray normalizeActionValue(INDArray value) {
        return normalizerReward.apply(value);
    }
}
