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
import org.mmarini.wheelly.apps.AverageValue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Shows the kpis average values
 */
public class KpisPanel extends MatrixTable {
    public static final double DISCOUNT = 0.977;
    public static final double PPM = 1e6;
    private static final Logger logger = LoggerFactory.getLogger(KpisPanel.class);
    private final AverageValue deltaAvg;
    private Map<String, Consumer<INDArray>> handlers;

    public KpisPanel() {
        this.handlers = Map.of();
        this.deltaAvg = new AverageValue(0, DISCOUNT);
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
        AverageValue grads = new AverageValue(0, DISCOUNT);
        AverageValue prob = new AverageValue(0, DISCOUNT);
        AverageValue probRatio = new AverageValue(1, DISCOUNT);
        return Stream.of(
                Tuple2.of(
                        "netGrads." + key + ".grads", data -> {
                            try (INDArray max = data.max(1)) {
                                for (long i = 0; i < data.size(0); i++) {
                                    grads.add(max.getDouble(i, 0));
                                }
                                printf(key + ".grads", "%,10.0f", grads.getValue() * PPM);
                            }
                        }),
                Tuple2.of(
                        "policy." + key, data -> {
                            try (INDArray max = data.max(1)) {
                                try (INDArray min = data.min(1)) {
                                    try (INDArray ratio = max.div(min)) {
                                        for (long i = 0; i < data.size(0); i++) {
                                            prob.add(max.getDouble(i, 0));
                                            probRatio.add(ratio.getDouble(i, 0));
                                        }
                                        printf(key + ".prob", "%,10.1f", prob.getValue() * 100);
                                        printf(key + ".probRatio", "%,10.2f", probRatio.getValue());
                                    }
                                }
                            }
                        })
        );
    }

    private void handleDelta(INDArray delta) {
        for (long i = 0; i < delta.size(0); i++) {
            deltaAvg.add(delta.getDouble(i, 0));
        }
        printf("delta", "%,10.3f", deltaAvg.getValue());
    }

    /**
     * Sets the keys shown
     *
     * @param keys the keys
     */
    public void setKeys(String... keys) {
        // Creates the handlers
        handlers = Stream.concat(
                        Stream.of(Tuple2.<String, Consumer<INDArray>>of("delta", this::handleDelta)),
                        Arrays.stream(keys)
                                .flatMap(this::createHandler))
                .collect(Tuple2.toMap());

        addColumn("delta", Messages.getString("KpisPanel.delta.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        Arrays.stream(keys)
                .sorted()
                .flatMap(key -> Stream.of(
                        key + ".grads",
                        key + ".prob",
                        key + ".probRatio"
                ))
                .forEach(key ->
                        addColumn(key,
                                Messages.getStringOpt("KpisPanel." + key + ".label").orElse(key), 10)
                                .setScrollOnChange(true)
                                .setPrintTimestamp(false)
                );
    }
}
