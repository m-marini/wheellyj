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

import org.mmarini.Tuple2;
import org.mmarini.rl.nets.TDNetwork;
import org.mmarini.swing.GridLayoutHelper;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Math.ceil;
import static java.lang.Math.sqrt;

/**
 * Displays the network activity
 */
public class NNActivityPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(NNActivityPanel.class);
    private final Map<String, Integer> SPECIAL_KEYS = Map.of(
            "canMoveFeatures", 1,
            "distanceFeatures", 24,
            "sectorStateFeatures", 24,
            "direction", 1,
            "sensorAction", 1,
            "power", 1
    );
    private Map<String, ActivityMap> layerMaps;

    /**
     * Creates the neural network activity panel
     */
    public NNActivityPanel() {
        layerMaps = Map.of();
    }

    /**
     * Configures the panel
     * Creates the monitor for the network layers
     *
     * @param network the network
     */
    public void configure(TDNetwork network) {
        removeAll();
        List<Tuple2<String, ActivityMap>> sourceLayers = network.sourceLayers()
                .stream()
                .sorted()
                .map(key ->
                        Tuple2.of(key, createActivityMap(key, network.size(key))))
                .toList();
        List<Tuple2<String, ActivityMap>> hiddenLayers = network.forwardSequence()
                .stream()
                .filter(Predicate.not(network.sinkLayers()::contains))
                .map(key ->
                        Tuple2.of(key, createActivityMap(key, network.size(key))))
                .toList();
        List<Tuple2<String, ActivityMap>> sinkLayers = network.sinkLayers()
                .stream()
                .sorted()
                .map(key ->
                        Tuple2.of(key, createActivityMap(key, network.size(key))))
                .toList();
        this.layerMaps = Stream.of(sourceLayers, sinkLayers, hiddenLayers)
                .flatMap(List::stream)
                .collect(Tuple2.toMap());

        // Add source layers
        GridLayoutHelper<JPanel> sourcePanelBuilder = new GridLayoutHelper<>(new JPanel())
                .modify("insets,5,5 center weight,1,1 fill");
        for (int i = 0; i < sourceLayers.size(); i++) {
            sourcePanelBuilder.at(i, 0).add(sourceLayers.get(i)._2);
        }
        // Creates hidden panel
        GridLayoutHelper<JPanel> hiddensPanelBuilder = new GridLayoutHelper<>(new JPanel())
                .modify("insets,5,5 center weight,1,1 fill");
        int n = hiddenLayers.size();
        // n = w * h
        // h = 3/4 w
        // n = 3/4 w^2
        // w = sqrt(4/3 n)
        int w = (int) ceil(sqrt(4D * n / 3));
        int h = n / w;

        for (int i = 0; i < n; i++) {
            hiddensPanelBuilder.at(i % w, h - i / w).add(hiddenLayers.get(i)._2);
        }
        // Add sink layers
        GridLayoutHelper<JPanel> sinksPanelBuilder = new GridLayoutHelper<>(new JPanel())
                .modify("insets,5,5 center weight,1,1 fill");
        for (int i = 0; i < sinkLayers.size(); i++) {
            sinksPanelBuilder.at(i, 0).add(sinkLayers.get(i)._2);
        }
        new GridLayoutHelper<>(this).modify("insets,5 center fill")
                .modify("at,0,0 weight,1,0").add(sinksPanelBuilder.getContainer())
                .modify("at,0,1 weight,1,1").add(hiddensPanelBuilder.getContainer())
                .modify("at,0,2 weight,1,0").add(sourcePanelBuilder.getContainer());
        doLayout();
    }

    /**
     * Returns the activity map for the given key
     *
     * @param key  the key
     * @param size the size of layer
     */
    private ActivityMap createActivityMap(String key, long size) {
        ActivityMap v2 = new ActivityMap();
        v2.setBorder(BorderFactory.createTitledBorder(key));
        Integer specialHeight = SPECIAL_KEYS.get(key);
        if (specialHeight != null) {
            int height = specialHeight;
            int width = (int) ceil((double) size / height);
            v2.setLayerSize(width, height);
        } else {
            int width = (int) (size > 4 ? ceil(sqrt(size)) : size);
            int height = (int) ((size + width - 1) / width);
            v2.setLayerSize(width, height);
        }
        return v2;
    }

    /**
     * Set the activity
     *
     * @param layers the layer values
     */
    public void setActivity(Map<String, INDArray> layers) {
        for (Map.Entry<String, ActivityMap> layerEntry : layerMaps.entrySet()) {
            INDArray values = layers.get(layerEntry.getKey() + ".values");
            if (values != null) {
                layerEntry.getValue().setActivity(values);
            }
        }
    }
}
