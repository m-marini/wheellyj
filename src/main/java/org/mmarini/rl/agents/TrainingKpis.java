/*
 * Copyright 2026 Marco Marini, marco.marini@mmarini.org
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
 * END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.rl.agents;

import org.mmarini.MapStream;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.log;
import static org.mmarini.rl.agents.NNMediator.CRITIC_ID;

/**
 * Tracks the predictions, the RL error and the average rewards of a minibatch
 *
 * @param predictions the predictions
 * @param deltas      the RL errors
 * @param avgReward   the average rewards
 */
public record TrainingKpis(Map<String, INDArray> predictions, INDArray deltas, float avgReward) {
    /**
     * Returns policy KPIS from policy
     *
     * @param policy the policy
     */
    private static INDArray computePolicyKpis(INDArray policy) {
        try (INDArray max = policy.max(true, 1)) {
            // Computes the normalised entropy
            try (INDArray entropy = policy.entropy(1)
                    .reshape(policy.size(0), 1)
                    .divi(log(policy.size(1)))) {
                return Nd4j.hstack(max, entropy);
            }
        }
    }

    /**
     * Returns training KPIS
     *
     * @param ids         the prediction identifiers
     * @param predictions the predictions
     * @param deltas      the deltas
     * @param avgReward   the average reward
     */
    public static TrainingKpis create(List<String> ids, INDArray[] predictions, INDArray deltas, float avgReward) {
        Map<String, INDArray> predictionMap = new HashMap<>();
        for (int i = 0; i < predictions.length; i++) {
            String id = ids.get(i);
            if (CRITIC_ID.equals(id)) {
                predictionMap.put(id, predictions[i].dup());
            } else {
                predictionMap.put(id, computePolicyKpis(predictions[i]));
            }
        }
        return new TrainingKpis(predictionMap, deltas.dup(), avgReward);
    }

    public static TrainingKpis create(Map<String, INDArray> predictions, INDArray deltas, float avgReward) {
        Map<String, INDArray> map = MapStream.of(predictions)
                .mapValues((key, values) ->
                        CRITIC_ID.equals(key)
                                ? values.dup()
                                : computePolicyKpis(values))
                .toMap();
        return new TrainingKpis(map, deltas.dup(), avgReward);
    }

    /**
     * Writes the KPIS into KPIS writer
     *
     * @param writer the writer
     */
    public TrainingKpis write(KeyBinWriter writer) throws IOException {
        writer.write(predictions);
        writer.write(Map.of("deltas", deltas));
        writer.write(Map.of("avgReward", Nd4j.createFromArray(avgReward)));
        return this;
    }
}
