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
import org.mmarini.wheelly.apps.MeanValue;
import org.mmarini.wheelly.apps.RMSValue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shows the kpis average values
 */
public class KpisPanel extends MatrixTable {
    public static final double PPM = 1e6;
    public static final double PPG = 1e9;
    private static final Logger logger = LoggerFactory.getLogger(KpisPanel.class);
    private final RMSValue criticRms;
    private final MeanValue advantageMean;
    private final RMSValue deltaRms;
    private final List<Consumer<Map<String, INDArray>>> handlers;


    public KpisPanel() {
        this.handlers = new ArrayList<>();
        this.criticRms = RMSValue.zeros();
        this.advantageMean = MeanValue.zeros();
        this.deltaRms = RMSValue.zeros();
        // Creates the default columns
        addColumn("avgReward", Messages.getString("KpisPanel.avgReward.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("deltaAdv", Messages.getString("KpisPanel.deltaAdv.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("critic.delta", Messages.getString("KpisPanel.critic.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        // Creates the default handlers
        handlers.add(this::handleDelta);
        handlers.add(this::handleCritic);
        handlers.add(this::handleAvgReward);
        setPrintTimestamp(false);
    }

    /**
     * Add an action kpi
     *
     * @param outputKey the action key
     * @param inputKey  the action input key
     */
    public void addActionKpi(String outputKey, String inputKey) {
        addColumn(outputKey + ".delta",
                Messages.getStringOpt("KpisPanel." + outputKey + ".delta.label").orElse(outputKey), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn(outputKey + ".deltaAction",
                Messages.getStringOpt("KpisPanel." + outputKey + ".deltaAction.label").orElse(outputKey), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn(outputKey + ".prob",
                Messages.getStringOpt("KpisPanel." + outputKey + ".prob.label").orElse(outputKey), 6)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn(inputKey + ".saturation",
                Messages.getStringOpt("KpisPanel." + inputKey + ".saturation.label").orElse(outputKey), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn(outputKey + ".probRatio",
                Messages.getStringOpt("KpisPanel." + outputKey + ".probRatio.label").orElse(outputKey), 7)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        handlers.add(createSaturationHandler(inputKey));
        handlers.add(createDeltaActionHandler(outputKey));
        handlers.add(createActionKpiHandler(outputKey));
    }

    /**
     * Adds kpis record
     *
     * @param kpis the kpis
     */
    public void addKpis(Map<String, INDArray> kpis) {
        for (Consumer<Map<String, INDArray>> mapConsumer : handlers) {
            try {
                mapConsumer.accept(kpis);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns the handlers of kpi for an action
     *
     * @param key the action key
     */
    private Consumer<Map<String, INDArray>> createActionKpiHandler(String key) {
        RMSValue delta = RMSValue.zeros();
        MeanValue prob = MeanValue.zeros();
        MeanValue probRatio = MeanValue.zeros();
        return kpis -> {
            INDArray deltaGrads = kpis.get("deltaGrads." + key);
            if (deltaGrads != null) {
                try (INDArray max = Transforms.abs(deltaGrads).max(true, 1)) {
                    printf(key + ".delta", "%,10.0f", delta.add(max).value() * PPM);
                }
            }
            INDArray data = kpis.get("trainingLayers." + key + ".values");
            if (data != null) {
                // shows the max of actin probabilities
                // and the ratio of max of action probabilities over the probabilities geometric mean value
                try (INDArray max = data.max(true, 1)) {
                    try (INDArray log = Transforms.log(data)) {
                        try (INDArray gm = Transforms.exp(log.mean(true, 1), false)) {
                            try (INDArray ratio = max.div(gm)) {
                                printf(key + ".prob", "%,6.1f", prob.add(max).value() * 100);
                                printf(key + ".probRatio", "%,7.2f", probRatio.add(ratio).value());
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * Shows the action policy correction
     *
     * @param actionKey the key action
     */
    private Consumer<Map<String, INDArray>> createDeltaActionHandler(String actionKey) {
        RMSValue deltaRms = RMSValue.zeros();
        return kpis -> {
            INDArray pi0 = kpis.get("trainingLayers." + actionKey + ".values");
            INDArray pi1 = kpis.get("trainedLayers." + actionKey + ".values");
            INDArray actionMasks = kpis.get("actionMasks." + actionKey);
            if (pi0 != null && pi1 != null && actionMasks != null) {
                try (INDArray pi0a = pi0.mul(actionMasks)) {
                    try (INDArray prob0 = pi0a.sum(true, 1)) {
                        try (INDArray pi1a = pi1.mul(actionMasks)) {
                            try (INDArray prob = pi1a.max(true, 1)) {
                                try (INDArray deltaRatio = prob.div(prob0).subi(1)) {
                                    printf(actionKey + ".deltaAction", "%,10.3f", deltaRms.add(deltaRatio).value() * 100);
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
     * Returns the saturation of input layer
     *
     * @param inputKey the input layer key
     */
    private Consumer<Map<String, INDArray>> createSaturationHandler(String inputKey) {
        MeanValue saturation = MeanValue.zeros();
        return kpis -> {
            INDArray data = kpis.get("trainingLayers." + inputKey + ".values");
            if (data != null) {
                try (INDArray abs = Transforms.abs(data, true)) {
                    try (INDArray sat = abs.max(true, 1)) {
                        printf(inputKey + ".saturation", "%,7.0f", saturation.add(sat).value() * 100);
                    }
                }
            }
        };
    }

    /**
     * Shows average reward
     *
     * @param kpis the kpis
     */
    private void handleAvgReward(Map<String, INDArray> kpis) {
        INDArray avgReward = kpis.get("avgReward");
        if (avgReward != null) {
            printf("avgReward", "%,10.0f", advantageMean.add(avgReward).value() * PPM);
        }
    }

    /**
     * Shows critic error (delta eta grad)
     *
     * @param kpis the kpis
     */
    private void handleCritic(Map<String, INDArray> kpis) {
        INDArray delta = kpis.get("deltaGrads.critic");
        if (delta != null) {
            printf("critic.delta", "%,10.0f", criticRms.add(delta).value() * PPG);
        }
    }

    /**
     * Shows TD error
     *
     * @param kpis the kpis
     */
    private void handleDelta(Map<String, INDArray> kpis) {
        INDArray delta = kpis.get("delta");
        if (delta != null) {
            printf("deltaAdv", "%,10.3f", deltaRms.add(delta).value());
        }
    }
}
