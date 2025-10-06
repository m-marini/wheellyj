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
import org.jetbrains.annotations.NotNull;
import org.mmarini.rl.agents.Kpis;
import org.mmarini.rl.agents.TrainingKpis;
import org.mmarini.swing.Messages;
import org.mmarini.wheelly.apps.DoubleReducedValue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mmarini.wheelly.apps.DoubleReducedValue.mean;
import static org.mmarini.wheelly.apps.DoubleReducedValue.rms;

/**
 * Shows the kpis average values
 */
public class KpisPanel extends MatrixTable {
    public static final double MICROS = 1e6;
    private final DoubleReducedValue criticMean;
    private final DoubleReducedValue advantageMean;
    private final DoubleReducedValue deltaRms;
    private final List<Consumer<Map<String, INDArray>>> handlers;
    private static final Logger logger = LoggerFactory.getLogger(KpisPanel.class);

    /**
     * Creates the kpis panel
     */
    public KpisPanel() {
        this.handlers = new ArrayList<>();
        this.advantageMean = mean();
        this.criticMean = mean();
        this.deltaRms = rms();
        // Creates the default columns
        addColumn("avgReward", Messages.getString("KpisPanel.avgReward.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("deltaAdv", Messages.getString("KpisPanel.deltaAdv.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("critic", Messages.getString("KpisPanel.critic.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        // Creates the default handlers
        handlers.add(this::handleCritic);
//        handlers.add(this::handleAvgReward);
        setPrintTimestamp(false);
    }

    /**
     * Adds the action columns
     *
     * @param actions the key action
     */
    public void addActionColumns(String... actions) {
        for (String key : actions) {
            // Add action probability colum
            addColumn(key + ".prob",
                    Messages.getStringOpt("KpisPanel." + key + ".prob.label").orElse(key), 6)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        for (String key : actions) {
            addColumn(key + ".probRatio",
                    Messages.getStringOpt("KpisPanel." + key + ".probRatio.label").orElse(key), 7)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        for (String key : actions) {
            // Creates handler
            Consumer<Map<String, INDArray>> handler = createActionHandler(key);
            handlers.add(handler);
        }
    }

    /**
     * Returns the frame with the monitor
     */
    public JFrame createFrame() {
        return createFrame(Messages.getString("KpisMonitor.title"));
    }

    private @NotNull Consumer<Map<String, INDArray>> createActionHandler(String key) {
        DoubleReducedValue prob = mean();
        DoubleReducedValue probRatio = mean();

        return kpis -> {
            INDArray data = kpis.get(key);
            if (data != null) {
                // shows the max of action probabilities
                try (INDArray max = data.max(true, 1)) {
                    printf(key + ".prob", "%,6.1f", prob.add(max).value() * 100);
                }
                // and the ratio of max of action probabilities over the probabilities geometric mean value
                try (INDArray ratio = Kpis.maxGeometricMeanRatio(data)) {
                    printf(key + ".probRatio", "%,7.2f", probRatio.add(ratio).value());
                }
            }

        };
    }

    /**
     * Shows critic error (delta eta grad)
     *
     * @param kpis the kpis
     */
    private void handleCritic(Map<String, INDArray> kpis) {
        INDArray critic = kpis.get("critic");
        if (critic != null) {
            printf("critic", "%,10.3f", criticMean.add(critic).value());
        }
    }

    /**
     * Add training kpis
     *
     * @param kpis the kpis
     */
    public void print(TrainingKpis kpis) {
        printf("avgReward", "%,10.0f", advantageMean.add(kpis.avgReward()).value() * MICROS);
        printf("deltaAdv", "%,10.3f", deltaRms.add(kpis.deltas()).value());
        Map<String, INDArray> predictions = kpis.predictions();
        for (Consumer<Map<String, INDArray>> handler : handlers) {
            try {
                handler.accept(predictions);
            } catch (Throwable ex) {
                logger.atError().setCause(ex).log("Error printing kpis");
            }
        }
    }
}
