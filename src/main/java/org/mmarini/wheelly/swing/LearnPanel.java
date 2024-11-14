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

import hu.akarnokd.rxjava3.swing.SwingObservable;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.mmarini.MapStream;
import org.mmarini.swing.GridLayoutHelper;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.util.List;
import java.util.*;

import static java.lang.Math.abs;
import static org.mmarini.Utils.zipWithIndex;

/**
 * Controls the learning parameters via UI
 */
public class LearnPanel extends JPanel {
    public static final Dimension DEFAULT_PREFERRED_SIZE = new Dimension(500, 300);
    private static final double[] ALPHA_VALUES = new double[]{
            3e-3,
            10e-3,
            30e-3,
            100e-3,
            300e-3,
            1,
            3,
            10,
            30,
            100,
            300
    };
    private static final double[] ETA_VALUES = new double[]{
            1e-6,
            3e-6,
            10e-6,
            30e-6,
            100e-6,
            300e-6,
            1e-3,
            3e-3,
            10e-3,
            30e-3,
            100e-3,
    };
    private static final Dictionary<Integer, JComponent> ALPHA_LABELS = createLabels(new String[]{
            "0.003",
            "0.010",
            "0.030",
            "0.100",
            "0.300",
            "1",
            "3",
            "10",
            "30",
            "100",
            "300"});
    private static final Dictionary<Integer, JComponent> ETA_LABELS = createLabels(new String[]{
            "1 ppm",
            "3 ppm",
            "10ppm",
            "30 ppm",
            "100 ppm",
            "300 ppm",
            "0.001",
            "0.003",
            "0.01",
            "0.03",
            "0.1"});

    private static Dictionary<Integer, JComponent> createLabels(String[] labelKeys) {
        Dictionary<Integer, JComponent> labels = new Hashtable<>();
        zipWithIndex(List.of(labelKeys)).forEach(t ->
                labels.put(t._1, new JLabel(t._2)));
        return labels;
    }

    /**
     * Returns the slider
     */
    private static JSlider createSlider(double[] values, Dictionary<Integer, JComponent> labels) {
        JSlider slider = new JSlider(JSlider.VERTICAL);
        slider.setMaximum(values.length - 1);
        slider.setPaintLabels(true);
        slider.setPaintTicks(true);
        slider.setMajorTickSpacing(1);
        slider.setSnapToTicks(true);
        slider.setLabelTable(labels);
        return slider;
    }

    /**
     * Returns the index of value
     *
     * @param value the value
     */
    private static int indexOf(double value, double[] values) {
        int idx = 0;
        double diff = abs(value - values[idx]);
        for (int i = 1; i < values.length; i++) {
            double snap = values[i];
            double dValue = abs(snap - value);
            if (dValue < diff) {
                idx = i;
                diff = dValue;
            }
        }
        return idx;
    }

    private final PublishProcessor<Map<String, Float>> actionAlphasProcessor;
    private final JSlider etaSlider;
    private Map<String, Float> learningRates;
    private Map<String, JSlider> learningRateSliders;

    /**
     * Creates the panel
     */
    public LearnPanel() {
        this.etaSlider = createSlider(ETA_VALUES, ETA_LABELS);
        this.actionAlphasProcessor = PublishProcessor.create();
        etaSlider.setBorder(BorderFactory.createTitledBorder(
                Messages.getStringOpt("LearnPanel.eta.label").orElse("eta")
        ));
        setPreferredSize(DEFAULT_PREFERRED_SIZE);
    }

    /**
     * Returns the frame with the panel
     */
    public JFrame createFrame() {
        JFrame frame = new JFrame(Messages.getString("LearnPanel.title"));
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(this, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    /**
     * Handles the slider value change
     *
     * @param event the event
     */
    private void handleAlphaSliders(ChangeEvent event) {
        if (Objects.requireNonNull(event.getSource()) instanceof JSlider slider) {
            if (!slider.getValueIsAdjusting()) {
                learningRates = MapStream.of(learningRates)
                        .mapValues((key, v) -> {
                            JSlider slider1 = learningRateSliders.get(key);
                            return slider1 != null ? (float) ALPHA_VALUES[slider1.getValue()] : v;
                        })
                        .toMap();
                actionAlphasProcessor.onNext(learningRates);
            }
        }
    }

    /**
     * Returns the learning alpha flows
     */
    public Flowable<Map<String, Float>> readActionAlphas() {
        return actionAlphasProcessor;
    }

    /**
     * Returns the learning rate flows
     */
    public Flowable<Float> readEtas() {
        return SwingObservable.change(etaSlider).toFlowable(BackpressureStrategy.DROP)
                .filter(event -> !etaSlider.getValueIsAdjusting())
                .map(event -> (float) ETA_VALUES[etaSlider.getValue()]);
    }

    /**
     * Sets the action alpha hyperparameters
     *
     * @param rates the rates
     */
    public void setActionAlphas(Map<String, Float> rates) {
        this.learningRates = rates;
        this.learningRateSliders = MapStream.of(rates)
                .mapValues((key, value) -> {
                    JSlider slider = createSlider(ALPHA_VALUES, ALPHA_LABELS);
                    slider.setValue(indexOf(value, ALPHA_VALUES));
                    slider.setBorder(BorderFactory.createTitledBorder(
                            Messages.getStringOpt("LearnPanel." + key + ".label").orElse(key)
                    ));
                    slider.addChangeListener(this::handleAlphaSliders);
                    return slider;
                })
                .toMap();
        GridLayoutHelper<LearnPanel> layoutBuilder = new GridLayoutHelper<>(this)
                .modify("insets,15,5 fill weight,1,1 center")
                .at(0, 0).add(etaSlider);
        List<String> actions = this.learningRateSliders.keySet().stream()
                .sorted()
                .toList();
        for (int i = 0; i < actions.size(); i++) {
            layoutBuilder.at(i + 1, 0).add(learningRateSliders.get(actions.get(i)));
        }
        doLayout();
    }

    /**
     * Sets the learning rate hyperparameter
     *
     * @param eta the learning rate hyperparameter
     */
    public void setEta(float eta) {
        etaSlider.setValue(indexOf(eta, ETA_VALUES));
    }
}
