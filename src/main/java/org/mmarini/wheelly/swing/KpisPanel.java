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
import org.mmarini.wheelly.apps.MeanValue;
import org.mmarini.wheelly.apps.RMSValue;
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
    public static final double DISCOUNT = 0.977;
    public static final double PPM = 1e6;
    private static final Logger logger = LoggerFactory.getLogger(KpisPanel.class);
    private final RMSValue criticRms;
    private Map<String, Consumer<INDArray>> handlers;

    public KpisPanel() {
        this.handlers = Map.of();
        this.criticRms = new RMSValue(0, DISCOUNT);
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
        RMSValue delta = new RMSValue(0, DISCOUNT);
        MeanValue prob = new MeanValue(0, DISCOUNT);
        MeanValue probRatio = new MeanValue(1, DISCOUNT);
        return Stream.of(
                Tuple2.of(
                        "deltas." + key, data -> {
                            try (INDArray max = Transforms.abs(data).max(true, 1)) {
                                printf(key + ".delta", "%,10.0f", delta.add(max).value() * PPM);
                            }
                        }),
                Tuple2.of(
                        "policy." + key, data -> {
                            try (INDArray max = data.max(1)) {
                                try (INDArray min = data.min(1)) {
                                    try (INDArray ratio = max.div(min)) {
                                        printf(key + ".prob", "%,10.1f", prob.add(max).value() * 100);
                                        printf(key + ".probRatio", "%,10.2f", probRatio.add(ratio).value());
                                    }
                                }
                            }
                        })
        );
    }

    private void handleCritic(INDArray delta) {
        printf("critic.delta", "%,10.0f", criticRms.add(delta).value() * PPM);
    }

    /**
     * Sets the keys shown
     *
     * @param keys the keys
     */
    public void setKeys(String... keys) {
        // Creates the handlers
        handlers = Stream.concat(
                        Stream.of(Tuple2.<String, Consumer<INDArray>>of("deltas.critic", this::handleCritic)),
                        Arrays.stream(keys)
                                .flatMap(this::createHandler))
                .collect(Tuple2.toMap());

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
                        Messages.getStringOpt("KpisPanel." + key + ".prob.label").orElse(key), 10)
                        .setScrollOnChange(true)
                        .setPrintTimestamp(false)
        );
        colKeys.forEach(key ->
                        addColumn(key + ".probRatio",
                                Messages.getStringOpt("KpisPanel." + key + ".probRatio.label").orElse(key), 10)
                                .setScrollOnChange(true)
                                .setPrintTimestamp(false)
                );
    }
}
