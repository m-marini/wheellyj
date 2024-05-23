/*
 * Copyright (c) 2024 Marco Marini, marco.marini@mmarini.org
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

package org.mmarini.wheelly.swing;

import io.reactivex.rxjava3.functions.Consumer;
import org.mmarini.Tuple2;
import org.mmarini.wheelly.apps.MeanValues;
import org.mmarini.wheelly.apps.RMSValues;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Shows the kpis average values
 */
public class KpisPanel extends MatrixTable {
    public static final double PPM = 1e6;
    private static final Logger logger = LoggerFactory.getLogger(KpisPanel.class);
    private final RMSValues criticRms;
    private final MeanValues advantageMean;
    private final RMSValues deltaRms;
    private Map<String, Consumer<INDArray>> handlers;
    private Consumer<Map<String, INDArray>> deltaActionHandler;

    public KpisPanel() {
        this.handlers = Map.of();
        this.criticRms = RMSValues.zeros();
        this.advantageMean = MeanValues.zeros();
        this.deltaRms = RMSValues.zeros();
        setPrintTimestamp(false);
    }

    /**
     * Adds kpis record
     *
     * @param kpis the kpis
     */
    public void addKpis(Map<String, INDArray> kpis) {
        Tuple2.stream(handlers)
                .forEach(t -> {
                    INDArray kpi = kpis.get(t._1);
                    if (kpi != null) {
                        try {
                            t._2.accept(kpi);
                        } catch (Throwable e) {
                            logger.atError().setCause(e).log("Error adding kpis");
                            throw new RuntimeException(e);
                        }
                    }
                });
        if (deltaActionHandler != null) {
            try {
                deltaActionHandler.accept(kpis);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        handleDelta(kpis);
    }

    /**
     * Returns the handler of delta action policy kpis
     *
     * @param action the key action
     */
    private Consumer<Map<String, INDArray>> createDeltaActionHandler(String action) {
        RMSValues deltaRms = RMSValues.zeros();
        return kpis -> {
            INDArray pi0 = kpis.get("trainingLayers." + action + ".values");
            INDArray pi1 = kpis.get("trainedLayers." + action + ".values");
            INDArray actions = kpis.get("actionMasks." + action);
            if (pi0 != null && pi1 != null && actions != null) {
                try (INDArray pi0a = pi0.mul(actions)) {
                    try (INDArray pi0max = pi0a.max(true, 1)) {
                        try (INDArray pi1a = pi1.mul(actions)) {
                            try (INDArray pi1max = pi1a.max(true, 1)) {
                                try (INDArray ratio = pi1max.sub(pi0max).divi(pi0max)) {
                                    printf(action + ".deltaAction", "%,10.3f", deltaRms.add(ratio).value() * 100);
                                }
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * Returns the frame with the monitor
     */
    public JFrame createFrame() {
        return createFrame(Messages.getString("KpisMonitor.title"));
    }

    /**
     * Returns the handlers of kpi
     *
     * @param key the key
     */
    private Stream<Tuple2<String, Consumer<INDArray>>> createHandler(String key) {
        RMSValues delta = RMSValues.zeros();
        MeanValues prob = MeanValues.zeros();
        MeanValues probRatio = MeanValues.zeros();
        return Stream.of(
                Tuple2.of(
                        "deltaGrads." + key, data -> {
                            try (INDArray max = Transforms.abs(data).max(true, 1)) {
                                printf(key + ".delta", "%,10.0f", delta.add(max).value() * PPM);
                            }
                        }),
                Tuple2.of(
                        "trainingLayers." + key + ".values", data -> {
                            // prob = max(data) / exp(mean(log(data)))
                            try (INDArray max = data.max(true, 1)) {
                                try (INDArray log = Transforms.log(data)) {
                                    try (INDArray mean = Transforms.exp(log.mean(true, 1), false)) {
                                        try (INDArray ratio = max.div(mean)) {
                                            printf(key + ".prob", "%,6.1f", prob.add(max).value() * 100);
                                            printf(key + ".probRatio", "%,7.2f", probRatio.add(ratio).value());
                                        }
                                    }
                                }
                            }
                        })
        );
    }

    /**
     * Handles average reward
     *
     * @param avgReward the average reward
     */
    private void handleAvgReward(INDArray avgReward) {
        printf("avgReward", "%,10.0f", advantageMean.add(avgReward).value() * PPM);
    }

    /**
     * Handles critic delta
     *
     * @param delta the critic delta
     */
    private void handleCritic(INDArray delta) {
        printf("critic.delta", "%,10.0f", criticRms.add(delta).value() * PPM);
    }

    /**
     * Returns the handler of delta critic kpis
     *
     * @param kpis the kpis
     */
    private void handleDelta(Map<String, INDArray> kpis) {
        INDArray delta = kpis.get("delta");
        if (delta != null) {
            printf("deltaAdv", "%,10.3f", deltaRms.add(delta).value());
        }
    }

    /**
     * Sets the keys shown
     *
     * @param keys the keys
     */
    public void setKeys(String... keys) {
        // Creates the handlers
        handlers = Stream.concat(Stream.of(
                                Tuple2.<String, Consumer<INDArray>>of("deltaGrads.critic", this::handleCritic),
                                Tuple2.<String, Consumer<INDArray>>of("avgReward", this::handleAvgReward)),
                        Arrays.stream(keys)
                                .flatMap(this::createHandler))
                .collect(Tuple2.toMap());

        addColumn("avgReward", Messages.getString("KpisPanel.avgReward.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("deltaAdv", Messages.getString("KpisPanel.deltaAdv.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("critic.delta", Messages.getString("KpisPanel.critic.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        List<String> colKeys = Arrays.stream(keys)
                .filter(Predicate.not("critic"::equals))
                .sorted()
                .toList();

        colKeys.forEach(key ->
                addColumn(key + ".delta",
                        Messages.getStringOpt("KpisPanel." + key + ".delta.label").orElse(key), 10)
                        .setScrollOnChange(true)
                        .setPrintTimestamp(false)
        );
        colKeys.forEach(key ->
                addColumn(key + ".prob",
                        Messages.getStringOpt("KpisPanel." + key + ".prob.label").orElse(key), 6)
                        .setScrollOnChange(true)
                        .setPrintTimestamp(false)
        );
        colKeys.forEach(key ->
                addColumn(key + ".probRatio",
                        Messages.getStringOpt("KpisPanel." + key + ".probRatio.label").orElse(key), 7)
                        .setScrollOnChange(true)
                        .setPrintTimestamp(false)
        );
        colKeys.forEach(key ->
                addColumn(key + ".deltaAction",
                        Messages.getStringOpt("KpisPanel." + key + ".deltaAction.label").orElse(key), 10)
                        .setScrollOnChange(true)
                        .setPrintTimestamp(false)
        );
        this.deltaActionHandler = colKeys.stream()
                .map(this::createDeltaActionHandler)
                .reduce((a, b) -> kpis -> {
                    a.accept(kpis);
                    b.accept(kpis);
                })
                .orElse(null);
    }
}
