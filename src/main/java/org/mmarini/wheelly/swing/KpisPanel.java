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
import org.mmarini.rl.agents.AbstractAgentNN;
import org.mmarini.rl.agents.Agent;
import org.mmarini.rl.agents.Kpis;
import org.mmarini.rl.nets.TDLayer;
import org.mmarini.wheelly.apps.ReducedValue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mmarini.wheelly.apps.ReducedValue.mean;
import static org.mmarini.wheelly.apps.ReducedValue.rms;

/**
 * Shows the kpis average values
 */
public class KpisPanel extends MatrixTable {
    public static final double MILLIS = 1e3;
    public static final double PPM = 1e6;
    public static final double NANOS = 1e9;
    public static final double PICOS = 1e12;
    private static final Logger logger = LoggerFactory.getLogger(KpisPanel.class);
    private final ReducedValue criticDeltaRms;
    private final ReducedValue criticMean;
    private final ReducedValue advantageMean;
    private final ReducedValue deltaRms;
    private final List<Consumer<Map<String, INDArray>>> handlers;

    /**
     * Creates the kpis panel
     */
    public KpisPanel() {
        this.handlers = new ArrayList<>();
        this.criticDeltaRms = rms();
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
        addColumn("critic.delta", Messages.getString("KpisPanel.criticDelta.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        addColumn("critic", Messages.getString("KpisPanel.critic.label"), 10)
                .setScrollOnChange(true)
                .setPrintTimestamp(false);
        // Creates the default handlers
        handlers.add(this::handleDelta);
        handlers.add(this::handleCritic);
        handlers.add(this::handleAvgReward);
        setPrintTimestamp(false);
    }

    /**
     * Add action kpis
     *
     * @param keys the action keys tuple (input, output)
     */
    private void addActionKpi(Stream<Tuple2<String, String>> keys) {
        // Add delta action kpi
        List<Tuple2<String, String>> keysList = keys.toList();
        for (Tuple2<String, String> t : keysList) {
            String key = t._2;
            addColumn(key + ".delta",
                    Messages.getStringOpt("KpisPanel." + key + ".delta.label").orElse(key), 10)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        // Add action probability kpi
        for (Tuple2<String, String> t : keysList) {
            String key = t._2;
            addColumn(key + ".prob",
                    Messages.getStringOpt("KpisPanel." + key + ".prob.label").orElse(key), 6)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        // Add delta action probability kpi
        for (Tuple2<String, String> t : keysList) {
            String key = t._2;
            addColumn(key + ".deltaAction",
                    Messages.getStringOpt("KpisPanel." + key + ".deltaAction.label").orElse(key), 10)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        // Add action saturation kpi
        for (Tuple2<String, String> t : keysList) {
            String key = t._1;
            addColumn(key + ".saturation",
                    Messages.getStringOpt("KpisPanel." + key + ".saturation.label").orElse(key), 10)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        // Add action predictability kpi
        for (Tuple2<String, String> t : keysList) {
            String key = t._2;
            addColumn(key + ".probRatio",
                    Messages.getStringOpt("KpisPanel." + key + ".probRatio.label").orElse(key), 7)
                    .setScrollOnChange(true)
                    .setPrintTimestamp(false);
        }
        for (Tuple2<String, String> t : keysList) {
            handlers.add(createSaturationHandler(t._1));
            handlers.add(createDeltaActionHandler(t._2));
            handlers.add(createActionKpiHandler(t._2));
        }
    }

    /**
     * Adds action kpis for the given agent
     *
     * @param agent the agent
     */
    public void addActionKpis(Agent agent) {
        if (agent instanceof AbstractAgentNN aa) {
            Map<String, TDLayer> layers = aa.network().layers();
            addActionKpi(agent.getActions().keySet().stream()
                    .filter(layers::containsKey)
                    .sorted()
                    .map(key ->
                            Tuple2.of(
                                    layers.get(key).inputs()[0],
                                    key)
                    ));
        }
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
        ReducedValue delta = rms();
        ReducedValue prob = mean();
        ReducedValue probRatio = mean();
        return kpis -> {
            INDArray deltaGrads = kpis.get("deltaGrads." + key);
            if (deltaGrads != null) {
                try (INDArray max = Kpis.absMax(deltaGrads)) {
                    printf(key + ".delta", "%,10.0f", delta.add(max).value() * PICOS);
                }
            }
            INDArray data = kpis.get("trainingLayers." + key + ".values");
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
     * Shows the action policy correction
     *
     * @param actionKey the key action
     */
    private Consumer<Map<String, INDArray>> createDeltaActionHandler(String actionKey) {
        ReducedValue deltaRms = rms();
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
        ReducedValue saturation = mean();
        return kpis -> {
            INDArray data = kpis.get("trainingLayers." + inputKey + ".values");
            if (data != null) {
                try (INDArray sat = Kpis.rms(data)) {
                    printf(inputKey + ".saturation", "%,7.0f", saturation.add(sat).value() * 100);
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
            printf("critic.delta", "%,10.0f", criticDeltaRms.add(delta).value() * PICOS);
        }
        INDArray critic = kpis.get("trainingLayers.critic.values");
        if (critic != null) {
            printf("critic", "%,10.3f", criticMean.add(critic).value());
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
